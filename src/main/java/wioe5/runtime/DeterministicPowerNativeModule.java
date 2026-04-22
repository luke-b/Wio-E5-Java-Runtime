package wioe5.runtime;

/**
 * Deterministic host-side implementation of {@code wioe5.system.Power} native behavior.
 */
public final class DeterministicPowerNativeModule {
    public static final int STATUS_OK = 0;

    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_BATTERY_OUT_OF_RANGE = -2;
    public static final int ERROR_SLEEP_DURATION_OUT_OF_RANGE = -3;
    public static final int ERROR_DELAY_MICROS_OUT_OF_RANGE = -4;

    public static final int RESET_REASON_POWER_ON = 0;
    public static final int RESET_REASON_WATCHDOG = 1;
    public static final int RESET_REASON_SOFTWARE = 2;
    public static final int RESET_REASON_BROWNOUT = 3;

    private static final int MIN_BATTERY_MV = 1800;
    private static final int MAX_BATTERY_MV = 5000;

    private int batteryMillivolts;
    private int resetReason;
    private int currentMillis;
    private int subMillisecondMicros;
    private int deepSleepCount;
    private int watchdogKickCount;
    private int lastDeepSleepDurationMs;
    private boolean lastWakeClockRestored;

    public DeterministicPowerNativeModule(int initialBatteryMillivolts, int initialResetReason, int initialMillis) {
        if (!isBatteryMillivoltsInRange(initialBatteryMillivolts)) {
            throw new IllegalArgumentException("initialBatteryMillivolts out of range");
        }
        this.batteryMillivolts = initialBatteryMillivolts;
        this.resetReason = initialResetReason;
        this.currentMillis = initialMillis;
    }

    public int deepSleep(int ms) {
        if (ms < 0) {
            return ERROR_SLEEP_DURATION_OUT_OF_RANGE;
        }
        lastWakeClockRestored = false;
        lastDeepSleepDurationMs = ms;
        deepSleepCount++;
        currentMillis += ms;
        lastWakeClockRestored = true;
        return STATUS_OK;
    }

    public int readBatteryMV() {
        return batteryMillivolts;
    }

    public int getResetReason() {
        return resetReason;
    }

    public int kickWatchdog() {
        watchdogKickCount++;
        return STATUS_OK;
    }

    public int millis() {
        return currentMillis;
    }

    public int delayMicros(int us) {
        if (us < 0) {
            return ERROR_DELAY_MICROS_OUT_OF_RANGE;
        }
        long totalMicros = (long) subMillisecondMicros + us;
        int millisToAdvance = (int) (totalMicros / 1000L);
        subMillisecondMicros = (int) (totalMicros % 1000L);
        currentMillis += millisToAdvance;
        return STATUS_OK;
    }

    public int setBatteryMV(int millivolts) {
        if (!isBatteryMillivoltsInRange(millivolts)) {
            return ERROR_BATTERY_OUT_OF_RANGE;
        }
        batteryMillivolts = millivolts;
        return STATUS_OK;
    }

    public int setResetReason(int newResetReason) {
        resetReason = newResetReason;
        return STATUS_OK;
    }

    public int deepSleepCount() {
        return deepSleepCount;
    }

    public int watchdogKickCount() {
        return watchdogKickCount;
    }

    public int lastDeepSleepDurationMs() {
        return lastDeepSleepDurationMs;
    }

    public int pendingDelayMicrosRemainder() {
        return subMillisecondMicros;
    }

    public boolean lastWakeClockRestored() {
        return lastWakeClockRestored;
    }

    public VersionedNativeDispatchTable.NativeHandler[] createDefaultDispatchHandlers() {
        VersionedNativeDispatchTable.NativeHandler[] handlers =
                new VersionedNativeDispatchTable.NativeHandler[VersionedNativeDispatchTable.defaultBindingCount()];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = args -> VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND;
        }

        handlers[0] = args -> readOneArg(args, true) ? deepSleep(args[0]) : ERROR_INVALID_ARGUMENT;
        handlers[1] = args -> readZeroArgs(args) ? readBatteryMV() : ERROR_INVALID_ARGUMENT;
        handlers[2] = args -> readZeroArgs(args) ? getResetReason() : ERROR_INVALID_ARGUMENT;
        handlers[3] = args -> readZeroArgs(args) ? kickWatchdog() : ERROR_INVALID_ARGUMENT;
        handlers[4] = args -> readZeroArgs(args) ? millis() : ERROR_INVALID_ARGUMENT;
        handlers[5] = args -> readOneArg(args, false) ? delayMicros(args[0]) : ERROR_INVALID_ARGUMENT;
        return handlers;
    }

    private static boolean isBatteryMillivoltsInRange(int value) {
        return value >= MIN_BATTERY_MV && value <= MAX_BATTERY_MV;
    }

    private static boolean readZeroArgs(int[] args) {
        return args != null && args.length == 0;
    }

    private static boolean readOneArg(int[] args, boolean allowNegative) {
        return args != null
                && args.length == 1
                && (allowNegative || args[0] >= 0);
    }
}
