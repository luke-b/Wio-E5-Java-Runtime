package wioe5.runtime;

public final class BytecodeInterpreterModuleTest {
    private BytecodeInterpreterModuleTest() {
    }

    public static void main(String[] args) {
        testArithmeticProgram();
        testBranchProgram();
        testNativeDispatchAndAllocationProgram();
        testUnsupportedOpcodeFailsExplicitly();
        testDivideByZeroFailsExplicitly();
    }

    private static void testArithmeticProgram() {
        byte[] program = new byte[]{
                0x05,       // iconst_2
                0x06,       // iconst_3
                0x60,       // iadd
                (byte) 0xac // ireturn
        };
        BytecodeInterpreterModule interpreter = new BytecodeInterpreterModule(program, 8, 16);

        runToHalt(interpreter, fixedFrameStack(), fixedHeap(), fixedNativeDispatch(), 16);

        assertEquals(5, interpreter.getReturnValue(), "arithmetic return");
        assertTrue(interpreter.isHalted(), "arithmetic halted");
    }

    private static void testBranchProgram() {
        byte[] program = new byte[]{
                0x08,             // iconst_5
                0x06,             // iconst_3
                (byte) 0xa3,      // if_icmpgt -> true branch
                0x00, 0x07,       // jump to iconst_1
                0x03,             // iconst_0
                (byte) 0xa7,      // goto end
                0x00, 0x04,       // jump to ireturn
                0x04,             // iconst_1
                (byte) 0xac       // ireturn
        };
        BytecodeInterpreterModule interpreter = new BytecodeInterpreterModule(program, 8, 16);

        runToHalt(interpreter, fixedFrameStack(), fixedHeap(), fixedNativeDispatch(), 20);

        assertEquals(1, interpreter.getReturnValue(), "branch return");
    }

    private static void testNativeDispatchAndAllocationProgram() {
        byte[] program = new byte[]{
                (byte) 0xbb, 0x04,       // new size=4
                0x10, 0x02,              // bipush 2
                (byte) 0xb8,             // invokestatic
                0x00, 0x01,              // class hash = 1
                0x00, 0x02,              // method hash = 2
                0x02,                    // argc = 2
                (byte) 0xac              // ireturn
        };
        BytecodeInterpreterModule interpreter = new BytecodeInterpreterModule(program, 8, 16);
        HeapManagerModule heap = new HeapManagerModule() {
            private int nextHandle = 1;

            @Override
            public int allocate(int sizeBytes) {
                return nextHandle++;
            }

            @Override
            public int collect(FrameStackModule frameStack) {
                return 0;
            }
        };

        runToHalt(interpreter, fixedFrameStack(), heap, fixedNativeDispatch(), 20);

        assertEquals(6, interpreter.getReturnValue(), "native and allocation return");
    }

    private static void testUnsupportedOpcodeFailsExplicitly() {
        byte[] program = new byte[]{
                0x12 // ldc (unsupported in this runtime subset)
        };
        BytecodeInterpreterModule interpreter = new BytecodeInterpreterModule(program, 8, 8);

        int status = interpreter.executeStep(fixedFrameStack(), fixedHeap(), fixedNativeDispatch());

        assertEquals(BytecodeInterpreterModule.ERROR_UNSUPPORTED_OPCODE, status, "unsupported opcode status");
        assertTrue(interpreter.getLastErrorMessage().indexOf("Unsupported opcode 0x12") >= 0, "unsupported opcode message");
    }

    private static void testDivideByZeroFailsExplicitly() {
        byte[] program = new byte[]{
                0x04,       // iconst_1
                0x03,       // iconst_0
                0x6c        // idiv
        };
        BytecodeInterpreterModule interpreter = new BytecodeInterpreterModule(program, 8, 8);

        int first = interpreter.executeStep(fixedFrameStack(), fixedHeap(), fixedNativeDispatch());
        int second = interpreter.executeStep(fixedFrameStack(), fixedHeap(), fixedNativeDispatch());
        int third = interpreter.executeStep(fixedFrameStack(), fixedHeap(), fixedNativeDispatch());

        assertEquals(BytecodeInterpreterModule.STEP_OK, first, "div zero step 1");
        assertEquals(BytecodeInterpreterModule.STEP_OK, second, "div zero step 2");
        assertEquals(BytecodeInterpreterModule.ERROR_DIVIDE_BY_ZERO, third, "div zero status");
        assertTrue(interpreter.getLastErrorMessage().indexOf("Division by zero") >= 0, "div zero message");
    }

    private static void runToHalt(
            BytecodeInterpreterModule interpreter,
            FrameStackModule frameStack,
            HeapManagerModule heap,
            NativeDispatchModule nativeDispatch,
            int maxSteps) {
        int status = BytecodeInterpreterModule.STEP_OK;
        for (int i = 0; i < maxSteps; i++) {
            status = interpreter.executeStep(frameStack, heap, nativeDispatch);
            if (status == BytecodeInterpreterModule.STEP_HALTED) {
                return;
            }
            if (status < 0) {
                throw new AssertionError("Unexpected interpreter error: " + interpreter.getLastErrorMessage());
            }
        }
        throw new AssertionError("Program did not halt within step budget, last status=" + status);
    }

    private static FrameStackModule fixedFrameStack() {
        return new FrameStackModule() {
            @Override
            public int pushFrame(int methodId) {
                return 0;
            }

            @Override
            public int popFrame() {
                return 0;
            }

            @Override
            public int currentDepth() {
                return 1;
            }

            @Override
            public int maxDepth() {
                return 8;
            }
        };
    }

    private static HeapManagerModule fixedHeap() {
        return new HeapManagerModule() {
            @Override
            public int allocate(int sizeBytes) {
                return sizeBytes;
            }

            @Override
            public int collect(FrameStackModule frameStack) {
                return 0;
            }
        };
    }

    private static NativeDispatchModule fixedNativeDispatch() {
        return (classHash, methodHash, args) -> {
            int sum = classHash + methodHash;
            for (int i = 0; i < args.length; i++) {
                sum += args[i];
            }
            return sum;
        };
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
