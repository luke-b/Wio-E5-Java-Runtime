package wioe5.runtime;

public final class DeterministicHeapManagerModuleTest {
    private DeterministicHeapManagerModuleTest() {
    }

    public static void main(String[] args) {
        testAllocationBoundaries();
        testMarkSweepFromFrameRoots();
        testCollectionPauseMetricsAreBounded();
        testInvalidReferenceLinksFailExplicitly();
    }

    private static void testAllocationBoundaries() {
        DeterministicHeapManagerModule heap = new DeterministicHeapManagerModule(16, 4, 2);

        int first = heap.allocate(6);
        int second = heap.allocate(6);
        int third = heap.allocate(5);

        assertTrue(first > 0, "first allocation handle");
        assertTrue(second > 0, "second allocation handle");
        assertEquals(DeterministicHeapManagerModule.ERROR_HEAP_FULL, third, "heap full allocation result");
        assertEquals(2, heap.liveObjectCount(), "live object count");
        assertEquals(12, heap.bumpPointerBytes(), "bump pointer after allocations");
    }

    private static void testMarkSweepFromFrameRoots() {
        DeterministicHeapManagerModule heap = new DeterministicHeapManagerModule(32, 6, 2);
        DeterministicFrameStackModule frames = new DeterministicFrameStackModule(2, 4, 4);
        assertEquals(DeterministicFrameStackModule.OK, frames.pushFrame(100, 2, 2), "push frame");

        int root = heap.allocate(8);
        int child = heap.allocate(8);
        int garbage = heap.allocate(8);

        assertEquals(DeterministicFrameStackModule.OK, frames.setLocalReference(0, root), "set root local reference");
        assertEquals(DeterministicHeapManagerModule.OK, heap.setObjectReference(root, 0, child), "link root->child");
        assertEquals(DeterministicHeapManagerModule.OK, heap.collect(frames), "collect result");

        assertEquals(2, heap.liveObjectCount(), "live objects after collection");
        assertEquals(8, heap.lastCollectionReclaimedBytes(), "reclaimed bytes");
        assertEquals(2, heap.lastCollectionMarkedCount(), "marked object count");
        assertTrue(heap.bumpPointerBytes() >= 16, "bump pointer remains at/after live tail");

        assertEquals(DeterministicHeapManagerModule.ERROR_INVALID_HANDLE, heap.setObjectReference(root, 1, garbage), "garbage handle invalid after sweep");
    }

    private static void testCollectionPauseMetricsAreBounded() {
        DeterministicHeapManagerModule heap = new DeterministicHeapManagerModule(64, 8, 2);
        DeterministicFrameStackModule frames = new DeterministicFrameStackModule(2, 4, 4);
        assertEquals(DeterministicFrameStackModule.OK, frames.pushFrame(7, 2, 2), "push frame");

        int a = heap.allocate(8);
        int b = heap.allocate(8);
        int c = heap.allocate(8);
        assertEquals(DeterministicFrameStackModule.OK, frames.setLocalReference(0, a), "set local root");
        assertEquals(DeterministicFrameStackModule.OK, frames.pushOperandReference(c), "push operand root");
        assertEquals(DeterministicHeapManagerModule.OK, heap.setObjectReference(a, 0, b), "link a->b");

        assertEquals(DeterministicHeapManagerModule.OK, heap.collect(frames), "collect");
        int expectedPauseTicks = heap.lastCollectionMarkedCount() + heap.maxObjects();
        assertEquals(expectedPauseTicks, heap.lastCollectionPauseTicks(), "deterministic pause ticks");
        assertTrue(heap.maxObservedCollectionPauseTicks() <= heap.maxObjects() * 2, "pause tick bound");
        assertEquals(heap.maxObjects(), heap.lastCollectionSweepCount(), "full sweep width");
    }

    private static void testInvalidReferenceLinksFailExplicitly() {
        DeterministicHeapManagerModule heap = new DeterministicHeapManagerModule(32, 3, 1);
        int handle = heap.allocate(8);

        assertEquals(DeterministicHeapManagerModule.ERROR_TOO_MANY_REFERENCE_SLOTS, heap.setObjectReference(handle, 1, 0), "invalid reference slot");
        assertEquals(DeterministicHeapManagerModule.ERROR_INVALID_HANDLE, heap.setObjectReference(999, 0, 0), "invalid from handle");
        assertEquals(DeterministicHeapManagerModule.ERROR_INVALID_HANDLE, heap.setObjectReference(handle, 0, 999), "invalid to handle");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label + " expected true");
        }
    }
}
