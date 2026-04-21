package wioe5.bus;

public final class I2C {
    private I2C() {
    }

    public static native int begin(int speedKhz);

    public static native int write(int address, byte[] data, int len);

    public static native int read(int address, byte[] buffer, int len);

    public static native int writeRead(int address, byte[] tx, int txLen, byte[] rx, int rxLen);

    public static native int end();
}
