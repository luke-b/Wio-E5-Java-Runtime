package wioe5.runtime;

public final class RuntimeModuleRegistryTest {
    private RuntimeModuleRegistryTest() {
    }

    public static void main(String[] args) {
        testRegistryStoresModules();
        testNullGuardInterpreter();
        testNullGuardHeapManager();
        testNullGuardFrameStack();
        testNullGuardNativeDispatch();
    }

    private static void testRegistryStoresModules() {
        InterpreterModule interpreter = (frameStack, heapManager, nativeDispatch) -> 0;
        HeapManagerModule heapManager = new HeapManagerModule() {
            @Override
            public int allocate(int sizeBytes) {
                return sizeBytes;
            }

            @Override
            public int collect(FrameStackModule frameStack) {
                return frameStack.currentDepth();
            }
        };
        FrameStackModule frameStack = new FrameStackModule() {
            @Override
            public int pushFrame(int methodId) {
                return methodId;
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
        NativeDispatchModule nativeDispatch = (classHash, methodHash, callArgs) -> classHash + methodHash + callArgs.length;

        RuntimeModuleRegistry registry = new RuntimeModuleRegistry(interpreter, heapManager, frameStack, nativeDispatch);

        assertSame(interpreter, registry.interpreterModule(), "interpreter module");
        assertSame(heapManager, registry.heapManagerModule(), "heap manager module");
        assertSame(frameStack, registry.frameStackModule(), "frame stack module");
        assertSame(nativeDispatch, registry.nativeDispatchModule(), "native dispatch module");
    }

    private static void testNullGuardInterpreter() {
        try {
            new RuntimeModuleRegistry(null, fixedHeap(), fixedFrameStack(), fixedNativeDispatch());
            throw new AssertionError("expected exception for null interpreter");
        } catch (IllegalArgumentException ex) {
            assertEquals("interpreterModule must not be null", ex.getMessage(), "null interpreter message");
        }
    }

    private static void testNullGuardHeapManager() {
        try {
            new RuntimeModuleRegistry(fixedInterpreter(), null, fixedFrameStack(), fixedNativeDispatch());
            throw new AssertionError("expected exception for null heap manager");
        } catch (IllegalArgumentException ex) {
            assertEquals("heapManagerModule must not be null", ex.getMessage(), "null heap manager message");
        }
    }

    private static void testNullGuardFrameStack() {
        try {
            new RuntimeModuleRegistry(fixedInterpreter(), fixedHeap(), null, fixedNativeDispatch());
            throw new AssertionError("expected exception for null frame stack");
        } catch (IllegalArgumentException ex) {
            assertEquals("frameStackModule must not be null", ex.getMessage(), "null frame stack message");
        }
    }

    private static void testNullGuardNativeDispatch() {
        try {
            new RuntimeModuleRegistry(fixedInterpreter(), fixedHeap(), fixedFrameStack(), null);
            throw new AssertionError("expected exception for null native dispatch");
        } catch (IllegalArgumentException ex) {
            assertEquals("nativeDispatchModule must not be null", ex.getMessage(), "null native dispatch message");
        }
    }

    private static InterpreterModule fixedInterpreter() {
        return (frameStack, heapManager, nativeDispatch) -> 0;
    }

    private static HeapManagerModule fixedHeap() {
        return new HeapManagerModule() {
            @Override
            public int allocate(int sizeBytes) {
                return 0;
            }

            @Override
            public int collect(FrameStackModule frameStack) {
                return 0;
            }
        };
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
                return 0;
            }

            @Override
            public int maxDepth() {
                return 8;
            }
        };
    }

    private static NativeDispatchModule fixedNativeDispatch() {
        return (classHash, methodHash, callArgs) -> 0;
    }

    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch");
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
