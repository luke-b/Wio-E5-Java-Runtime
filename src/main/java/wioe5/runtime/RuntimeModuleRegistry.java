package wioe5.runtime;

/**
 * Immutable integration point for runtime core module boundaries.
 */
public final class RuntimeModuleRegistry {
    private final InterpreterModule interpreterModule;
    private final HeapManagerModule heapManagerModule;
    private final FrameStackModule frameStackModule;
    private final NativeDispatchModule nativeDispatchModule;

    public RuntimeModuleRegistry(
            InterpreterModule interpreterModule,
            HeapManagerModule heapManagerModule,
            FrameStackModule frameStackModule,
            NativeDispatchModule nativeDispatchModule) {
        this.interpreterModule = requireNonNull(interpreterModule, "interpreterModule");
        this.heapManagerModule = requireNonNull(heapManagerModule, "heapManagerModule");
        this.frameStackModule = requireNonNull(frameStackModule, "frameStackModule");
        this.nativeDispatchModule = requireNonNull(nativeDispatchModule, "nativeDispatchModule");
    }

    public InterpreterModule interpreterModule() {
        return interpreterModule;
    }

    public HeapManagerModule heapManagerModule() {
        return heapManagerModule;
    }

    public FrameStackModule frameStackModule() {
        return frameStackModule;
    }

    public NativeDispatchModule nativeDispatchModule() {
        return nativeDispatchModule;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
