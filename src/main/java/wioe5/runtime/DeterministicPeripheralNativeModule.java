package wioe5.runtime;

/**
 * Deterministic host-side native module for GPIO, I2C, and UART behavior.
 */
public final class DeterministicPeripheralNativeModule {
    public static final int STATUS_OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;

    public static final int ERROR_PIN_OUT_OF_RANGE = -10;
    public static final int ERROR_PIN_MODE_OUT_OF_RANGE = -11;
    public static final int ERROR_PIN_MODE_MISMATCH = -12;
    public static final int ERROR_DIGITAL_VALUE_OUT_OF_RANGE = -13;
    public static final int ERROR_ANALOG_VALUE_OUT_OF_RANGE = -14;

    public static final int ERROR_I2C_NOT_INITIALIZED = -20;
    public static final int ERROR_I2C_ALREADY_INITIALIZED = -21;
    public static final int ERROR_I2C_SPEED_UNSUPPORTED = -22;
    public static final int ERROR_I2C_ADDRESS_OUT_OF_RANGE = -23;
    public static final int ERROR_I2C_DEVICE_NOT_FOUND = -24;
    public static final int ERROR_LENGTH_OUT_OF_RANGE = -25;
    public static final int ERROR_BUFFER_HANDLE_INVALID = -26;
    public static final int ERROR_STRING_HANDLE_INVALID = -27;
    public static final int ERROR_DISPATCH_STORAGE_FULL = -28;

    public static final int ERROR_UART_OUT_OF_RANGE = -30;
    public static final int ERROR_UART_NOT_INITIALIZED = -31;
    public static final int ERROR_UART_ALREADY_INITIALIZED = -32;
    public static final int ERROR_UART_BAUD_OUT_OF_RANGE = -33;
    public static final int ERROR_UART_TX_OVERFLOW = -34;
    public static final int UART_READ_EMPTY = -1;

    private static final int GPIO_PIN_COUNT = 7;
    private static final int GPIO_MODE_INPUT = 0;
    private static final int GPIO_MODE_OUTPUT = 1;
    private static final int GPIO_MODE_INPUT_PULLUP = 2;
    private static final int GPIO_MODE_INPUT_PULLDOWN = 3;
    private static final int GPIO_MODE_ANALOG = 4;

    private static final int GPIO_LOW = 0;
    private static final int GPIO_HIGH = 1;
    private static final int ANALOG_MIN = 0;
    private static final int ANALOG_MAX = 4095;

    private static final int I2C_MIN_ADDRESS = 0x01;
    private static final int I2C_MAX_ADDRESS = 0x7F;

    private static final int UART_ONE = 1;
    private static final int UART_TWO = 2;
    private static final int UART_COUNT = 2;
    private static final int UART_MIN_BAUD = 1200;
    private static final int UART_MAX_BAUD = 921600;

    private final int[] pinModes;
    private final int[] digitalLevels;
    private final int[] analogLevels;
    private final int[] gpioLoopbackSourcePin;

    private final int i2cDeviceMemorySize;
    private final boolean[] i2cDevicePresent;
    private final byte[][] i2cDeviceMemory;
    private final int[] i2cRegisterPointer;
    private boolean i2cInitialized;
    private int i2cSpeedKhz;

    private final int uartBufferCapacity;
    private final boolean[] uartInitialized;
    private final int[] uartBaud;
    private final byte[][] uartRxBuffers;
    private final byte[][] uartTxBuffers;
    private final int[] uartRxHead;
    private final int[] uartRxTail;
    private final int[] uartRxCount;
    private final int[] uartTxHead;
    private final int[] uartTxTail;
    private final int[] uartTxCount;
    private final int[] uartLoopbackPeer;

    private final byte[][] dispatchByteBuffers;
    private final String[] dispatchStrings;

    public DeterministicPeripheralNativeModule(int i2cDeviceMemorySize, int uartBufferCapacity) {
        this(i2cDeviceMemorySize, uartBufferCapacity, 32, 16);
    }

    public DeterministicPeripheralNativeModule(
            int i2cDeviceMemorySize,
            int uartBufferCapacity,
            int maxDispatchByteBuffers,
            int maxDispatchStrings) {
        if (i2cDeviceMemorySize <= 0
                || uartBufferCapacity <= 0
                || maxDispatchByteBuffers <= 0
                || maxDispatchStrings <= 0) {
            throw new IllegalArgumentException("all capacities must be > 0");
        }
        this.i2cDeviceMemorySize = i2cDeviceMemorySize;
        this.uartBufferCapacity = uartBufferCapacity;
        this.pinModes = new int[GPIO_PIN_COUNT];
        this.digitalLevels = new int[GPIO_PIN_COUNT];
        this.analogLevels = new int[GPIO_PIN_COUNT];
        this.gpioLoopbackSourcePin = new int[GPIO_PIN_COUNT];

        this.i2cDevicePresent = new boolean[128];
        this.i2cDeviceMemory = new byte[128][i2cDeviceMemorySize];
        this.i2cRegisterPointer = new int[128];

        this.uartInitialized = new boolean[UART_COUNT];
        this.uartBaud = new int[UART_COUNT];
        this.uartRxBuffers = new byte[UART_COUNT][uartBufferCapacity];
        this.uartTxBuffers = new byte[UART_COUNT][uartBufferCapacity];
        this.uartRxHead = new int[UART_COUNT];
        this.uartRxTail = new int[UART_COUNT];
        this.uartRxCount = new int[UART_COUNT];
        this.uartTxHead = new int[UART_COUNT];
        this.uartTxTail = new int[UART_COUNT];
        this.uartTxCount = new int[UART_COUNT];
        this.uartLoopbackPeer = new int[UART_COUNT];

        this.dispatchByteBuffers = new byte[maxDispatchByteBuffers][];
        this.dispatchStrings = new String[maxDispatchStrings];

        for (int i = 0; i < GPIO_PIN_COUNT; i++) {
            pinModes[i] = GPIO_MODE_INPUT;
            digitalLevels[i] = GPIO_LOW;
            analogLevels[i] = ANALOG_MIN;
            gpioLoopbackSourcePin[i] = -1;
        }
        for (int i = 0; i < UART_COUNT; i++) {
            uartLoopbackPeer[i] = -1;
        }
    }

    public int pinMode(int pin, int mode) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (!isValidMode(mode)) {
            return ERROR_PIN_MODE_OUT_OF_RANGE;
        }
        pinModes[pin] = mode;
        return STATUS_OK;
    }

    public int digitalWrite(int pin, int value) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (pinModes[pin] != GPIO_MODE_OUTPUT) {
            return ERROR_PIN_MODE_MISMATCH;
        }
        if (value != GPIO_LOW && value != GPIO_HIGH) {
            return ERROR_DIGITAL_VALUE_OUT_OF_RANGE;
        }
        digitalLevels[pin] = value;
        propagateGpioLoopback(pin, value);
        return STATUS_OK;
    }

    public int digitalRead(int pin) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (pinModes[pin] == GPIO_MODE_ANALOG) {
            return ERROR_PIN_MODE_MISMATCH;
        }
        return digitalLevels[pin];
    }

    public int analogRead(int pin) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (pinModes[pin] != GPIO_MODE_ANALOG) {
            return ERROR_PIN_MODE_MISMATCH;
        }
        return analogLevels[pin];
    }

    public int setAnalogInputForTest(int pin, int analogValue) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (analogValue < ANALOG_MIN || analogValue > ANALOG_MAX) {
            return ERROR_ANALOG_VALUE_OUT_OF_RANGE;
        }
        analogLevels[pin] = analogValue;
        return STATUS_OK;
    }

    public int setDigitalInputForTest(int pin, int value) {
        if (!isValidPin(pin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (value != GPIO_LOW && value != GPIO_HIGH) {
            return ERROR_DIGITAL_VALUE_OUT_OF_RANGE;
        }
        if (pinModes[pin] == GPIO_MODE_OUTPUT) {
            return ERROR_PIN_MODE_MISMATCH;
        }
        digitalLevels[pin] = value;
        return STATUS_OK;
    }

    public int configureGpioLoopback(int outputPin, int inputPin) {
        if (!isValidPin(outputPin) || !isValidPin(inputPin)) {
            return ERROR_PIN_OUT_OF_RANGE;
        }
        if (outputPin == inputPin) {
            return ERROR_INVALID_ARGUMENT;
        }
        gpioLoopbackSourcePin[inputPin] = outputPin;
        return STATUS_OK;
    }

    public int beginI2c(int speedKhz) {
        if (i2cInitialized) {
            return ERROR_I2C_ALREADY_INITIALIZED;
        }
        if (speedKhz != 100 && speedKhz != 400) {
            return ERROR_I2C_SPEED_UNSUPPORTED;
        }
        i2cInitialized = true;
        i2cSpeedKhz = speedKhz;
        return STATUS_OK;
    }

    public int writeI2c(int address, byte[] data, int len) {
        if (!i2cInitialized) {
            return ERROR_I2C_NOT_INITIALIZED;
        }
        if (!isValidI2cAddress(address)) {
            return ERROR_I2C_ADDRESS_OUT_OF_RANGE;
        }
        int lengthStatus = validateBufferAndLength(data, len);
        if (lengthStatus != STATUS_OK) {
            return lengthStatus;
        }
        if (!i2cDevicePresent[address]) {
            return ERROR_I2C_DEVICE_NOT_FOUND;
        }
        if (len == 0) {
            return STATUS_OK;
        }

        int startRegister = data[0] & 0xFF;
        i2cRegisterPointer[address] = startRegister % i2cDeviceMemorySize;
        for (int i = 1; i < len; i++) {
            int register = (startRegister + i - 1) % i2cDeviceMemorySize;
            i2cDeviceMemory[address][register] = data[i];
        }
        i2cRegisterPointer[address] = (startRegister + len - 1) % i2cDeviceMemorySize;
        return len;
    }

    public int readI2c(int address, byte[] buffer, int len) {
        if (!i2cInitialized) {
            return ERROR_I2C_NOT_INITIALIZED;
        }
        if (!isValidI2cAddress(address)) {
            return ERROR_I2C_ADDRESS_OUT_OF_RANGE;
        }
        int lengthStatus = validateBufferAndLength(buffer, len);
        if (lengthStatus != STATUS_OK) {
            return lengthStatus;
        }
        if (!i2cDevicePresent[address]) {
            return ERROR_I2C_DEVICE_NOT_FOUND;
        }

        int pointer = i2cRegisterPointer[address];
        for (int i = 0; i < len; i++) {
            buffer[i] = i2cDeviceMemory[address][(pointer + i) % i2cDeviceMemorySize];
        }
        i2cRegisterPointer[address] = (pointer + len) % i2cDeviceMemorySize;
        return len;
    }

    public int writeReadI2c(int address, byte[] tx, int txLen, byte[] rx, int rxLen) {
        int writeStatus = writeI2c(address, tx, txLen);
        if (writeStatus < 0) {
            return writeStatus;
        }
        return readI2c(address, rx, rxLen);
    }

    public int endI2c() {
        if (!i2cInitialized) {
            return ERROR_I2C_NOT_INITIALIZED;
        }
        i2cInitialized = false;
        i2cSpeedKhz = 0;
        return STATUS_OK;
    }

    public int registerI2cDeviceForTest(int address, byte[] initialMemory) {
        if (!isValidI2cAddress(address) || initialMemory == null || initialMemory.length == 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        int copyLength = initialMemory.length;
        if (copyLength > i2cDeviceMemorySize) {
            copyLength = i2cDeviceMemorySize;
        }
        for (int i = 0; i < i2cDeviceMemorySize; i++) {
            i2cDeviceMemory[address][i] = 0;
        }
        for (int i = 0; i < copyLength; i++) {
            i2cDeviceMemory[address][i] = initialMemory[i];
        }
        i2cDevicePresent[address] = true;
        i2cRegisterPointer[address] = 0;
        return STATUS_OK;
    }

    public int beginUart(int uart, int baud) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0) {
            return ERROR_UART_OUT_OF_RANGE;
        }
        if (baud < UART_MIN_BAUD || baud > UART_MAX_BAUD) {
            return ERROR_UART_BAUD_OUT_OF_RANGE;
        }
        if (uartInitialized[uartIndex]) {
            return ERROR_UART_ALREADY_INITIALIZED;
        }
        uartInitialized[uartIndex] = true;
        uartBaud[uartIndex] = baud;
        clearUartBuffers(uartIndex);
        return STATUS_OK;
    }

    public int availableUart(int uart) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0) {
            return ERROR_UART_OUT_OF_RANGE;
        }
        if (!uartInitialized[uartIndex]) {
            return ERROR_UART_NOT_INITIALIZED;
        }
        return uartRxCount[uartIndex];
    }

    public int readUart(int uart) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0) {
            return ERROR_UART_OUT_OF_RANGE;
        }
        if (!uartInitialized[uartIndex]) {
            return ERROR_UART_NOT_INITIALIZED;
        }
        if (uartRxCount[uartIndex] == 0) {
            return UART_READ_EMPTY;
        }
        int value = uartRxBuffers[uartIndex][uartRxHead[uartIndex]] & 0xFF;
        uartRxHead[uartIndex] = (uartRxHead[uartIndex] + 1) % uartBufferCapacity;
        uartRxCount[uartIndex]--;
        return value;
    }

    public int writeUart(int uart, byte[] data, int len) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0) {
            return ERROR_UART_OUT_OF_RANGE;
        }
        if (!uartInitialized[uartIndex]) {
            return ERROR_UART_NOT_INITIALIZED;
        }
        int lengthStatus = validateBufferAndLength(data, len);
        if (lengthStatus != STATUS_OK) {
            return lengthStatus;
        }
        int peerUartIndex = uartLoopbackPeer[uartIndex];
        if (len > uartBufferCapacity) {
            return ERROR_UART_TX_OVERFLOW;
        }
        if (peerUartIndex >= 0
                && uartInitialized[peerUartIndex]
                && (uartBufferCapacity - uartRxCount[peerUartIndex] < len)) {
            return ERROR_UART_TX_OVERFLOW;
        }
        for (int i = 0; i < len; i++) {
            enqueueUartTx(uartIndex, data[i]);
        }
        clearUartTx(uartIndex);
        if (peerUartIndex >= 0 && uartInitialized[peerUartIndex]) {
            for (int i = 0; i < len; i++) {
                enqueueUartRx(peerUartIndex, data[i]);
            }
        }
        return len;
    }

    public int printUart(int uart, String text) {
        if (text == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] payload = asciiBytes(text, false);
        if (payload == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        return writeUart(uart, payload, payload.length);
    }

    public int printlnUart(int uart, String text) {
        if (text == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] payload = asciiBytes(text, true);
        if (payload == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        return writeUart(uart, payload, payload.length);
    }

    public int injectUartRxForTest(int uart, byte[] data) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0) {
            return ERROR_UART_OUT_OF_RANGE;
        }
        if (!uartInitialized[uartIndex]) {
            return ERROR_UART_NOT_INITIALIZED;
        }
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (uartBufferCapacity - uartRxCount[uartIndex] < data.length) {
            return ERROR_UART_TX_OVERFLOW;
        }
        for (int i = 0; i < data.length; i++) {
            enqueueUartRx(uartIndex, data[i]);
        }
        return data.length;
    }

    public int configureUartLoopback(int sourceUart, int targetUart) {
        int source = toUartIndex(sourceUart);
        int target = toUartIndex(targetUart);
        if (source < 0 || target < 0 || source == target) {
            return ERROR_INVALID_ARGUMENT;
        }
        uartLoopbackPeer[source] = target;
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

    public int registerDispatchString(String value) {
        if (value == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        for (int i = 0; i < dispatchStrings.length; i++) {
            if (dispatchStrings[i] == null) {
                dispatchStrings[i] = value;
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

    public boolean i2cInitialized() {
        return i2cInitialized;
    }

    public int i2cSpeedKhz() {
        return i2cSpeedKhz;
    }

    public int uartBaud(int uart) {
        int uartIndex = toUartIndex(uart);
        if (uartIndex < 0 || !uartInitialized[uartIndex]) {
            return ERROR_UART_NOT_INITIALIZED;
        }
        return uartBaud[uartIndex];
    }

    public VersionedNativeDispatchTable.NativeHandler[] createDefaultDispatchHandlers() {
        VersionedNativeDispatchTable.NativeHandler[] handlers =
                new VersionedNativeDispatchTable.NativeHandler[VersionedNativeDispatchTable.defaultBindingCount()];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = args -> VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND;
        }

        handlers[6] = args -> argsLength(args, 2) ? pinMode(args[0], args[1]) : ERROR_INVALID_ARGUMENT;
        handlers[7] = args -> argsLength(args, 2) ? digitalWrite(args[0], args[1]) : ERROR_INVALID_ARGUMENT;
        handlers[8] = args -> argsLength(args, 1) ? digitalRead(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[9] = args -> argsLength(args, 1) ? analogRead(args[0]) : ERROR_INVALID_ARGUMENT;

        handlers[21] = args -> argsLength(args, 1) ? beginI2c(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[22] = args -> dispatchI2cWrite(args);
        handlers[23] = args -> dispatchI2cRead(args);
        handlers[24] = args -> dispatchI2cWriteRead(args);
        handlers[25] = args -> argsLength(args, 0) ? endI2c() : ERROR_INVALID_ARGUMENT;

        handlers[26] = args -> argsLength(args, 2) ? beginUart(args[0], args[1]) : ERROR_INVALID_ARGUMENT;
        handlers[27] = args -> argsLength(args, 1) ? availableUart(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[28] = args -> argsLength(args, 1) ? readUart(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[29] = args -> dispatchUartWrite(args);
        handlers[30] = args -> dispatchUartPrint(args, false);
        handlers[31] = args -> dispatchUartPrint(args, true);
        return handlers;
    }

    private int dispatchI2cWrite(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] data = resolveDispatchByteBuffer(args[1]);
        if (data == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return writeI2c(args[0], data, args[2]);
    }

    private int dispatchI2cRead(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] buffer = resolveDispatchByteBuffer(args[1]);
        if (buffer == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return readI2c(args[0], buffer, args[2]);
    }

    private int dispatchI2cWriteRead(int[] args) {
        if (!argsLength(args, 5)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] tx = resolveDispatchByteBuffer(args[1]);
        byte[] rx = resolveDispatchByteBuffer(args[3]);
        if (tx == null || rx == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return writeReadI2c(args[0], tx, args[2], rx, args[4]);
    }

    private int dispatchUartWrite(int[] args) {
        if (!argsLength(args, 3)) {
            return ERROR_INVALID_ARGUMENT;
        }
        byte[] payload = resolveDispatchByteBuffer(args[1]);
        if (payload == null) {
            return ERROR_BUFFER_HANDLE_INVALID;
        }
        return writeUart(args[0], payload, args[2]);
    }

    private int dispatchUartPrint(int[] args, boolean lineEnding) {
        if (!argsLength(args, 2)) {
            return ERROR_INVALID_ARGUMENT;
        }
        String text = resolveDispatchString(args[1]);
        if (text == null) {
            return ERROR_STRING_HANDLE_INVALID;
        }
        return lineEnding ? printlnUart(args[0], text) : printUart(args[0], text);
    }

    private void propagateGpioLoopback(int outputPin, int value) {
        for (int i = 0; i < gpioLoopbackSourcePin.length; i++) {
            if (gpioLoopbackSourcePin[i] == outputPin) {
                digitalLevels[i] = value;
            }
        }
    }

    private void clearUartBuffers(int uartIndex) {
        clearUartTx(uartIndex);
        clearUartRx(uartIndex);
    }

    private void clearUartTx(int uartIndex) {
        uartTxHead[uartIndex] = 0;
        uartTxTail[uartIndex] = 0;
        uartTxCount[uartIndex] = 0;
    }

    private void clearUartRx(int uartIndex) {
        uartRxHead[uartIndex] = 0;
        uartRxTail[uartIndex] = 0;
        uartRxCount[uartIndex] = 0;
    }

    private void enqueueUartTx(int uartIndex, byte value) {
        uartTxBuffers[uartIndex][uartTxTail[uartIndex]] = value;
        uartTxTail[uartIndex] = (uartTxTail[uartIndex] + 1) % uartBufferCapacity;
        uartTxCount[uartIndex]++;
    }

    private void enqueueUartRx(int uartIndex, byte value) {
        uartRxBuffers[uartIndex][uartRxTail[uartIndex]] = value;
        uartRxTail[uartIndex] = (uartRxTail[uartIndex] + 1) % uartBufferCapacity;
        uartRxCount[uartIndex]++;
    }

    private static byte[] asciiBytes(String text, boolean appendCrlf) {
        int suffix = appendCrlf ? 2 : 0;
        byte[] payload = new byte[text.length() + suffix];
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch > 0x7F) {
                return null;
            }
            payload[i] = (byte) ch;
        }
        if (appendCrlf) {
            payload[payload.length - 2] = '\r';
            payload[payload.length - 1] = '\n';
        }
        return payload;
    }

    private static int validateBufferAndLength(byte[] data, int len) {
        if (data == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (len < 0 || len > data.length) {
            return ERROR_LENGTH_OUT_OF_RANGE;
        }
        return STATUS_OK;
    }

    private static boolean argsLength(int[] args, int expectedLength) {
        return args != null && args.length == expectedLength;
    }

    private static boolean isValidPin(int pin) {
        return pin >= 0 && pin < GPIO_PIN_COUNT;
    }

    private static boolean isValidMode(int mode) {
        return mode >= GPIO_MODE_INPUT && mode <= GPIO_MODE_ANALOG;
    }

    private static boolean isValidI2cAddress(int address) {
        return address >= I2C_MIN_ADDRESS && address <= I2C_MAX_ADDRESS;
    }

    private static byte[] copyBytes(byte[] source) {
        byte[] copy = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i];
        }
        return copy;
    }

    private int toUartIndex(int uart) {
        if (uart == UART_ONE) {
            return 0;
        }
        if (uart == UART_TWO) {
            return 1;
        }
        return -1;
    }

    private byte[] resolveDispatchByteBuffer(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchByteBuffers.length) {
            return null;
        }
        return dispatchByteBuffers[index];
    }

    private String resolveDispatchString(int handle) {
        int index = handle - 1;
        if (index < 0 || index >= dispatchStrings.length) {
            return null;
        }
        return dispatchStrings[index];
    }
}
