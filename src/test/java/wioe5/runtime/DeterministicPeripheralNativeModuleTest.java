package wioe5.runtime;

public final class DeterministicPeripheralNativeModuleTest {
    private DeterministicPeripheralNativeModuleTest() {
    }

    public static void main(String[] args) {
        testGpioDeterministicBehavior();
        testI2cDeterministicBehavior();
        testUartDeterministicBehavior();
        testDispatchIntegration();
    }

    private static void testGpioDeterministicBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(32, 32);

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(1, 1), "D0 output mode");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(2, 0), "D1 input mode");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.configureGpioLoopback(1, 2), "GPIO loopback map");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.digitalWrite(1, 1), "GPIO write high");
        assertEquals(1, module.digitalRead(2), "loopback read high");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.digitalWrite(1, 0), "GPIO write low");
        assertEquals(0, module.digitalRead(2), "loopback read low");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(3, 4), "D2 analog mode");
        assertEquals(0, module.analogRead(3), "default analog value");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.setAnalogInputForTest(3, 4095), "set analog max");
        assertEquals(4095, module.analogRead(3), "analog max value");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.digitalRead(3), "analog pin digital read rejected");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.setDigitalInputForTest(2, 1), "set digital input");
        assertEquals(1, module.digitalRead(2), "digital input value");

        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_OUT_OF_RANGE, module.pinMode(9, 1), "invalid pin");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_OUT_OF_RANGE, module.pinMode(1, 9), "invalid mode");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.digitalWrite(2, 1), "input pin write rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_DIGITAL_VALUE_OUT_OF_RANGE, module.digitalWrite(1, 3), "invalid digital value");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_ANALOG_VALUE_OUT_OF_RANGE, module.setAnalogInputForTest(3, 4096), "analog out of range");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.setDigitalInputForTest(1, 1), "cannot force output pin input");
    }

    private static void testI2cDeterministicBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(16, 32);
        byte[] readBuffer = new byte[4];
        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_NOT_INITIALIZED, module.readI2c(0x44, readBuffer, 1), "read before begin rejected");

        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_SPEED_UNSUPPORTED, module.beginI2c(200), "unsupported speed");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.beginI2c(400), "begin 400kHz");
        assertEquals(400, module.i2cSpeedKhz(), "active i2c speed");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_ALREADY_INITIALIZED, module.beginI2c(100), "double begin rejected");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.registerI2cDeviceForTest(0x44, new byte[]{10, 11, 12, 13, 14, 15}),
                "register i2c device");

        byte[] writeRegisterOnly = new byte[]{2};
        assertEquals(1, module.writeI2c(0x44, writeRegisterOnly, 1), "set register pointer");
        assertEquals(2, module.readI2c(0x44, readBuffer, 2), "read length");
        assertEquals(12, readBuffer[0] & 0xFF, "read register value 2");
        assertEquals(13, readBuffer[1] & 0xFF, "read register value 3");

        byte[] writePayload = new byte[]{4, 90, 91};
        assertEquals(3, module.writeI2c(0x44, writePayload, 3), "register write");
        byte[] tx = new byte[]{4};
        byte[] rx = new byte[2];
        assertEquals(2, module.writeReadI2c(0x44, tx, 1, rx, 2), "writeRead");
        assertEquals(90, rx[0] & 0xFF, "writeRead byte 0");
        assertEquals(91, rx[1] & 0xFF, "writeRead byte 1");

        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_ADDRESS_OUT_OF_RANGE,
                module.writeI2c(0x80, tx, 1), "invalid address");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_DEVICE_NOT_FOUND,
                module.writeI2c(0x45, tx, 1), "missing device");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_LENGTH_OUT_OF_RANGE,
                module.readI2c(0x44, new byte[1], 2), "length exceeds buffer");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.endI2c(), "end i2c");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_NOT_INITIALIZED, module.endI2c(), "double end rejected");
    }

    private static void testUartDeterministicBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(16, 8);

        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_NOT_INITIALIZED, module.availableUart(1), "available before begin rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_BAUD_OUT_OF_RANGE, module.beginUart(1, 100), "invalid baud");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.beginUart(1, 9600), "begin uart1");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.beginUart(2, 9600), "begin uart2");
        assertEquals(9600, module.uartBaud(1), "uart1 baud");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_ALREADY_INITIALIZED, module.beginUart(1, 115200), "double begin");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.configureUartLoopback(1, 2), "uart loopback");
        byte[] payload = new byte[]{1, 2, 3};
        assertEquals(3, module.writeUart(1, payload, payload.length), "uart write");
        assertEquals(3, module.availableUart(2), "uart2 available");
        assertEquals(1, module.readUart(2), "uart2 byte 1");
        assertEquals(2, module.readUart(2), "uart2 byte 2");
        assertEquals(3, module.readUart(2), "uart2 byte 3");
        assertEquals(DeterministicPeripheralNativeModule.UART_READ_EMPTY, module.readUart(2), "uart2 empty");

        assertEquals(4, module.printUart(1, "test"), "print len");
        assertEquals(4, module.availableUart(2), "uart2 available after print");
        assertEquals('t', module.readUart(2), "uart2 print byte 1");
        assertEquals('e', module.readUart(2), "uart2 print byte 2");
        assertEquals('s', module.readUart(2), "uart2 print byte 3");
        assertEquals('t', module.readUart(2), "uart2 print byte 4");
        assertEquals(4, module.printlnUart(1, "ok"), "println len");
        assertEquals(4, module.availableUart(2), "uart2 available after println");
        assertEquals('o', module.readUart(2), "uart2 println byte 1");
        assertEquals('k', module.readUart(2), "uart2 println byte 2");
        assertEquals('\r', module.readUart(2), "uart2 println CR");
        assertEquals('\n', module.readUart(2), "uart2 println LF");
        assertEquals(DeterministicPeripheralNativeModule.UART_READ_EMPTY, module.readUart(2), "uart2 empty after println drain");
        assertEquals(3, module.injectUartRxForTest(1, new byte[]{7, 8, 9}), "inject uart rx");
        assertEquals(3, module.availableUart(1), "uart1 available after inject");
        assertEquals(7, module.readUart(1), "uart1 injected byte 1");
        assertEquals(8, module.readUart(1), "uart1 injected byte 2");
        assertEquals(9, module.readUart(1), "uart1 injected byte 3");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_TX_OVERFLOW,
                module.writeUart(1, new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9}, 9), "overflow detection");
    }

    private static void testDispatchIntegration() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(16, 32);
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(3, 3, 3, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(3), "dispatch compatibility");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                        VersionedNativeDispatchTable.METHOD_HASH_GPIO_PIN_MODE,
                        new int[]{1, 1}),
                "dispatch gpio pinMode");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                        VersionedNativeDispatchTable.METHOD_HASH_GPIO_PIN_MODE,
                        new int[]{2, 0}),
                "dispatch gpio input pinMode");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.configureGpioLoopback(1, 2), "dispatch loopback setup");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                        VersionedNativeDispatchTable.METHOD_HASH_GPIO_DIGITAL_WRITE,
                        new int[]{1, 1}),
                "dispatch gpio write");
        assertEquals(1,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                        VersionedNativeDispatchTable.METHOD_HASH_GPIO_DIGITAL_READ,
                        new int[]{2}),
                "dispatch gpio read");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_BEGIN,
                        new int[]{100}),
                "dispatch i2c begin");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.registerI2cDeviceForTest(0x40, new byte[]{20, 21, 22, 23}),
                "dispatch i2c register");
        int txHandle = module.registerDispatchByteBuffer(new byte[]{1});
        int rxHandle = module.registerDispatchByteBuffer(new byte[2]);
        assertTrue(txHandle > 0, "dispatch tx handle");
        assertTrue(rxHandle > 0, "dispatch rx handle");
        assertEquals(1,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_WRITE,
                        new int[]{0x40, txHandle, 1}),
                "dispatch i2c write");
        assertEquals(2,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_READ,
                        new int[]{0x40, rxHandle, 2}),
                "dispatch i2c read");
        byte[] copiedRead = module.copyDispatchByteBuffer(rxHandle);
        assertEquals(21, copiedRead[0] & 0xFF, "dispatch i2c rx[0]");
        assertEquals(22, copiedRead[1] & 0xFF, "dispatch i2c rx[1]");

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_BEGIN,
                        new int[]{1, 9600}),
                "dispatch uart1 begin");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_BEGIN,
                        new int[]{2, 9600}),
                "dispatch uart2 begin");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.configureUartLoopback(1, 2), "dispatch uart loopback");
        int textHandle = module.registerDispatchString("hi");
        assertTrue(textHandle > 0, "dispatch text handle");
        assertEquals(2,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_PRINT,
                        new int[]{1, textHandle}),
                "dispatch uart print");
        assertEquals(2,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_AVAILABLE,
                        new int[]{2}),
                "dispatch uart available");
        assertEquals('h',
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_READ,
                        new int[]{2}),
                "dispatch uart read h");
        assertEquals('i',
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_READ,
                        new int[]{2}),
                "dispatch uart read i");

        assertEquals(DeterministicPeripheralNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_READ,
                        new int[]{0x40, 999, 1}),
                "dispatch invalid buffer handle");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_STRING_HANDLE_INVALID,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_PRINT,
                        new int[]{1, 999}),
                "dispatch invalid string handle");

        assertEquals(VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                        new int[0]),
                "non-implemented power handlers stay unresolved");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label + " expected true");
        }
    }
}
