package wioe5.runtime;

/**
 * Deterministic host-side implementation of {@code wioe5.lora.LoRaWAN}
 * native behavior.
 */
public final class DeterministicLoRaNativeModule {
    public static final int STATUS_OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_NOT_INITIALIZED = -40;
    public static final int ERROR_REGION_UNSUPPORTED = -41;
    public static final int ERROR_NOT_JOINED = -42;
    public static final int ERROR_JOIN_IN_PROGRESS = -43;
    public static final int ERROR_MAC_BUSY = -44;
    public static final int ERROR_DUTY_CYCLE_RESTRICTED = -45;
    public static final int ERROR_PAYLOAD_LENGTH_OUT_OF_RANGE = -46;
    public static final int ERROR_PORT_OUT_OF_RANGE = -47;
    public static final int ERROR_KEY_LENGTH_INVALID = -48;
    public static final int ERROR_BUFFER_HANDLE_INVALID = -49;
    public static final int ERROR_INT_BUFFER_HANDLE_INVALID = -50;
    public static final int ERROR_DISPATCH_STORAGE_FULL = -51;
    public static final int ERROR_TX_POWER_OUT_OF_RANGE = -52;

    public static final int REGION_EU868 = 0;
    public static final int REGION_US915 = 1;
    public static final int REGION_AS923 = 2;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_JOINING = 1;
    public static final int STATUS_JOINED = 2;
    public static final int STATUS_TX_BUSY = 3;
    public static final int STATUS_RX_PENDING = 4;

    private static final int OTAA_EUI_LENGTH = 8;
    private static final int OTAA_APP_KEY_LENGTH = 16;
    private static final int ABP_DEV_ADDR_LENGTH = 4;
    private static final int ABP_SESSION_KEY_LENGTH = 16;
    private static final int MAX_UPLINK_LENGTH = 242;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 223;
    private static final int MIN_TX_POWER_DBM = -9;
    private static final int MAX_TX_POWER_DBM = 22;

    private final byte[][] dispatchByteBuffers;
    private final int[][] dispatchIntBuffers;

    private final byte[][] downlinkPayloads;
    private final int[] downlinkLengths;
    private final int[] downlinkPorts;
    private final int[] downlinkRssi;
    private final int[] downlinkSnrTenths;
    private int downlinkHead;
    private int downlinkTail;
    private int downlinkCount;

    private boolean initialized;
    private int region = -1;
    private int status = STATUS_IDLE;
    private int txPowerDbm = 14;
    private boolean adrEnabled = true;
    private int lastRssi = -120;
    private int lastSnrTenths = -200;
    private int processTickCount;
    private int joinPacketLossBudget;
    private int joinPacketLossObserved;
    private int txPacketLossBudget;
    private int txPacketLossObserved;
    private int txBusyTicksRemaining;
    private int dutyCycleTicksRemaining;
    private int dutyCycleHoldTicks = 1;

    public DeterministicLoRaNativeModule() {
        this(32, 16, 8);
    }

    public DeterministicLoRaNativeModule(int maxDispatchByteBuffers, int maxDispatchIntBuffers, int maxDownlinkQueueDepth) {
        if (maxDispatchByteBuffers <= 0 || maxDispatchIntBuffers <= 0 || maxDownlinkQueueDepth <= 0) {
            throw new IllegalArgumentException("all capacities must be > 0");
        }
        this.dispatchByteBuffers = new byte[maxDispatchByteBuffers][];
        this.dispatchIntBuffers = new int[maxDispatchIntBuffers][];
        this.downlinkPayloads = new byte[maxDownlinkQueueDepth][];
        this.downlinkLengths = new int[maxDownlinkQueueDepth];
        this.downlinkPorts = new int[maxDownlinkQueueDepth];
        this.downlinkRssi = new int[maxDownlinkQueueDepth];
        this.downlinkSnrTenths = new int[maxDownlinkQueueDepth];
    }

    public int init(int newRegion) {
        if (!isValidRegion(newRegion)) {
            return ERROR_REGION_UNSUPPORTED;
        }
        initialized = true;
        region = newRegion;
        status = STATUS_IDLE;
        processTickCount = 0;
        joinPacketLossObserved = 0;
        txPacketLossObserved = 0;
        txBusyTicksRemaining = 0;
        dutyCycleTicksRemaining = 0;
        downlinkHead = 0;
        downlinkTail = 0;
        downlinkCount = 0;
        return STATUS_OK;
    }

    public int joinOTAA(byte[] devEui, byte[] appEui, byte[] appKey) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        if (!isLength(devEui, OTAA_EUI_LENGTH) || !isLength(appEui, OTAA_EUI_LENGTH) || !isLength(appKey, OTAA_APP_KEY_LENGTH)) {
            return ERROR_KEY_LENGTH_INVALID;
        }
        if (status == STATUS_JOINING) {
            return ERROR_JOIN_IN_PROGRESS;
        }
        status = STATUS_JOINING;
        return STATUS_OK;
    }

    public int joinABP(byte[] devAddr, byte[] nwkSKey, byte[] appSKey) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        if (!isLength(devAddr, ABP_DEV_ADDR_LENGTH)
                || !isLength(nwkSKey, ABP_SESSION_KEY_LENGTH)
                || !isLength(appSKey, ABP_SESSION_KEY_LENGTH)) {
            return ERROR_KEY_LENGTH_INVALID;
        }
        status = STATUS_JOINED;
        return STATUS_OK;
    }

    public int send(byte[] data, int len, int port, boolean confirmed) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        if (txBusyTicksRemaining > 0) {
            return ERROR_MAC_BUSY;
        }
        if (status != STATUS_JOINED) {
            return ERROR_NOT_JOINED;
        }
        if (dutyCycleTicksRemaining > 0) {
            return ERROR_DUTY_CYCLE_RESTRICTED;
        }
        if (data == null || len < 0 || len > data.length || len > MAX_UPLINK_LENGTH) {
            return ERROR_PAYLOAD_LENGTH_OUT_OF_RANGE;
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            return ERROR_PORT_OUT_OF_RANGE;
        }
        if (len == 0) {
            return ERROR_PAYLOAD_LENGTH_OUT_OF_RANGE;
        }

        status = STATUS_TX_BUSY;
        txBusyTicksRemaining = confirmed ? 2 : 1;
        return STATUS_OK;
    }

    public int process() {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        processTickCount++;

        if (dutyCycleTicksRemaining > 0) {
            dutyCycleTicksRemaining--;
        }

        if (status == STATUS_JOINING) {
            if (joinPacketLossObserved < joinPacketLossBudget) {
                joinPacketLossObserved++;
                return STATUS_OK;
            }
            status = STATUS_JOINED;
            return STATUS_OK;
        }

        if (status == STATUS_TX_BUSY) {
            if (txBusyTicksRemaining > 0) {
                txBusyTicksRemaining--;
            }
            if (txBusyTicksRemaining == 0) {
                if (txPacketLossObserved < txPacketLossBudget) {
                    txPacketLossObserved++;
                    txBusyTicksRemaining = 1;
                    return STATUS_OK;
                }
                dutyCycleTicksRemaining = dutyCycleHoldTicks;
                status = downlinkCount > 0 ? STATUS_RX_PENDING : STATUS_JOINED;
            }
            return STATUS_OK;
        }

        if (status == STATUS_JOINED && downlinkCount > 0) {
            status = STATUS_RX_PENDING;
        } else if (status == STATUS_RX_PENDING && downlinkCount == 0) {
            status = STATUS_JOINED;
        }
        return STATUS_OK;
    }

    public int getStatus() {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        return status;
    }

    public int readDownlink(byte[] buffer, int[] portOut) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        if (buffer == null || portOut == null || portOut.length < 1) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (downlinkCount == 0) {
            if (status == STATUS_RX_PENDING) {
                status = STATUS_JOINED;
            }
            return 0;
        }
        int slot = downlinkHead;
        int bytesToCopy = downlinkLengths[slot];
        if (bytesToCopy > buffer.length) {
            bytesToCopy = buffer.length;
        }
        for (int i = 0; i < bytesToCopy; i++) {
            buffer[i] = downlinkPayloads[slot][i];
        }
        portOut[0] = downlinkPorts[slot];
        lastRssi = downlinkRssi[slot];
        lastSnrTenths = downlinkSnrTenths[slot];

        downlinkPayloads[slot] = null;
        downlinkLengths[slot] = 0;
        downlinkPorts[slot] = 0;
        downlinkRssi[slot] = 0;
        downlinkSnrTenths[slot] = 0;
        downlinkHead = (downlinkHead + 1) % downlinkPayloads.length;
        downlinkCount--;
        if (downlinkCount == 0) {
            status = STATUS_JOINED;
        }
        return bytesToCopy;
    }

    public int setTxPower(int dbm) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        if (dbm < MIN_TX_POWER_DBM || dbm > MAX_TX_POWER_DBM) {
            return ERROR_TX_POWER_OUT_OF_RANGE;
        }
        txPowerDbm = dbm;
        return STATUS_OK;
    }

    public int setADR(boolean enabled) {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        adrEnabled = enabled;
        return STATUS_OK;
    }

    public int getLastRSSI() {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        return lastRssi;
    }

    public int getLastSNR() {
        if (!initialized) {
            return ERROR_NOT_INITIALIZED;
        }
        return lastSnrTenths;
    }

    public int registerDownlinkForTest(byte[] data, int len, int port, int rssi, int snrTenths) {
        if (data == null || len < 0 || len > data.length || len > MAX_UPLINK_LENGTH) {
            return ERROR_PAYLOAD_LENGTH_OUT_OF_RANGE;
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            return ERROR_PORT_OUT_OF_RANGE;
        }
        if (downlinkCount >= downlinkPayloads.length) {
            return ERROR_DISPATCH_STORAGE_FULL;
        }
        byte[] payload = new byte[len];
        for (int i = 0; i < len; i++) {
            payload[i] = data[i];
        }
        int slot = downlinkTail;
        downlinkPayloads[slot] = payload;
        downlinkLengths[slot] = len;
        downlinkPorts[slot] = port;
        downlinkRssi[slot] = rssi;
        downlinkSnrTenths[slot] = snrTenths;
        downlinkTail = (downlinkTail + 1) % downlinkPayloads.length;
        downlinkCount++;
        if (initialized && status == STATUS_JOINED) {
            status = STATUS_RX_PENDING;
        }
        return STATUS_OK;
    }

    public int setJoinPacketLossBudgetForTest(int packetLossCount) {
        if (packetLossCount < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        joinPacketLossBudget = packetLossCount;
        joinPacketLossObserved = 0;
        return STATUS_OK;
    }

    public int setTxPacketLossBudgetForTest(int packetLossCount) {
        if (packetLossCount < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        txPacketLossBudget = packetLossCount;
        txPacketLossObserved = 0;
        return STATUS_OK;
    }

    public int setDutyCycleHoldTicksForTest(int ticks) {
        if (ticks < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        dutyCycleHoldTicks = ticks;
        return STATUS_OK;
    }

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

    public int registerDispatchIntBuffer(int[] data) {
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        for (int i = 0; i < dispatchIntBuffers.length; i++) {
            if (dispatchIntBuffers[i] == null) {
                int[] copy = new int[data.length];
                for (int j = 0; j < data.length; j++) {
                    copy[j] = data[j];
                }
                dispatchIntBuffers[i] = copy;
                return i + 1;
            }
        }
        return ERROR_DISPATCH_STORAGE_FULL;
    }

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

    public int[] copyDispatchIntBuffer(int handle) {
        int[] buffer = resolveDispatchIntBuffer(handle);
        if (buffer == null) {
            return null;
        }
        int[] copy = new int[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            copy[i] = buffer[i];
        }
        return copy;
    }

    public int processTickCount() {
        return processTickCount;
    }

    public int activeRegion() {
        return region;
    }

    public int txPowerDbm() {
        return txPowerDbm;
    }

    public boolean adrEnabled() {
        return adrEnabled;
    }

    public int joinPacketLossObserved() {
        return joinPacketLossObserved;
    }

    public int txPacketLossObserved() {
        return txPacketLossObserved;
    }

    public VersionedNativeDispatchTable.NativeHandler[] createDefaultDispatchHandlers() {
        VersionedNativeDispatchTable.NativeHandler[] handlers =
                new VersionedNativeDispatchTable.NativeHandler[VersionedNativeDispatchTable.defaultBindingCount()];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = args -> VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND;
        }

        handlers[10] = args -> argsLength(args, 1) ? init(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[11] = this::dispatchJoinOtaa;
        handlers[12] = this::dispatchJoinAbp;
        handlers[13] = this::dispatchSend;
        handlers[14] = args -> argsLength(args, 0) ? process() : ERROR_INVALID_ARGUMENT;
        handlers[15] = args -> argsLength(args, 0) ? getStatus() : ERROR_INVALID_ARGUMENT;
        handlers[16] = this::dispatchReadDownlink;
        handlers[17] = args -> argsLength(args, 1) ? setTxPower(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[18] = args -> argsLength(args, 1) ? setADR(args[0] != 0) : ERROR_INVALID_ARGUMENT;
        handlers[19] = args -> argsLength(args, 0) ? getLastRSSI() : ERROR_INVALID_ARGUMENT;
        handlers[20] = args -> argsLength(args, 0) ? getLastSNR() : ERROR_INVALID_ARGUMENT;
        return handlers;
    }

    private int dispatchJoinOtaa(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] devEui = resolveDispatchByteBuffer(args[0]);
        byte[] appEui = resolveDispatchByteBuffer(args[1]);
        byte[] appKey = resolveDispatchByteBuffer(args[2]);
        if (devEui == null || appEui == null || appKey == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return joinOTAA(devEui, appEui, appKey);
    }

    private int dispatchJoinAbp(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] devAddr = resolveDispatchByteBuffer(args[0]);
        byte[] nwkSKey = resolveDispatchByteBuffer(args[1]);
        byte[] appSKey = resolveDispatchByteBuffer(args[2]);
        if (devAddr == null || nwkSKey == null || appSKey == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return joinABP(devAddr, nwkSKey, appSKey);
    }

    private int dispatchSend(int[] args) {
        if (!argsLength(args, 4)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] payload = resolveDispatchByteBuffer(args[0]);
        if (payload == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return send(payload, args[1], args[2], args[3] != 0);
    }

    private int dispatchReadDownlink(int[] args) {
        if (!argsLength(args, 2)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] payload = resolveDispatchByteBuffer(args[0]);
        if (payload == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        int[] portOut = resolveDispatchIntBuffer(args[1]);
        if (portOut == null || portOut.length < 1) {
            return ERROR_INT_BUFFER_HANDLE_INVALID;
        }
        return readDownlink(payload, portOut);
    }

    private static boolean argsLength(int[] args, int expectedLength) {
        return args != null && args.length == expectedLength;
    }

    private static boolean isValidRegion(int candidateRegion) {
        return candidateRegion == REGION_EU868
                || candidateRegion == REGION_US915
                || candidateRegion == REGION_AS923;
    }

    private static boolean isLength(byte[] data, int requiredLength) {
        return data != null && data.length == requiredLength;
    }

    private byte[] resolveDispatchByteBuffer(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchByteBuffers.length) {
            return null;
        }
        return dispatchByteBuffers[index];
    }

    private int[] resolveDispatchIntBuffer(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchIntBuffers.length) {
            return null;
        }
        return dispatchIntBuffers[index];
    }
}
