package wioe5.runtime;

public final class DeterministicPeripheralNativeModuleTest {
    private DeterministicPeripheralNativeModuleTest() {
    }

    public static void main(String[] args) {
        testGpioDeterministicBehavior();
        testI2cDeterministicBehavior();
        testUartDeterministicBehavior();
        testDispatchIntegration();
        testGpioHardenedBehavior();
        testI2cBoundaryBehavior();
        testUartBoundaryBehavior();
        testDispatchHardenedBehavior();
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

    private static void testGpioHardenedBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(16, 16);

        // LED (pin 0): OUTPUT mode read-back returns the driven level
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(0, 1), "LED output mode");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.digitalWrite(0, 1), "LED write high");
        assertEquals(1, module.digitalRead(0), "LED output read-back high");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.digitalWrite(0, 0), "LED write low");
        assertEquals(0, module.digitalRead(0), "LED output read-back low");

        // INPUT_PULLUP: digitalRead succeeds and returns driven level; digitalWrite rejected
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(1, 2), "D0 INPUT_PULLUP");
        assertEquals(0, module.digitalRead(1), "INPUT_PULLUP default low");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.setDigitalInputForTest(1, 1), "INPUT_PULLUP set high");
        assertEquals(1, module.digitalRead(1), "INPUT_PULLUP driven high");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.digitalWrite(1, 1), "INPUT_PULLUP write rejected");

        // INPUT_PULLDOWN: digitalRead succeeds; digitalWrite rejected
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(2, 3), "D1 INPUT_PULLDOWN");
        assertEquals(0, module.digitalRead(2), "INPUT_PULLDOWN default low");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.setDigitalInputForTest(2, 0), "INPUT_PULLDOWN set low");
        assertEquals(0, module.digitalRead(2), "INPUT_PULLDOWN driven low");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.digitalWrite(2, 0), "INPUT_PULLDOWN write rejected");

        // analogRead rejected on all non-ANALOG modes
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.analogRead(0), "analogRead on OUTPUT rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.analogRead(1), "analogRead on INPUT_PULLUP rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.analogRead(2), "analogRead on INPUT_PULLDOWN rejected");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(3, 0), "D2 INPUT mode");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_MODE_MISMATCH, module.analogRead(3), "analogRead on INPUT rejected");

        // All 7 valid pin indices (0..6) accept pinMode; pin 7 is out of range
        for (int pin = 0; pin <= 6; pin++) {
            assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.pinMode(pin, 0), "pin " + pin + " valid");
        }
        assertEquals(DeterministicPeripheralNativeModule.ERROR_PIN_OUT_OF_RANGE, module.pinMode(7, 0), "pin 7 out of range");
    }

    private static void testI2cBoundaryBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(8, 16);

        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module.beginI2c(100), "I2C begin 100kHz");
        assertEquals(100, module.i2cSpeedKhz(), "I2C speed 100kHz");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.registerI2cDeviceForTest(0x20, new byte[]{10, 11, 12, 13, 14, 15, 16, 17}),
                "register i2c device at 0x20");

        // write len=0: no-op, register pointer unchanged, returns STATUS_OK
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.writeI2c(0x20, new byte[0], 0), "write len=0 no-op");

        // write len=1: sets register pointer to data[0], writes no data, returns 1
        assertEquals(1, module.writeI2c(0x20, new byte[]{3}, 1), "write len=1 sets register pointer");
        byte[] readBuf = new byte[2];
        assertEquals(2, module.readI2c(0x20, readBuf, 2), "read after len=1 write pointer set");
        assertEquals(13, readBuf[0] & 0xFF, "register 3 value after pointer set");
        assertEquals(14, readBuf[1] & 0xFF, "register 4 value after pointer set");

        // read len=0: no-op, pointer unchanged, returns 0
        assertEquals(0, module.readI2c(0x20, new byte[0], 0), "read len=0 no-op");

        // writeRead failure propagation: write error (device not found) propagates; no read attempted
        assertEquals(DeterministicPeripheralNativeModule.ERROR_I2C_DEVICE_NOT_FOUND,
                module.writeReadI2c(0x50, new byte[]{0}, 1, new byte[2], 2),
                "writeRead propagates write failure");

        // Register wrap-around: write 3 data bytes starting near end of 8-byte device memory
        // wrapData: reg=6, data=[90,91,92]; writes land at mem[6]=90, mem[7]=91, mem[0]=92 (wrap)
        assertEquals(4, module.writeI2c(0x20, new byte[]{6, 90, 91, 92}, 4), "register wrap-around write");
        assertEquals(1, module.writeI2c(0x20, new byte[]{0}, 1), "set register pointer to 0 after wrap");
        byte[] wrapReadBuf = new byte[3];
        assertEquals(3, module.readI2c(0x20, wrapReadBuf, 3), "register wrap-around read");
        assertEquals(92, wrapReadBuf[0] & 0xFF, "wrap: register 0 = 92 (wrapped)");
        assertEquals(11, wrapReadBuf[1] & 0xFF, "wrap: register 1 = 11 (original)");
        assertEquals(12, wrapReadBuf[2] & 0xFF, "wrap: register 2 = 12 (original)");
    }

    private static void testUartBoundaryBehavior() {
        // Baud boundary: minimum valid (1200) and maximum valid (921600)
        DeterministicPeripheralNativeModule module1 = new DeterministicPeripheralNativeModule(16, 32);
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module1.beginUart(1, 1200), "baud minimum valid");
        assertEquals(1200, module1.uartBaud(1), "baud 1200");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module1.beginUart(2, 921600), "baud maximum valid");
        assertEquals(921600, module1.uartBaud(2), "baud 921600");

        // Baud boundary: just outside valid extremes
        DeterministicPeripheralNativeModule module2 = new DeterministicPeripheralNativeModule(16, 32);
        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_BAUD_OUT_OF_RANGE,
                module2.beginUart(1, 1199), "baud below minimum rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_UART_BAUD_OUT_OF_RANGE,
                module2.beginUart(1, 921601), "baud above maximum rejected");

        // printUart / printlnUart with non-ASCII chars rejected (code point > 0x7F)
        DeterministicPeripheralNativeModule module3 = new DeterministicPeripheralNativeModule(16, 32);
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module3.beginUart(1, 9600), "uart1 begin");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                module3.printUart(1, "\u00e9"), "non-ascii print rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                module3.printlnUart(1, "\u00e9"), "non-ascii println rejected");

        // printUart / printlnUart with null string rejected
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                module3.printUart(1, null), "null print rejected");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                module3.printlnUart(1, null), "null println rejected");

        // writeUart with len=0 is a no-op returning 0; loopback peer receives nothing
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK, module3.beginUart(2, 9600), "uart2 begin");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module3.configureUartLoopback(1, 2), "uart loopback for len=0 test");
        assertEquals(0, module3.writeUart(1, new byte[0], 0), "write len=0 no-op");
        assertEquals(0, module3.availableUart(2), "uart2 available 0 after write len=0");
    }

    private static void testDispatchHardenedBehavior() {
        DeterministicPeripheralNativeModule module = new DeterministicPeripheralNativeModule(16, 32);
        VersionedNativeDispatchTable table =
                VersionedNativeDispatchTable.createDefault(3, 3, 3, module.createDefaultDispatchHandlers());
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(3), "dispatch compatibility");

        // UART_PRINTLN dispatch: "Hello\r\n" = 5 chars + 2 = 7 bytes transmitted to loopback peer
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_BEGIN,
                        new int[]{1, 9600}),
                "dispatch uart1 begin for println test");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_BEGIN,
                        new int[]{2, 9600}),
                "dispatch uart2 begin for println test");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.configureUartLoopback(1, 2), "uart loopback for println dispatch");
        int helloHandle = module.registerDispatchString("Hello");
        assertTrue(helloHandle > 0, "string handle for Hello");
        assertEquals(7,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_PRINTLN,
                        new int[]{1, helloHandle}),
                "dispatch uart println Hello returns 5+2=7");
        assertEquals(7,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_AVAILABLE,
                        new int[]{2}),
                "dispatch uart2 available 7 after println");

        // I2C writeRead: valid tx handle but invalid rx handle returns ERROR_BUFFER_HANDLE_INVALID
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_BEGIN,
                        new int[]{100}),
                "dispatch i2c begin for writeRead test");
        assertEquals(DeterministicPeripheralNativeModule.STATUS_OK,
                module.registerI2cDeviceForTest(0x30, new byte[]{1, 2, 3, 4}),
                "register i2c device 0x30");
        int txHandle = module.registerDispatchByteBuffer(new byte[]{0});
        assertTrue(txHandle > 0, "valid tx handle");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_WRITE_READ,
                        new int[]{0x30, txHandle, 1, 999, 2}),
                "dispatch i2c writeRead valid tx invalid rx");

        // I2C writeRead: invalid tx handle but valid rx handle returns ERROR_BUFFER_HANDLE_INVALID
        int rxHandle = module.registerDispatchByteBuffer(new byte[2]);
        assertTrue(rxHandle > 0, "valid rx handle");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_BUFFER_HANDLE_INVALID,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_WRITE_READ,
                        new int[]{0x30, 999, 1, rxHandle, 2}),
                "dispatch i2c writeRead invalid tx valid rx");

        // Wrong argument count is rejected for GPIO, I2C writeRead, and UART write
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                        VersionedNativeDispatchTable.METHOD_HASH_GPIO_PIN_MODE,
                        new int[]{1}),
                "dispatch gpio pinMode wrong arg count (1 instead of 2)");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_I2C,
                        VersionedNativeDispatchTable.METHOD_HASH_I2C_WRITE_READ,
                        new int[]{0x30, txHandle, 1}),
                "dispatch i2c writeRead wrong arg count (3 instead of 5)");
        assertEquals(DeterministicPeripheralNativeModule.ERROR_INVALID_ARGUMENT,
                table.dispatch(VersionedNativeDispatchTable.CLASS_HASH_UART,
                        VersionedNativeDispatchTable.METHOD_HASH_UART_WRITE,
                        new int[]{1, txHandle}),
                "dispatch uart write wrong arg count (2 instead of 3)");
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
