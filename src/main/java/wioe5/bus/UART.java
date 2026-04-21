package wioe5.bus;

public final class UART {
    public static final int UART1 = 1;
    public static final int UART2 = 2;

    private UART() {
    }

    public static native int begin(int uart, int baud);

    public static native int available(int uart);

    public static native int read(int uart);

    public static native int write(int uart, byte[] data, int len);

    public static native int print(int uart, String s);

    public static native int println(int uart, String s);
}
