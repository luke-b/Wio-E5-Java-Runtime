package wioe5.runtime;

/**
 * Owns native call routing from romized method identifiers to C bindings.
 */
public interface NativeDispatchModule {
    /**
     * Routes a native call by ROMized class/method hashes.
     *
     * @param classHash class identifier hash from romized metadata.
     * @param methodHash method identifier hash from romized metadata.
     * @param args primitive argument payload.
     * @return 0 on success, negative error code on failure.
     */
    int dispatch(int classHash, int methodHash, int[] args);
}
