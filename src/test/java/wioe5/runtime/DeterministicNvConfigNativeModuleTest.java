package wioe5.runtime;

public final class DeterministicNvConfigNativeModuleTest {
    private DeterministicNvConfigNativeModuleTest() {
    }

    public static void main(String[] args) {
        testReadWriteBasicFlow();
        testEraseBeforeWrite();
        testWearTracking();
        testBoundaryConditions();
        testDispatchIntegration();
        testNegativePaths();
    }

    private static void testReadWriteBasicFlow() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();

        byte[] readBuf = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];

        // Unwritten key returns 0 bytes
        assertEquals(0, module.read(0, readBuf), "read unwritten key 0 returns 0");
        assertEquals(-1, module.storedLengthForKey(0), "unwritten stored length is -1");

        // Write and read back each key with distinct content
        for (int key = 0; key < DeterministicNvConfigNativeModule.KEY_COUNT; key++) {
            byte[] data = new byte[key + 1];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (key * 10 + i);
            }
            assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                    module.write(key, data, data.length),
                    "write key " + key);
            assertEquals(data.length, module.storedLengthForKey(key), "stored length key " + key);

            byte[] out = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
            assertEquals(data.length, module.read(key, out), "read key " + key + " bytes");
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i] & 0xFF, out[i] & 0xFF, "key " + key + " byte[" + i + "]");
            }
        }

        // Partial-buffer read: buffer smaller than stored data
        byte[] partial = new byte[3];
        // Write 10 bytes to key 0
        byte[] big = new byte[10];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i + 1);
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(0, big, big.length),
                "write 10 bytes to key 0");
        assertEquals(3, module.read(0, partial), "partial read returns 3");
        assertEquals(1, partial[0] & 0xFF, "partial[0]");
        assertEquals(2, partial[1] & 0xFF, "partial[1]");
        assertEquals(3, partial[2] & 0xFF, "partial[2]");
    }

    private static void testEraseBeforeWrite() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();

        // Write a full 64-byte value
        byte[] full = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
        for (int i = 0; i < full.length; i++) full[i] = (byte) 0xFF;
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(1, full, full.length),
                "write 64 bytes");

        // Overwrite with a shorter value
        byte[] shorter = new byte[]{0x11, 0x22, 0x33};
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(1, shorter, shorter.length),
                "overwrite with shorter value");

        // Read back: should only see the shorter value, no old trailing bytes
        byte[] readBuf = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
        assertEquals(3, module.read(1, readBuf), "read after erase-before-write");
        assertEquals(0x11, readBuf[0] & 0xFF, "byte[0] after overwrite");
        assertEquals(0x22, readBuf[1] & 0xFF, "byte[1] after overwrite");
        assertEquals(0x33, readBuf[2] & 0xFF, "byte[2] after overwrite");
        // Erase semantics: bytes beyond new length were zeroed
        assertEquals(0, readBuf[3] & 0xFF, "byte[3] must be zeroed after erase");
        assertEquals(0, readBuf[63] & 0xFF, "byte[63] must be zeroed after erase");
    }

    private static void testWearTracking() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();
        byte[] data = new byte[]{1, 2, 3};

        // Initial write count is zero
        assertEquals(0, module.writeCountForKey(2), "initial write count for key 2");

        // Each successful write increments the count
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(2, data, data.length), "write 1");
        assertEquals(1, module.writeCountForKey(2), "write count after 1st write");
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(2, data, data.length), "write 2");
        assertEquals(2, module.writeCountForKey(2), "write count after 2nd write");

        // Reads do not increment write count
        byte[] buf = new byte[8];
        assertEquals(3, module.read(2, buf), "read does not count as write");
        assertEquals(2, module.writeCountForKey(2), "write count unchanged after read");

        // Enforce a write budget: set budget to 3, exhaust it, verify rejection
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.setWriteBudgetPerKeyForTest(3), "set budget 3");
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(2, data, data.length), "write 3 (within budget)");
        assertEquals(3, module.writeCountForKey(2), "write count at budget limit");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_WRITE_BUDGET_EXCEEDED,
                module.write(2, data, data.length), "write 4 exceeds budget");
        assertEquals(3, module.writeCountForKey(2), "write count unchanged after budget exceeded");

        // Different key has its own independent write count
        assertEquals(0, module.writeCountForKey(3), "key 3 write count independent");
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(3, data, data.length), "write to key 3 allowed");
        assertEquals(1, module.writeCountForKey(3), "key 3 write count incremented");
    }

    private static void testBoundaryConditions() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();

        // Write exactly MAX_VALUE_BYTES
        byte[] maxData = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
        for (int i = 0; i < maxData.length; i++) maxData[i] = (byte) i;
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(0, maxData, maxData.length), "write max bytes");
        byte[] readBuf = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
        assertEquals(DeterministicNvConfigNativeModule.MAX_VALUE_BYTES,
                module.read(0, readBuf), "read back max bytes");
        assertEquals(63, readBuf[63] & 0xFF, "last byte correct");

        // Attempt to write MAX_VALUE_BYTES + 1 via len exceeds stored data
        byte[] oversize = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES + 1];
        assertEquals(DeterministicNvConfigNativeModule.ERROR_DATA_TOO_LARGE,
                module.write(0, oversize, oversize.length), "write oversized data rejected");

        // Read into exact-size buffer
        byte[] exact = new byte[DeterministicNvConfigNativeModule.MAX_VALUE_BYTES];
        assertEquals(DeterministicNvConfigNativeModule.MAX_VALUE_BYTES,
                module.read(0, exact), "read into exact-size buffer");

        // Read into buffer of size 1 (truncation)
        byte[] one = new byte[1];
        assertEquals(1, module.read(0, one), "truncated read returns 1");
        assertEquals(0, one[0] & 0xFF, "truncated read returns first byte");

        // Write zero bytes is allowed (len=0)
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(1, new byte[0], 0), "write zero-length value");
        assertEquals(0, module.storedLengthForKey(1), "stored length 0 after zero-len write");
        assertEquals(0, module.read(1, readBuf), "read after zero-len write returns 0");
    }

    private static void testDispatchIntegration() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(5, 5, 5, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(5), "compatibility");

        // Register write data buffer
        byte[] writeData = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        int writeHandle = module.registerDispatchByteBuffer(writeData);
        assertTrue(writeHandle > 0, "write data handle allocated");

        // Dispatch write(key=4, data=writeHandle, len=3)
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE,
                        new int[]{4, writeHandle, 3}),
                "dispatch write key 4");
        assertEquals(1, module.writeCountForKey(4), "write count incremented via dispatch");

        // Register a read output buffer
        int readHandle = module.registerDispatchByteBuffer(new byte[8]);
        assertTrue(readHandle > 0, "read buffer handle allocated");

        // Dispatch read(key=4, buffer=readHandle)
        assertEquals(3,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{4, readHandle}),
                "dispatch read key 4 returns 3 bytes");

        byte[] copied = module.copyDispatchByteBuffer(readHandle);
        assertEquals(0xAA, copied[0] & 0xFF, "dispatch read byte[0]");
        assertEquals(0xBB, copied[1] & 0xFF, "dispatch read byte[1]");
        assertEquals(0xCC, copied[2] & 0xFF, "dispatch read byte[2]");

        // Dispatch read unwritten key returns 0
        int emptyHandle = module.registerDispatchByteBuffer(new byte[8]);
        assertEquals(0,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{5, emptyHandle}),
                "dispatch read unwritten key returns 0");

        // Non-NVConfig symbol returns ERROR_SYMBOL_NOT_FOUND
        assertEquals(VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                        new int[0]),
                "non-NVConfig symbol not found");
    }

    private static void testNegativePaths() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule(2);
        byte[] data = new byte[]{1, 2, 3};
        byte[] buf = new byte[8];

        // Null buffer in read
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.read(0, null), "read null buffer");

        // Null data in write
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.write(0, null, 0), "write null data");

        // len > data.length
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.write(0, data, data.length + 1), "write len > data.length");

        // Negative len
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.write(0, data, -1), "write negative len");

        // Invalid key (below range)
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.read(-1, buf), "read invalid key -1");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.write(-1, data, data.length), "write invalid key -1");

        // Invalid key (above range)
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.read(DeterministicNvConfigNativeModule.KEY_COUNT, buf),
                "read invalid key above range");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.write(DeterministicNvConfigNativeModule.KEY_COUNT, data, data.length),
                "write invalid key above range");

        // writeCountForKey / storedLengthForKey with invalid key
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.writeCountForKey(-1), "writeCountForKey invalid key");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_INVALID,
                module.storedLengthForKey(DeterministicNvConfigNativeModule.KEY_COUNT),
                "storedLengthForKey invalid key");

        // setWriteBudgetPerKeyForTest with negative budget
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.setWriteBudgetPerKeyForTest(-1), "negative write budget");

        // registerDispatchByteBuffer overflow
        int h1 = module.registerDispatchByteBuffer(new byte[]{1});
        int h2 = module.registerDispatchByteBuffer(new byte[]{2});
        assertTrue(h1 > 0, "first byte handle");
        assertTrue(h2 > 0, "second byte handle");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_DISPATCH_STORAGE_FULL,
                module.registerDispatchByteBuffer(new byte[]{3}), "byte storage full");

        // copyDispatchByteBuffer with invalid handle
        assertEquals(null, module.copyDispatchByteBuffer(0), "handle 0 invalid");
        assertEquals(null, module.copyDispatchByteBuffer(999), "handle 999 invalid");

        // Dispatch: invalid argument count
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(5, 5, 5, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(5), "compat");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{0}),
                "dispatch read wrong arg count");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE,
                        new int[]{0, 1}),
                "dispatch write wrong arg count");

        // Dispatch: invalid buffer handle
        DeterministicNvConfigNativeModule cleanModule = new DeterministicNvConfigNativeModule();
        VersionedNativeDispatchTable cleanTable =
                VersionedNativeDispatchTable.createDefault(5, 5, 5, cleanModule.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, cleanTable.verifyCompatibility(5), "clean compat");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                cleanTable.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{0, 999}),
                "dispatch read invalid buffer handle");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                cleanTable.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE,
                        new int[]{0, 999, 1}),
                "dispatch write invalid buffer handle");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        boolean equal = (expected == null) ? (actual == null) : expected.equals(actual);
        if (!equal) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }
}
