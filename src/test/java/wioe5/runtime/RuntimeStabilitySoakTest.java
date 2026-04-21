package wioe5.runtime;

public final class RuntimeStabilitySoakTest {
    private static final int SIMULATED_TOTAL_MILLIS = 24 * 60 * 60 * 1000;
    private static final int LOOP_PERIOD_MILLIS = 1000;
    private static final int ITERATIONS = SIMULATED_TOTAL_MILLIS / LOOP_PERIOD_MILLIS;

    private RuntimeStabilitySoakTest() {
    }

    public static void main(String[] args) {
        testTwentyFourHourEquivalentSoakLoop();
    }

    private static void testTwentyFourHourEquivalentSoakLoop() {
        DeterministicHeapManagerModule heap = new DeterministicHeapManagerModule(64, 4, 2);
        DeterministicFrameStackModule frames = new DeterministicFrameStackModule(2, 4, 4);
        int pauseBound = heap.maxObjects() * 2;

        for (int i = 0; i < ITERATIONS; i++) {
            assertEquals(DeterministicFrameStackModule.OK, frames.pushFrame(100 + (i % 16), 2, 2), "push frame");

            int root = heap.allocate(8);
            int child = heap.allocate(8);
            int garbage = heap.allocate(8);
            assertTrue(root > 0, "root allocation");
            assertTrue(child > 0, "child allocation");
            assertTrue(garbage > 0, "garbage allocation");

            assertEquals(DeterministicFrameStackModule.OK, frames.setLocalReference(0, root), "set local root");
            assertEquals(DeterministicFrameStackModule.OK, frames.pushOperandReference(child), "set operand root");
            assertEquals(DeterministicHeapManagerModule.OK, heap.setObjectReference(root, 0, child), "link root->child");

            assertEquals(DeterministicHeapManagerModule.OK, heap.collect(frames), "collect with active roots");
            assertEquals(2, heap.liveObjectCount(), "live object count with roots");
            assertTrue(heap.lastCollectionPauseTicks() <= pauseBound, "pause bound with roots");
            assertEquals(heap.maxObjects(), heap.lastCollectionSweepCount(), "sweep count with roots");

            assertEquals(DeterministicFrameStackModule.OK, frames.popFrame(), "pop frame");

            assertEquals(DeterministicHeapManagerModule.OK, heap.collect(frames), "collect after frame pop");
            assertEquals(0, heap.liveObjectCount(), "live object count after pop");
            assertEquals(0, heap.usedBytes(), "used bytes after pop");
            assertEquals(0, heap.bumpPointerBytes(), "bump pointer reset after pop");
            assertTrue(heap.lastCollectionPauseTicks() <= pauseBound, "pause bound after pop");
            assertEquals(0, frames.currentDepth(), "frame depth at loop end");
        }
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
