package wioe5.runtime;

/**
 * Fixed-capacity bump allocator with deterministic mark-sweep collection metrics.
 */
public final class DeterministicHeapManagerModule implements HeapManagerModule {
    public static final int OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_HEAP_FULL = -2;
    public static final int ERROR_INVALID_HANDLE = -3;
    public static final int ERROR_TOO_MANY_REFERENCE_SLOTS = -4;

    private final int heapCapacityBytes;
    private final int maxObjects;
    private final int maxReferenceSlotsPerObject;

    private final int[] objectSizes;
    private final int[] objectOffsets;
    private final boolean[] allocated;
    private final boolean[] marked;
    private final int[][] objectReferences;
    private final int[] traversalStack;

    private int bumpPointerBytes;
    private int lastCollectionMarkedCount;
    private int lastCollectionReclaimedBytes;
    private int lastCollectionSweepCount;
    private int lastCollectionPauseTicks;
    private int maxObservedCollectionPauseTicks;

    public DeterministicHeapManagerModule(int heapCapacityBytes, int maxObjects, int maxReferenceSlotsPerObject) {
        if (heapCapacityBytes <= 0) {
            throw new IllegalArgumentException("heapCapacityBytes must be > 0");
        }
        if (maxObjects <= 0) {
            throw new IllegalArgumentException("maxObjects must be > 0");
        }
        if (maxReferenceSlotsPerObject <= 0) {
            throw new IllegalArgumentException("maxReferenceSlotsPerObject must be > 0");
        }
        this.heapCapacityBytes = heapCapacityBytes;
        this.maxObjects = maxObjects;
        this.maxReferenceSlotsPerObject = maxReferenceSlotsPerObject;
        this.objectSizes = new int[maxObjects];
        this.objectOffsets = new int[maxObjects];
        this.allocated = new boolean[maxObjects];
        this.marked = new boolean[maxObjects];
        this.objectReferences = new int[maxObjects][maxReferenceSlotsPerObject];
        this.traversalStack = new int[maxObjects];
    }

    @Override
    public int allocate(int sizeBytes) {
        if (sizeBytes <= 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (sizeBytes > heapCapacityBytes) {
            return ERROR_HEAP_FULL;
        }
        if (bumpPointerBytes + sizeBytes > heapCapacityBytes) {
            return ERROR_HEAP_FULL;
        }

        int slot = findFreeSlot();
        if (slot < 0) {
            return ERROR_HEAP_FULL;
        }

        allocated[slot] = true;
        marked[slot] = false;
        objectSizes[slot] = sizeBytes;
        objectOffsets[slot] = bumpPointerBytes;
        clearReferences(slot);
        bumpPointerBytes += sizeBytes;
        return toHandle(slot);
    }

    @Override
    public int collect(FrameStackModule frameStack) {
        if (frameStack == null) {
            return ERROR_INVALID_ARGUMENT;
        }

        clearMarks();
        int markedCount = markFromRoots(frameStack);
        int reclaimedBytes = sweepUnmarkedAndResetBumpPointer();
        int sweepCount = maxObjects;
        int pauseTicks = markedCount + sweepCount;

        lastCollectionMarkedCount = markedCount;
        lastCollectionReclaimedBytes = reclaimedBytes;
        lastCollectionSweepCount = sweepCount;
        lastCollectionPauseTicks = pauseTicks;
        if (pauseTicks > maxObservedCollectionPauseTicks) {
            maxObservedCollectionPauseTicks = pauseTicks;
        }
        return OK;
    }

    public int setObjectReference(int fromHandle, int referenceSlot, int toHandle) {
        if (referenceSlot < 0 || referenceSlot >= maxReferenceSlotsPerObject) {
            return ERROR_TOO_MANY_REFERENCE_SLOTS;
        }
        int fromIndex = toIndex(fromHandle);
        if (!isAllocatedIndex(fromIndex)) {
            return ERROR_INVALID_HANDLE;
        }

        if (toHandle == 0) {
            objectReferences[fromIndex][referenceSlot] = 0;
            return OK;
        }

        int toIndex = toIndex(toHandle);
        if (!isAllocatedIndex(toIndex)) {
            return ERROR_INVALID_HANDLE;
        }
        objectReferences[fromIndex][referenceSlot] = toHandle;
        return OK;
    }

    public int liveObjectCount() {
        int count = 0;
        for (int i = 0; i < maxObjects; i++) {
            if (allocated[i]) {
                count++;
            }
        }
        return count;
    }

    public int usedBytes() {
        int used = 0;
        for (int i = 0; i < maxObjects; i++) {
            if (allocated[i]) {
                used += objectSizes[i];
            }
        }
        return used;
    }

    public int bumpPointerBytes() {
        return bumpPointerBytes;
    }

    public int heapCapacityBytes() {
        return heapCapacityBytes;
    }

    public int maxObjects() {
        return maxObjects;
    }

    public int lastCollectionMarkedCount() {
        return lastCollectionMarkedCount;
    }

    public int lastCollectionReclaimedBytes() {
        return lastCollectionReclaimedBytes;
    }

    public int lastCollectionSweepCount() {
        return lastCollectionSweepCount;
    }

    public int lastCollectionPauseTicks() {
        return lastCollectionPauseTicks;
    }

    public int maxObservedCollectionPauseTicks() {
        return maxObservedCollectionPauseTicks;
    }

    private int markFromRoots(FrameStackModule frameStack) {
        int rootCount = frameStack.gcRootCount();
        int stackDepth = 0;
        int markCount = 0;

        for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
            int rootHandle = frameStack.gcRootAt(rootIndex);
            int rootSlot = toIndex(rootHandle);
            if (!isAllocatedIndex(rootSlot) || marked[rootSlot]) {
                continue;
            }
            traversalStack[stackDepth++] = rootHandle;
            marked[rootSlot] = true;
            markCount++;

            while (stackDepth > 0) {
                int currentHandle = traversalStack[--stackDepth];
                int currentIndex = toIndex(currentHandle);
                for (int ref = 0; ref < maxReferenceSlotsPerObject; ref++) {
                    int childHandle = objectReferences[currentIndex][ref];
                    if (childHandle == 0) {
                        continue;
                    }
                    int childIndex = toIndex(childHandle);
                    if (!isAllocatedIndex(childIndex) || marked[childIndex]) {
                        continue;
                    }
                    marked[childIndex] = true;
                    traversalStack[stackDepth++] = childHandle;
                    markCount++;
                }
            }
        }
        return markCount;
    }

    private int sweepUnmarkedAndResetBumpPointer() {
        int reclaimedBytes = 0;
        int highestLiveEnd = 0;

        for (int i = 0; i < maxObjects; i++) {
            if (!allocated[i]) {
                continue;
            }

            if (!marked[i]) {
                reclaimedBytes += objectSizes[i];
                allocated[i] = false;
                objectSizes[i] = 0;
                objectOffsets[i] = 0;
                clearReferences(i);
                continue;
            }

            int objectEnd = objectOffsets[i] + objectSizes[i];
            if (objectEnd > highestLiveEnd) {
                highestLiveEnd = objectEnd;
            }
        }
        bumpPointerBytes = highestLiveEnd;
        return reclaimedBytes;
    }

    private void clearMarks() {
        for (int i = 0; i < maxObjects; i++) {
            marked[i] = false;
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < maxObjects; i++) {
            if (!allocated[i]) {
                return i;
            }
        }
        return -1;
    }

    private void clearReferences(int objectIndex) {
        for (int i = 0; i < maxReferenceSlotsPerObject; i++) {
            objectReferences[objectIndex][i] = 0;
        }
    }

    private boolean isAllocatedIndex(int objectIndex) {
        return objectIndex >= 0 && objectIndex < maxObjects && allocated[objectIndex];
    }

    private int toHandle(int objectIndex) {
        return objectIndex + 1;
    }

    private int toIndex(int handle) {
        return handle - 1;
    }
}
