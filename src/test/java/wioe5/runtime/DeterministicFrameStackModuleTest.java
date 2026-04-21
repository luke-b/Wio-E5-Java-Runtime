package wioe5.runtime;

public final class DeterministicFrameStackModuleTest {
    private DeterministicFrameStackModuleTest() {
    }

    public static void main(String[] args) {
        testFrameDepthBoundaries();
        testFrameUnderflow();
        testLocalSlotRules();
        testOperandSlotRules();
        testFrameIsolationAfterPop();
        testInvalidFrameSlotConfiguration();
    }

    private static void testFrameDepthBoundaries() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 4, 4);

        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(100, 2, 2), "push frame 1");
        assertEquals(1, stack.currentDepth(), "depth after frame 1");
        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(200, 1, 3), "push frame 2");
        assertEquals(2, stack.currentDepth(), "depth after frame 2");
        assertEquals(FrameStackModule.ERROR_FRAME_STACK_OVERFLOW, stack.pushFrame(300, 1, 1), "push overflow");
        assertEquals(2, stack.currentDepth(), "depth remains max");
    }

    private static void testFrameUnderflow() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 4, 4);

        assertEquals(FrameStackModule.ERROR_FRAME_STACK_UNDERFLOW, stack.popFrame(), "pop underflow");
    }

    private static void testLocalSlotRules() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 4, 4);
        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(101, 2, 2), "push frame");

        assertEquals(DeterministicFrameStackModule.OK, stack.setLocal(0, 7), "set local 0");
        assertEquals(DeterministicFrameStackModule.OK, stack.setLocal(1, 9), "set local 1");
        assertEquals(7, stack.getLocal(0), "get local 0");
        assertEquals(9, stack.getLocal(1), "get local 1");
        assertEquals(DeterministicFrameStackModule.ERROR_LOCAL_SLOT_OUT_OF_RANGE, stack.setLocal(2, 3), "set local out of range");
        assertEquals(DeterministicFrameStackModule.ERROR_LOCAL_SLOT_OUT_OF_RANGE, stack.getLocal(2), "get local out of range");
    }

    private static void testOperandSlotRules() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 4, 4);
        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(102, 2, 2), "push frame");

        assertEquals(DeterministicFrameStackModule.OK, stack.pushOperand(11), "push operand 1");
        assertEquals(DeterministicFrameStackModule.OK, stack.pushOperand(22), "push operand 2");
        assertEquals(2, stack.currentOperandDepth(), "operand depth at limit");
        assertEquals(DeterministicFrameStackModule.ERROR_OPERAND_STACK_OVERFLOW, stack.pushOperand(33), "operand overflow");
        assertEquals(22, stack.popOperand(), "pop operand 2");
        assertEquals(11, stack.popOperand(), "pop operand 1");
        assertEquals(DeterministicFrameStackModule.ERROR_OPERAND_STACK_UNDERFLOW, stack.popOperand(), "operand underflow");
    }

    private static void testFrameIsolationAfterPop() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 3, 3);
        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(500, 1, 1), "push frame 1");
        assertEquals(DeterministicFrameStackModule.OK, stack.setLocal(0, 44), "set local in frame 1");
        assertEquals(DeterministicFrameStackModule.OK, stack.popFrame(), "pop frame 1");
        assertEquals(DeterministicFrameStackModule.OK, stack.pushFrame(600, 1, 1), "push frame 2");
        assertEquals(0, stack.getLocal(0), "local reset on reused frame slot");
        assertEquals(600, stack.currentMethodId(), "method id updates");
    }

    private static void testInvalidFrameSlotConfiguration() {
        DeterministicFrameStackModule stack = new DeterministicFrameStackModule(2, 4, 4);

        assertEquals(DeterministicFrameStackModule.ERROR_INVALID_SLOT_CONFIGURATION, stack.pushFrame(1, 0, 1), "invalid local slots");
        assertEquals(DeterministicFrameStackModule.ERROR_INVALID_SLOT_CONFIGURATION, stack.pushFrame(1, 1, 0), "invalid operand slots");
        assertEquals(DeterministicFrameStackModule.ERROR_INVALID_SLOT_CONFIGURATION, stack.pushFrame(1, 5, 1), "local slots exceed capacity");
        assertEquals(DeterministicFrameStackModule.ERROR_INVALID_SLOT_CONFIGURATION, stack.pushFrame(1, 1, 5), "operand slots exceed capacity");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
