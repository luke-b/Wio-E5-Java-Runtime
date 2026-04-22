package wioe5.runtime;

public final class DeterministicLoRaNativeModuleTest {
    private DeterministicLoRaNativeModuleTest() {
    }

    public static void main(String[] args) {
        testJoinSendAndProcessContract();
        testDownlinkAndRadioMetrics();
        testDispatchIntegration();
        testNegativePaths();
    }

    private static void testJoinSendAndProcessContract() {
        DeterministicLoRaNativeModule module = new DeterministicLoRaNativeModule();

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.init(DeterministicLoRaNativeModule.REGION_EU868), "init");
        assertEquals(DeterministicLoRaNativeModule.REGION_EU868, module.activeRegion(), "active region");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.setJoinPacketLossBudgetForTest(2), "join packet loss budget");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.joinOTAA(new byte[8], new byte[8], new byte[16]),
                "joinOTAA");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINING, module.getStatus(), "joining status");
        assertEquals(DeterministicLoRaNativeModule.ERROR_JOIN_IN_PROGRESS,
                module.joinOTAA(new byte[8], new byte[8], new byte[16]),
                "joinOTAA blocked while joining");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "process join tick 1");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINING, module.getStatus(), "joining after loss 1");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "process join tick 2");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINING, module.getStatus(), "joining after loss 2");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "process join complete");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINED, module.getStatus(), "joined");
        assertEquals(2, module.joinPacketLossObserved(), "join packet loss observed");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.setTxPacketLossBudgetForTest(1), "tx packet loss budget");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.setDutyCycleHoldTicksForTest(2), "duty-cycle hold");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.send(new byte[]{1, 2, 3}, 3, 10, true),
                "send confirmed");
        assertEquals(DeterministicLoRaNativeModule.STATUS_TX_BUSY, module.getStatus(), "tx busy");
        assertEquals(DeterministicLoRaNativeModule.ERROR_MAC_BUSY,
                module.send(new byte[]{4}, 1, 10, false),
                "send blocked while busy");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "tx process 1");
        assertEquals(DeterministicLoRaNativeModule.STATUS_TX_BUSY, module.getStatus(), "tx still busy");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "tx process 2");
        assertEquals(DeterministicLoRaNativeModule.STATUS_TX_BUSY, module.getStatus(), "tx retransmit due to packet loss");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "tx complete after retransmit");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINED, module.getStatus(), "joined after tx done");
        assertEquals(1, module.txPacketLossObserved(), "tx packet loss observed");

        assertEquals(DeterministicLoRaNativeModule.ERROR_DUTY_CYCLE_RESTRICTED,
                module.send(new byte[]{1}, 1, 1, false),
                "duty-cycle send blocked");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "duty-cycle decay tick 1");
        assertEquals(DeterministicLoRaNativeModule.ERROR_DUTY_CYCLE_RESTRICTED,
                module.send(new byte[]{1}, 1, 1, false),
                "duty-cycle still blocked");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.process(), "duty-cycle decay tick 2");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.send(new byte[]{1}, 1, 1, false),
                "send allowed after duty cycle");
    }

    private static void testDownlinkAndRadioMetrics() {
        DeterministicLoRaNativeModule module = new DeterministicLoRaNativeModule();
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK, module.init(DeterministicLoRaNativeModule.REGION_US915), "init");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.joinABP(new byte[4], new byte[16], new byte[16]),
                "joinABP");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINED, module.getStatus(), "joined");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.registerDownlinkForTest(new byte[]{9, 8, 7}, 3, 5, -72, 75),
                "register downlink");
        assertEquals(DeterministicLoRaNativeModule.STATUS_RX_PENDING, module.getStatus(), "rx pending");

        byte[] payload = new byte[8];
        int[] portOut = new int[1];
        assertEquals(3, module.readDownlink(payload, portOut), "read downlink");
        assertEquals(9, payload[0] & 0xFF, "payload[0]");
        assertEquals(8, payload[1] & 0xFF, "payload[1]");
        assertEquals(7, payload[2] & 0xFF, "payload[2]");
        assertEquals(5, portOut[0], "port");
        assertEquals(-72, module.getLastRSSI(), "rssi");
        assertEquals(75, module.getLastSNR(), "snr");
        assertEquals(0, module.readDownlink(payload, portOut), "read no pending downlink");
    }

    private static void testDispatchIntegration() {
        DeterministicLoRaNativeModule module = new DeterministicLoRaNativeModule();
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(4, 4, 4, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(4), "compatibility");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_INIT,
                        new int[]{DeterministicLoRaNativeModule.REGION_AS923}),
                "dispatch init");

        int devEui = module.registerDispatchByteBuffer(new byte[8]);
        int appEui = module.registerDispatchByteBuffer(new byte[8]);
        int appKey = module.registerDispatchByteBuffer(new byte[16]);
        int sendPayload = module.registerDispatchByteBuffer(new byte[]{3, 2, 1});
        int readPayload = module.registerDispatchByteBuffer(new byte[4]);
        int portOut = module.registerDispatchIntBuffer(new int[1]);
        assertTrue(devEui > 0 && appEui > 0 && appKey > 0 && sendPayload > 0 && readPayload > 0 && portOut > 0, "dispatch handles");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_JOIN_OTAA,
                        new int[]{devEui, appEui, appKey}),
                "dispatch joinOTAA");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_PROCESS,
                        new int[0]),
                "dispatch process join completion");
        assertEquals(DeterministicLoRaNativeModule.STATUS_JOINED,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_GET_STATUS,
                        new int[0]),
                "dispatch status joined");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_SET_ADR,
                        new int[]{0}),
                "dispatch set ADR");
        assertTrue(!module.adrEnabled(), "adr disabled");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_SET_TX_POWER,
                        new int[]{18}),
                "dispatch set tx power");
        assertEquals(18, module.txPowerDbm(), "tx power updated");

        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_SEND,
                        new int[]{sendPayload, 3, 8, 0}),
                "dispatch send");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.registerDownlinkForTest(new byte[]{42, 43}, 2, 8, -88, 40),
                "enqueue downlink");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_PROCESS,
                        new int[0]),
                "dispatch process tx");

        assertEquals(2,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_READ_DOWNLINK,
                        new int[]{readPayload, portOut}),
                "dispatch readDownlink");
        byte[] copied = module.copyDispatchByteBuffer(readPayload);
        int[] copiedPortOut = module.copyDispatchIntBuffer(portOut);
        assertEquals(42, copied[0] & 0xFF, "dispatch payload[0]");
        assertEquals(43, copied[1] & 0xFF, "dispatch payload[1]");
        assertEquals(8, copiedPortOut[0], "dispatch port");
        assertEquals(-88,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_GET_LAST_RSSI,
                        new int[0]),
                "dispatch rssi");
        assertEquals(40,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_GET_LAST_SNR,
                        new int[0]),
                "dispatch snr");
        assertEquals(VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                        new int[0]),
                "non-LoRa symbols unresolved in LoRa handlers");
    }

    private static void testNegativePaths() {
        DeterministicLoRaNativeModule module = new DeterministicLoRaNativeModule(1, 1, 1);

        assertEquals(DeterministicLoRaNativeModule.ERROR_NOT_INITIALIZED,
                module.send(new byte[]{1}, 1, 1, false),
                "send before init");
        assertEquals(DeterministicLoRaNativeModule.ERROR_REGION_UNSUPPORTED,
                module.init(99),
                "unsupported region");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.init(DeterministicLoRaNativeModule.REGION_EU868),
                "init");
        assertEquals(DeterministicLoRaNativeModule.ERROR_KEY_LENGTH_INVALID,
                module.joinOTAA(new byte[7], new byte[8], new byte[16]),
                "invalid OTAA key lengths");
        assertEquals(DeterministicLoRaNativeModule.ERROR_KEY_LENGTH_INVALID,
                module.joinABP(new byte[5], new byte[16], new byte[16]),
                "invalid ABP key lengths");
        assertEquals(DeterministicLoRaNativeModule.ERROR_NOT_JOINED,
                module.send(new byte[]{1}, 1, 1, false),
                "send before join");
        assertEquals(DeterministicLoRaNativeModule.STATUS_OK,
                module.joinABP(new byte[4], new byte[16], new byte[16]),
                "joinABP");
        assertEquals(DeterministicLoRaNativeModule.ERROR_PAYLOAD_LENGTH_OUT_OF_RANGE,
                module.send(new byte[243], 243, 1, false),
                "payload too long");
        assertEquals(DeterministicLoRaNativeModule.ERROR_PORT_OUT_OF_RANGE,
                module.send(new byte[]{1}, 1, 0, false),
                "invalid port");
        assertEquals(DeterministicLoRaNativeModule.ERROR_TX_POWER_OUT_OF_RANGE,
                module.setTxPower(100),
                "invalid tx power");
        assertEquals(DeterministicLoRaNativeModule.ERROR_INVALID_ARGUMENT,
                module.setDutyCycleHoldTicksForTest(-1),
                "invalid duty-cycle ticks");
        assertTrue(module.registerDispatchByteBuffer(new byte[]{1}) > 0, "byte handle allocation");
        assertEquals(DeterministicLoRaNativeModule.ERROR_DISPATCH_STORAGE_FULL,
                module.registerDispatchByteBuffer(new byte[]{2}),
                "byte storage full");
        assertTrue(module.registerDispatchIntBuffer(new int[]{1}) > 0, "int handle allocation");
        assertEquals(DeterministicLoRaNativeModule.ERROR_DISPATCH_STORAGE_FULL,
                module.registerDispatchIntBuffer(new int[]{2}),
                "int storage full");

        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(4, 4, 4, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(4), "compatibility");
        assertEquals(DeterministicLoRaNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_INIT,
                        new int[0]),
                "dispatch invalid arg length");
        assertEquals(DeterministicLoRaNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_JOIN_OTAA,
                        new int[]{999, 999, 999}),
                "dispatch invalid byte handles");
        assertEquals(DeterministicLoRaNativeModule.ERROR_INT_BUFFER_HANDLE_INVALID,
                table.dispatch(
                        VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                        VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_READ_DOWNLINK,
                        new int[]{1, 999}),
                "dispatch invalid int handle");
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
