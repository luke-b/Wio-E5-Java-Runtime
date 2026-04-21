package wioe5.runtime;

/**
 * Owns heap allocation and garbage collection.
 */
public interface HeapManagerModule {
    /**
     * Attempts deterministic allocation in the Java heap.
     *
     * @return allocated object handle/reference id, or negative error code.
     */
    int allocate(int sizeBytes);

    /**
     * Runs mark-sweep collection using frame-stack roots.
     *
     * @return 0 on success, negative error code on failure.
     */
    int collect(FrameStackModule frameStack);
}
