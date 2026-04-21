package wioe5.runtime;

/**
 * Owns frame lifecycle and root-set visibility for GC.
 */
public interface FrameStackModule {
    int ERROR_FRAME_STACK_OVERFLOW = -1;
    int ERROR_FRAME_STACK_UNDERFLOW = -2;

    /**
     * Pushes a frame for the provided ROMized method identifier.
     *
     * @return 0 on success, negative error code on overflow/invalid state.
     */
    int pushFrame(int methodId);

    /**
     * Pops the current frame.
     *
     * @return 0 on success, negative error code when no frame is available.
     */
    int popFrame();

    /**
     * @return current active frame depth.
     */
    int currentDepth();

    /**
     * @return configured maximum frame depth.
     */
    int maxDepth();
}
