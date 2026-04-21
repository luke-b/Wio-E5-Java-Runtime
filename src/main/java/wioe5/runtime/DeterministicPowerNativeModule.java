package wioe5.runtime;

/**
 * Deterministic host-side model for {@code wioe5.system.Power} native behavior.
 */
public final class DeterministicPowerNativeModule {
    public static final int STATUS_OK = 0;
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_OUT_OF_RANGE = -2;

    public static final int RESET_REASON_POWER_ON = 1;
    public static final int RESET_REASON_SOFTWARE = 2;
    public static final int RESET_REASON_WATCHDOG = 3;
    public static final int RESET_REASON_BROWN_OUT = 4;

    private static final int POWER_NATIVE_INDEX_BASE = 0;
    private static final int POWER_NATIVE_HANDLER_COUNT = 6;
    private static final int POWER_NATIVE_INDEX_DEEP_SLEEP = 0;
    private static final int POWER_NATIVE_INDEX_READ_BATTERY_MV = 1;
    private static final int POWER_NATIVE_INDEX_GET_RESET_REASON = 2;
    private static final int POWER_NATIVE_INDEX_KICK_WATCHDOG = 3;
    private static final int POWER_NATIVE_INDEX_MILLIS = 4;
    private static final int POWER_NATIVE_INDEX_DELAY_MICROS = 5;

    private final int maxDeepSleepMillis;
    private final int maxDelayMicros;
    private final int minBatteryMillivolts;
    private final int maxBatteryMillivolts;

    private int batteryMillivolts;
    private int resetReason;
    private int currentMillis;
    private int microsCarry;
    private int watchdogKickCount;
    private int deepSleepCallCount;
    private int wakeClockRestoreCount;

    public DeterministicPowerNativeModule(
            int maxDeepSleepMillis,
            int maxDelayMicros,
            int minBatteryMillivolts,
            int maxBatteryMillivolts,
            int initialBatteryMillivolts,
            int initialResetReason) {
        if (maxDeepSleepMillis < 0) {
            throw new IllegalArgumentException("maxDeepSleepMillis must be >= 0");
        }
        if (maxDelayMicros < 0) {
            throw new IllegalArgumentException("maxDelayMicros must be >= 0");
        }
        if (minBatteryMillivolts < 0 || maxBatteryMillivolts < minBatteryMillivolts) {
            throw new IllegalArgumentException("invalid battery millivolt range");
        }
        this.maxDeepSleepMillis = maxDeepSleepMillis;
        this.maxDelayMicros = maxDelayMicros;
        this.minBatteryMillivolts = minBatteryMillivolts;
        this.maxBatteryMillivolts = maxBatteryMillivolts;
        this.batteryMillivolts = initialBatteryMillivolts;
        if (!isValidBatteryMillivolts(initialBatteryMillivolts)) {
            throw new IllegalArgumentException("initialBatteryMillivolts out of range");
        }
        this.resetReason = initialResetReason;
    }

    public int deepSleep(int milliseconds) {
        if (milliseconds < 0 || milliseconds > maxDeepSleepMillis) {
            return ERROR_OUT_OF_RANGE;
        }
        deepSleepCallCount++;
        wakeClockRestoreCount++;
        return advanceMillis(milliseconds);
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

    public int delayMicros(int microseconds) {
        if (microseconds < 0 || microseconds > maxDelayMicros) {
            return ERROR_OUT_OF_RANGE;
        }
        int totalMicros = microsCarry + microseconds;
        int millisDelta = totalMicros / 1000;
        microsCarry = totalMicros % 1000;
        return advanceMillis(millisDelta);
    }

    public int setBatteryMillivolts(int millivolts) {
        if (!isValidBatteryMillivolts(millivolts)) {
            return ERROR_OUT_OF_RANGE;
        }
        batteryMillivolts = millivolts;
        return STATUS_OK;
    }

    public int setResetReason(int reason) {
        resetReason = reason;
        return STATUS_OK;
    }

    public int setCurrentMillis(int millis) {
        if (millis < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        currentMillis = millis;
        microsCarry = 0;
        return STATUS_OK;
    }

    public int watchdogKickCount() {
        return watchdogKickCount;
    }

    public int deepSleepCallCount() {
        return deepSleepCallCount;
    }

    public int wakeClockRestoreCount() {
        return wakeClockRestoreCount;
    }

    public int microsCarry() {
        return microsCarry;
    }

    public void installInto(VersionedNativeDispatchTable.NativeHandler[] handlers) {
        if (handlers == null) {
            throw new IllegalArgumentException("handlers must not be null");
        }
        if (handlers.length < POWER_NATIVE_INDEX_BASE + POWER_NATIVE_HANDLER_COUNT) {
            throw new IllegalArgumentException("handlers must include power native indices");
        }

        handlers[POWER_NATIVE_INDEX_DEEP_SLEEP] = args -> {
            if (args.length < 1) {
                return ERROR_INVALID_ARGUMENT;
            }
            return deepSleep(args[0]);
        };
        handlers[POWER_NATIVE_INDEX_READ_BATTERY_MV] = args -> readBatteryMV();
        handlers[POWER_NATIVE_INDEX_GET_RESET_REASON] = args -> getResetReason();
        handlers[POWER_NATIVE_INDEX_KICK_WATCHDOG] = args -> kickWatchdog();
        handlers[POWER_NATIVE_INDEX_MILLIS] = args -> millis();
        handlers[POWER_NATIVE_INDEX_DELAY_MICROS] = args -> {
            if (args.length < 1) {
                return ERROR_INVALID_ARGUMENT;
            }
            return delayMicros(args[0]);
        };
    }

    private int advanceMillis(int deltaMillis) {
        if (deltaMillis < 0) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (deltaMillis > Integer.MAX_VALUE - currentMillis) {
            return ERROR_OUT_OF_RANGE;
        }
        currentMillis += deltaMillis;
        return STATUS_OK;
    }

    private boolean isValidBatteryMillivolts(int millivolts) {
        return millivolts >= minBatteryMillivolts && millivolts <= maxBatteryMillivolts;
    }
}
