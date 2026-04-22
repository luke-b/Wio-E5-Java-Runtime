package wioe5.runtime;

/**
 * Deterministic host-side implementation of {@code wioe5.storage.NVConfig}
 * native behavior with wear-aware write tracking.
 *
 * <p>Models a 4 KB Flash sector partitioned into six fixed keys (IDs 0–5),
 * each storing up to {@value #MAX_VALUE_BYTES} bytes. Write operations model
 * erase-before-write semantics: the previous value for a key is zeroed before
 * the new value is written. A per-key write counter enables wear-budget
 * enforcement, which is injected at test time via
 * {@link #setWriteBudgetPerKeyForTest(int)}.
 *
 * <p>Dispatch handlers for native indexes 32 ({@code NVConfig.read}) and
 * 33 ({@code NVConfig.write}) are produced by
 * {@link #createDefaultDispatchHandlers()}.
 */
public final class DeterministicNvConfigNativeModule {

    public static final int STATUS_OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_KEY_INVALID = -60;
    public static final int ERROR_DATA_TOO_LARGE = -61;
    public static final int ERROR_WRITE_BUDGET_EXCEEDED = -62;
    public static final int ERROR_BUFFER_HANDLE_INVALID = -63;
    public static final int ERROR_DISPATCH_STORAGE_FULL = -64;

    /** Number of defined NVConfig keys (KEY_LORA_REGION … KEY_APP_VERSION). */
    public static final int KEY_COUNT = 6;

    /** Maximum value size per key, matching the architecture 64-byte limit. */
    public static final int MAX_VALUE_BYTES = 64;

    /**
     * Sentinel stored-length value indicating a key has never been written
     * (equivalent to an erased Flash page with no committed value).
     */
    private static final int UNWRITTEN = -1;

    private static final int UNLIMITED_WRITE_BUDGET = Integer.MAX_VALUE;

    private final byte[][] store;
    private final int[] storedLengths;
    private final int[] writeCounts;
    private int writeBudgetPerKey;

    private final byte[][] dispatchByteBuffers;

    /** Create a module with the default dispatch-buffer capacity of 16. */
    public DeterministicNvConfigNativeModule() {
        this(16);
    }

    /**
     * Create a module with a specific dispatch-buffer capacity.
     *
     * @param maxDispatchByteBuffers capacity of the dispatch byte-buffer registry
     */
    public DeterministicNvConfigNativeModule(int maxDispatchByteBuffers) {
        if (maxDispatchByteBuffers <= 0) {
            throw new IllegalArgumentException("maxDispatchByteBuffers must be > 0");
        }
        store = new byte[KEY_COUNT][MAX_VALUE_BYTES];
        storedLengths = new int[KEY_COUNT];
        writeCounts = new int[KEY_COUNT];
        dispatchByteBuffers = new byte[maxDispatchByteBuffers][];
        writeBudgetPerKey = UNLIMITED_WRITE_BUDGET;
        for (int i = 0; i < KEY_COUNT; i++) {
            storedLengths[i] = UNWRITTEN;
        }
    }

    /**
     * Read the stored value for {@code key} into {@code buffer}.
     *
     * @param key    key ID (0–{@value #KEY_COUNT}-1)
     * @param buffer destination buffer; may be smaller than the stored value
     * @return number of bytes copied (0 if the key has never been written),
     *         or a negative error code
     */
    public int read(int key, byte[] buffer) {
        if (!isValidKey(key)) {
            return ERROR_KEY_INVALID;
        }
        if (buffer == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        int stored = storedLengths[key];
        if (stored == UNWRITTEN) {
            return 0;
        }
        int toCopy = stored < buffer.length ? stored : buffer.length;
        for (int i = 0; i < toCopy; i++) {
            buffer[i] = store[key][i];
        }
        return toCopy;
    }

    /**
     * Write {@code len} bytes from {@code data} to {@code key}.
     * Models erase-before-write: existing bytes for the key are zeroed before
     * the new value is committed.
     *
     * @param key  key ID (0–{@value #KEY_COUNT}-1)
     * @param data source data
     * @param len  number of bytes to write (must be &gt; 0 and ≤ {@value #MAX_VALUE_BYTES})
     * @return {@link #STATUS_OK}, or a negative error code
     */
    public int write(int key, byte[] data, int len) {
        if (!isValidKey(key)) {
            return ERROR_KEY_INVALID;
        }
        if (data == null || len < 0 || len > data.length) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (len > MAX_VALUE_BYTES) {
            return ERROR_DATA_TOO_LARGE;
        }
        if (writeCounts[key] >= writeBudgetPerKey) {
            return ERROR_WRITE_BUDGET_EXCEEDED;
        }
        for (int i = 0; i < MAX_VALUE_BYTES; i++) {
            store[key][i] = 0;
        }
        for (int i = 0; i < len; i++) {
            store[key][i] = data[i];
        }
        storedLengths[key] = len;
        writeCounts[key]++;
        return STATUS_OK;
    }

    // -------------------------------------------------------------------------
    // Test-seam accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the number of times {@code key} has been written.
     * Used to verify wear-tracking behavior in tests.
     */
    public int writeCountForKey(int key) {
        if (!isValidKey(key)) {
            return ERROR_KEY_INVALID;
        }
        return writeCounts[key];
    }

    /**
     * Returns the stored byte count for {@code key}, or {@code -1} if the key
     * has never been written.
     */
    public int storedLengthForKey(int key) {
        if (!isValidKey(key)) {
            return ERROR_KEY_INVALID;
        }
        return storedLengths[key];
    }

    /**
     * Inject a per-key write budget for deterministic wear-limit tests.
     *
     * @param budget maximum number of write operations allowed per key
     * @return {@link #STATUS_OK}, or {@link #ERROR_INVALID_ARGUMENT} if budget &lt; 0
     */
    public int setWriteBudgetPerKeyForTest(int budget) {
        if (budget < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        writeBudgetPerKey = budget;
        return STATUS_OK;
    }

    // -------------------------------------------------------------------------
    // Dispatch buffer registry
    // -------------------------------------------------------------------------

    /**
     * Register a byte array for use in a dispatch call.
     *
     * @param data the array to register (its contents are copied)
     * @return a positive 1-based handle, or a negative error code
     */
    public int registerDispatchByteBuffer(byte[] data) {
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        for (int i = 0; i < dispatchByteBuffers.length; i++) {
            if (dispatchByteBuffers[i] == null) {
                byte[] copy = new byte[data.length];
                for (int j = 0; j < data.length; j++) {
                    copy[j] = data[j];
                }
                dispatchByteBuffers[i] = copy;
                return i + 1;
            }
        }
        return ERROR_DISPATCH_STORAGE_FULL;
    }

    /**
     * Copy the current contents of the dispatch buffer identified by
     * {@code handle}.
     *
     * @param handle 1-based handle returned by {@link #registerDispatchByteBuffer}
     * @return a defensive copy, or {@code null} if the handle is invalid
     */
    public byte[] copyDispatchByteBuffer(int handle) {
        byte[] buffer = resolveDispatchByteBuffer(handle);
        if (buffer == null) {
            return null;
        }
        byte[] copy = new byte[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            copy[i] = buffer[i];
        }
        return copy;
    }

    // -------------------------------------------------------------------------
    // Native dispatch handler factory
    // -------------------------------------------------------------------------

    /**
     * Build a full-size handler array wired for NVConfig native indexes 32 and
     * 33. All other slots return {@link VersionedNativeDispatchTable#ERROR_SYMBOL_NOT_FOUND}
     * as placeholders until the corresponding native modules supply their own
     * handlers.
     *
     * @return handler array suitable for
     *         {@link VersionedNativeDispatchTable#createDefault createDefault}
     */
    public VersionedNativeDispatchTable.NativeHandler[] createDefaultDispatchHandlers() {
        VersionedNativeDispatchTable.NativeHandler[] handlers =
                new VersionedNativeDispatchTable.NativeHandler[VersionedNativeDispatchTable.defaultBindingCount()];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = args -> VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND;
        }
        handlers[32] = this::dispatchRead;
        handlers[33] = this::dispatchWrite;
        return handlers;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int dispatchRead(int[] args) {
        if (!argsLength(args, 2)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] buffer = resolveDispatchByteBuffer(args[1]);
        if (buffer == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return read(args[0], buffer);
    }

    private int dispatchWrite(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] data = resolveDispatchByteBuffer(args[1]);
        if (data == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return write(args[0], data, args[2]);
    }

    private byte[] resolveDispatchByteBuffer(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchByteBuffers.length) {
            return null;
        }
        return dispatchByteBuffers[index];
    }

    private static boolean argsLength(int[] args, int expected) {
        return args != null && args.length == expected;
    }

    private static boolean isValidKey(int key) {
        return key >= 0 && key < KEY_COUNT;
    }
}
