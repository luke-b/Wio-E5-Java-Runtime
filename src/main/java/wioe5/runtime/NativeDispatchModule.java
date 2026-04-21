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

    /**
     * Validates that a ROMized image native-table version is compatible with
     * this runtime. Implementations that do not version native symbols may
     * return success.
     *
     * @param romizedNativeTableVersion version declared by ROMized artifact.
     * @return 0 on success, negative error code on incompatibility.
     */
    default int verifyCompatibility(int romizedNativeTableVersion) {
        return 0;
    }
}
