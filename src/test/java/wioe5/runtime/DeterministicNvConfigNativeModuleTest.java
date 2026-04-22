package wioe5.runtime;

import wioe5.storage.NVConfig;

public final class DeterministicNvConfigNativeModuleTest {
    private DeterministicNvConfigNativeModuleTest() {
    }

    public static void main(String[] args) {
        testWriteReadPersistenceAndIntegrity();
        testWearAwareSlotRotation();
        testDispatchIntegration();
        testNegativePaths();
    }

    private static void testWriteReadPersistenceAndIntegrity() {
        DeterministicNvConfigNativeModule.FlashSector flashSector =
                new DeterministicNvConfigNativeModule.FlashSector(6, 4, DeterministicNvConfigNativeModule.MAX_VALUE_LENGTH);
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule(flashSector, 16);

        byte[] appKey = new byte[16];
        for (int i = 0; i < appKey.length; i++) {
            appKey[i] = (byte) (0xA0 + i);
        }
        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                module.write(NVConfig.KEY_LORA_APPKEY, appKey, appKey.length),
                "write app key");

        byte[] readBuffer = new byte[16];
        assertEquals(16, module.read(NVConfig.KEY_LORA_APPKEY, readBuffer), "read app key");
        for (int i = 0; i < 16; i++) {
            assertEquals(appKey[i] & 0xFF, readBuffer[i] & 0xFF, "app key byte " + i);
        }

        DeterministicNvConfigNativeModule afterReset = new DeterministicNvConfigNativeModule(flashSector, 16);
        byte[] resetReadBuffer = new byte[16];
        assertEquals(16, afterReset.read(NVConfig.KEY_LORA_APPKEY, resetReadBuffer), "read app key after reset");
        for (int i = 0; i < 16; i++) {
            assertEquals(appKey[i] & 0xFF, resetReadBuffer[i] & 0xFF, "app key byte after reset " + i);
        }

        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                afterReset.corruptLatestRecordForTest(NVConfig.KEY_LORA_APPKEY),
                "corrupt latest");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_VALUE_NOT_FOUND,
                afterReset.read(NVConfig.KEY_LORA_APPKEY, new byte[16]),
                "corruption rejected by checksum");
    }

    private static void testWearAwareSlotRotation() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();
        for (int i = 0; i < 8; i++) {
            byte[] value = new byte[]{(byte) i, (byte) (i + 1), (byte) (i + 2)};
            assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                    module.write(NVConfig.KEY_SENSOR_CAL, value, value.length),
                    "wear write " + i);
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        for (int slot = 0; slot < 4; slot++) {
            int count = module.slotWriteCountForTest(NVConfig.KEY_SENSOR_CAL, slot);
            min = Math.min(min, count);
            max = Math.max(max, count);
            sum += count;
        }

        assertEquals(8, sum, "total wear count");
        assertTrue(max - min <= 1, "wear spread is bounded");

        byte[] latest = new byte[8];
        assertEquals(3, module.read(NVConfig.KEY_SENSOR_CAL, latest), "read latest");
        assertEquals(7, latest[0] & 0xFF, "latest byte 0");
        assertEquals(8, latest[1] & 0xFF, "latest byte 1");
        assertEquals(9, latest[2] & 0xFF, "latest byte 2");
    }

    private static void testDispatchIntegration() {
        DeterministicNvConfigNativeModule module = new DeterministicNvConfigNativeModule();
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(5, 5, 5, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(5), "compatibility");

        int writeHandle = module.registerDispatchByteBuffer(new byte[]{4, 5, 6, 7});
        int readHandle = module.registerDispatchByteBuffer(new byte[8]);
        assertTrue(writeHandle > 0 && readHandle > 0, "dispatch handles");

        assertEquals(DeterministicNvConfigNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE,
                        new int[]{NVConfig.KEY_APP_VERSION, writeHandle, 4}),
                "dispatch write");
        assertEquals(4,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{NVConfig.KEY_APP_VERSION, readHandle}),
                "dispatch read");

        byte[] readBack = module.copyDispatchByteBuffer(readHandle);
        assertEquals(4, readBack[0] & 0xFF, "dispatch read byte 0");
        assertEquals(5, readBack[1] & 0xFF, "dispatch read byte 1");
        assertEquals(6, readBack[2] & 0xFF, "dispatch read byte 2");
        assertEquals(7, readBack[3] & 0xFF, "dispatch read byte 3");
    }

    private static void testNegativePaths() {
        DeterministicNvConfigNativeModule module =
                new DeterministicNvConfigNativeModule(new DeterministicNvConfigNativeModule.FlashSector(6, 2, 64), 1);

        assertEquals(DeterministicNvConfigNativeModule.ERROR_VALUE_NOT_FOUND,
                module.read(NVConfig.KEY_LORA_REGION, new byte[4]),
                "read missing key");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_KEY_OUT_OF_RANGE,
                module.write(99, new byte[]{1}, 1),
                "invalid key");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_LENGTH_OUT_OF_RANGE,
                module.write(NVConfig.KEY_LORA_REGION, new byte[65], 65),
                "value too large");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_LENGTH_OUT_OF_RANGE,
                module.write(NVConfig.KEY_LORA_REGION, new byte[]{1, 2}, 3),
                "len out of range");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                module.read(NVConfig.KEY_LORA_REGION, null),
                "null read buffer");

        assertTrue(module.registerDispatchByteBuffer(new byte[]{1}) > 0, "first dispatch handle");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_DISPATCH_STORAGE_FULL,
                module.registerDispatchByteBuffer(new byte[]{2}),
                "dispatch storage full");

        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(5, 5, 5, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(5), "compatibility");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_READ,
                        new int[]{NVConfig.KEY_LORA_REGION}),
                "dispatch read arg length");
        assertEquals(DeterministicNvConfigNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                        VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE,
                        new int[]{NVConfig.KEY_LORA_REGION, 999, 1}),
                "dispatch invalid handle");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + " expected true");
        }
    }
}
