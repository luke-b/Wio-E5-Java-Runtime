package wioe5.system;

public final class Power {
    private Power() {
    }

    public static native int deepSleep(int ms);

    public static native int readBatteryMV();

    public static native int getResetReason();

    public static native int kickWatchdog();

    public static native int millis();

    public static native void delayMicros(int us);
}
