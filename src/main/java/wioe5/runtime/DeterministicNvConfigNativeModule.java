package wioe5.runtime;

import wioe5.storage.NVConfig;

/**
 * Deterministic host-side implementation of {@code wioe5.storage.NVConfig}
 * with fixed-capacity wear-aware records and integrity checks.
 */
public final class DeterministicNvConfigNativeModule {
    public static final int STATUS_OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_KEY_OUT_OF_RANGE = -60;
    public static final int ERROR_LENGTH_OUT_OF_RANGE = -61;
    public static final int ERROR_BUFFER_HANDLE_INVALID = -62;
    public static final int ERROR_DISPATCH_STORAGE_FULL = -63;
    public static final int ERROR_VALUE_NOT_FOUND = -64;

    public static final int MAX_VALUE_LENGTH = 64;

    private static final int KEY_MIN = NVConfig.KEY_LORA_REGION;
    private static final int KEY_MAX = NVConfig.KEY_APP_VERSION;
    private static final int DEFAULT_SLOTS_PER_KEY = 4;

    private final FlashSector flashSector;
    private final byte[][] dispatchByteBuffers;

    public DeterministicNvConfigNativeModule() {
        this(new FlashSector(KEY_MAX + 1, DEFAULT_SLOTS_PER_KEY, MAX_VALUE_LENGTH), 32);
    }

    public DeterministicNvConfigNativeModule(FlashSector flashSector, int maxDispatchByteBuffers) {
        if (flashSector == null || maxDispatchByteBuffers <= 0) {
            throw new IllegalArgumentException("flashSector must be non-null and maxDispatchByteBuffers must be > 0");
        }
        this.flashSector = flashSector;
        this.dispatchByteBuffers = new byte[maxDispatchByteBuffers][];
    }

    public int read(int key, byte[] buffer) {
        if (buffer == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (!isValidKey(key)) {
            return ERROR_KEY_OUT_OF_RANGE;
        }

        SlotRef latest = findLatestValidSlot(key);
        if (latest == null) {
            return ERROR_VALUE_NOT_FOUND;
        }

        int bytesToCopy = latest.slot.length;
        if (bytesToCopy > buffer.length) {
            bytesToCopy = buffer.length;
        }
        for (int i = 0; i < bytesToCopy; i++) {
            buffer[i] = latest.slot.payload[i];
        }
        return bytesToCopy;
    }

    public int write(int key, byte[] data, int len) {
        if (!isValidKey(key)) {
            return ERROR_KEY_OUT_OF_RANGE;
        }
        int lengthStatus = validateDataLength(data, len);
        if (lengthStatus != STATUS_OK) {
            return lengthStatus;
        }

        SlotRef latest = findLatestValidSlot(key);
        int targetSlotIndex = latest == null ? 0 : (latest.slotIndex + 1) % flashSector.slotsPerKey();
        int generation = latest == null ? 1 : latest.slot.generation + 1;

        FlashSlot slot = flashSector.slotAt(key, targetSlotIndex);
        slot.erase();
        slot.write(generation, data, len, checksum(key, generation, data, len));
        return STATUS_OK;
    }

    public int registerDispatchByteBuffer(byte[] data) {
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        for (int i = 0; i < dispatchByteBuffers.length; i++) {
            if (dispatchByteBuffers[i] == null) {
                dispatchByteBuffers[i] = copyBytes(data);
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
        return copyBytes(buffer);
    }

    public int slotWriteCountForTest(int key, int slotIndex) {
        if (!isValidKey(key)) {
            return ERROR_KEY_OUT_OF_RANGE;
        }
        if (slotIndex < 0 || slotIndex >= flashSector.slotsPerKey()) {
            return ERROR_INVALID_ARGUMENT;
        }
        return flashSector.slotAt(key, slotIndex).eraseCount;
    }

    public int corruptLatestRecordForTest(int key) {
        if (!isValidKey(key)) {
            return ERROR_KEY_OUT_OF_RANGE;
        }
        SlotRef latest = findLatestValidSlot(key);
        if (latest == null) {
            return ERROR_VALUE_NOT_FOUND;
        }
        if (latest.slot.length == 0) {
            latest.slot.checksum ^= 0x55AA;
            return STATUS_OK;
        }
        latest.slot.payload[0] ^= 0x5A;
        return STATUS_OK;
    }

    public FlashSector flashSector() {
        return flashSector;
    }

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
        byte[] payload = resolveDispatchByteBuffer(args[1]);
        if (payload == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return write(args[0], payload, args[2]);
    }

    private SlotRef findLatestValidSlot(int key) {
        SlotRef latest = null;
        for (int slotIndex = 0; slotIndex < flashSector.slotsPerKey(); slotIndex++) {
            FlashSlot slot = flashSector.slotAt(key, slotIndex);
            if (!slot.programmed) {
                continue;
            }
            if (slot.length < 0 || slot.length > flashSector.maxValueLength()) {
                continue;
            }
            if (slot.checksum != checksum(key, slot.generation, slot.payload, slot.length)) {
                continue;
            }
            if (latest == null || slot.generation > latest.slot.generation) {
                latest = new SlotRef(slotIndex, slot);
            }
        }
        return latest;
    }

    private byte[] resolveDispatchByteBuffer(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchByteBuffers.length) {
            return null;
        }
        return dispatchByteBuffers[index];
    }

    private static int validateDataLength(byte[] data, int len) {
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (len < 0 || len > data.length || len > MAX_VALUE_LENGTH) {
            return ERROR_LENGTH_OUT_OF_RANGE;
        }
        return STATUS_OK;
    }

    private static boolean isValidKey(int key) {
        return key >= KEY_MIN && key <= KEY_MAX;
    }

    private static boolean argsLength(int[] args, int expectedLength) {
        return args != null && args.length == expectedLength;
    }

    private static int checksum(int key, int generation, byte[] data, int len) {
        int crc = 0xFFFF;
        crc = crc16Step(crc, key & 0xFF);
        crc = crc16Step(crc, generation & 0xFF);
        crc = crc16Step(crc, (generation >> 8) & 0xFF);
        crc = crc16Step(crc, len & 0xFF);
        for (int i = 0; i < len; i++) {
            crc = crc16Step(crc, data[i] & 0xFF);
        }
        return crc & 0xFFFF;
    }

    private static int crc16Step(int crc, int value) {
        crc ^= value << 8;
        for (int i = 0; i < 8; i++) {
            if ((crc & 0x8000) != 0) {
                crc = (crc << 1) ^ 0x1021;
            } else {
                crc <<= 1;
            }
        }
        return crc & 0xFFFF;
    }

    private static byte[] copyBytes(byte[] source) {
        byte[] copy = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i];
        }
        return copy;
    }

    private static final class SlotRef {
        private final int slotIndex;
        private final FlashSlot slot;

        private SlotRef(int slotIndex, FlashSlot slot) {
            this.slotIndex = slotIndex;
            this.slot = slot;
        }
    }

    public static final class FlashSector {
        private final FlashSlot[][] slots;
        private final int maxValueLength;

        public FlashSector(int keyCount, int slotsPerKey, int maxValueLength) {
            if (keyCount <= 0 || slotsPerKey <= 0 || maxValueLength <= 0) {
                throw new IllegalArgumentException("all capacities must be > 0");
            }
            this.slots = new FlashSlot[keyCount][slotsPerKey];
            this.maxValueLength = maxValueLength;
            for (int key = 0; key < keyCount; key++) {
                for (int slot = 0; slot < slotsPerKey; slot++) {
                    slots[key][slot] = new FlashSlot(maxValueLength);
                }
            }
        }

        private FlashSlot slotAt(int key, int slotIndex) {
            return slots[key][slotIndex];
        }

        private int slotsPerKey() {
            return slots[0].length;
        }

        private int maxValueLength() {
            return maxValueLength;
        }
    }

    private static final class FlashSlot {
        private final byte[] payload;
        private boolean programmed;
        private int generation;
        private int length;
        private int checksum;
        private int eraseCount;

        private FlashSlot(int maxValueLength) {
            this.payload = new byte[maxValueLength];
        }

        private void erase() {
            programmed = false;
            generation = 0;
            length = 0;
            checksum = 0;
            eraseCount++;
            for (int i = 0; i < payload.length; i++) {
                payload[i] = 0;
            }
        }

        private void write(int generation, byte[] data, int len, int checksum) {
            this.generation = generation;
            this.length = len;
            this.checksum = checksum;
            for (int i = 0; i < payload.length; i++) {
                payload[i] = i < len ? data[i] : 0;
            }
            programmed = true;
        }
    }
}
