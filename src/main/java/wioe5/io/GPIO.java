package wioe5.io;

public final class GPIO {
    public static final int LED = 0;
    public static final int D0 = 1;
    public static final int D1 = 2;
    public static final int D2 = 3;
    public static final int D3 = 4;
    public static final int D4 = 5;
    public static final int D5 = 6;

    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int INPUT_PULLUP = 2;
    public static final int INPUT_PULLDOWN = 3;
    public static final int ANALOG = 4;

    public static final int LOW = 0;
    public static final int HIGH = 1;

    private GPIO() {
    }

    public static native int pinMode(int pin, int mode);

    public static native int digitalWrite(int pin, int value);

    public static native int digitalRead(int pin);

    public static native int analogRead(int pin);
}
