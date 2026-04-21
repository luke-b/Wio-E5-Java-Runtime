package wioe5.runtime;

/**
 * Owns bytecode fetch/decode/execute for the supported CLDC subset.
 */
public interface InterpreterModule {
    /**
     * Executes exactly one deterministic interpreter step.
     *
     * @return 0 on success, negative error code on failure.
     */
    int executeStep(FrameStackModule frameStack, HeapManagerModule heapManager, NativeDispatchModule nativeDispatch);
}
