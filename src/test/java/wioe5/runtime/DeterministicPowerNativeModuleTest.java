package wioe5.runtime;

public final class DeterministicPowerNativeModuleTest {
    private DeterministicPowerNativeModuleTest() {
    }

    public static void main(String[] args) {
        testDeterministicPowerBehavior();
        testPowerDispatchIntegration();
    }

    private static void testDeterministicPowerBehavior() {
        DeterministicPowerNativeModule power = new DeterministicPowerNativeModule(
                3600,
                DeterministicPowerNativeModule.RESET_REASON_POWER_ON,
                1000);

        assertEquals(3600, power.readBatteryMV(), "initial battery");
        assertEquals(1000, power.millis(), "initial millis");
        assertEquals(DeterministicPowerNativeModule.RESET_REASON_POWER_ON, power.getResetReason(), "initial reset reason");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.kickWatchdog(), "watchdog kick status");
        assertEquals(1, power.watchdogKickCount(), "watchdog kick count");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.delayMicros(1500), "delay status");
        assertEquals(1001, power.millis(), "millis advanced by delay");
        assertEquals(500, power.pendingDelayMicrosRemainder(), "delay remainder");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.deepSleep(250), "deep sleep status");
        assertEquals(1251, power.millis(), "millis advanced by deep sleep");
        assertEquals(1, power.deepSleepCount(), "deep sleep count");
        assertEquals(250, power.lastDeepSleepDurationMs(), "last deep sleep duration");
        assertTrue(power.lastWakeClockRestored(), "wake clock restore");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.setBatteryMV(3300), "set battery");
        assertEquals(3300, power.readBatteryMV(), "updated battery");
        assertEquals(DeterministicPowerNativeModule.ERROR_BATTERY_OUT_OF_RANGE, power.setBatteryMV(1500), "invalid battery");
        assertEquals(3300, power.readBatteryMV(), "battery unchanged after invalid set");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.setResetReason(DeterministicPowerNativeModule.RESET_REASON_WATCHDOG), "set reset reason");
        assertEquals(DeterministicPowerNativeModule.RESET_REASON_WATCHDOG, power.getResetReason(), "updated reset reason");

        assertEquals(DeterministicPowerNativeModule.ERROR_SLEEP_DURATION_OUT_OF_RANGE, power.deepSleep(-1), "negative deep sleep rejected");
        assertEquals(DeterministicPowerNativeModule.ERROR_DELAY_MICROS_OUT_OF_RANGE, power.delayMicros(-1), "negative delay rejected");
    }

    private static void testPowerDispatchIntegration() {
        DeterministicPowerNativeModule power = new DeterministicPowerNativeModule(
                3400,
                DeterministicPowerNativeModule.RESET_REASON_SOFTWARE,
                Integer.MAX_VALUE - 2);
        VersionedNativeDispatchTable.NativeHandler[] handlers = power.createDefaultDispatchHandlers();
        VersionedNativeDispatchTable table = VersionedNativeDispatchTable.createDefault(2, 2, 2, handlers);
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(2), "compatibility");

        int millisBefore = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]);
        assertEquals(Integer.MAX_VALUE - 2, millisBefore, "dispatch millis before");

        int sleepResult = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DEEP_SLEEP,
                new int[]{5});
        assertEquals(DeterministicPowerNativeModule.STATUS_OK, sleepResult, "dispatch deepSleep status");

        int millisAfter = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]);
        assertEquals(Integer.MIN_VALUE + 2, millisAfter, "dispatch millis wrap");

        int battery = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_READ_BATTERY_MV,
                new int[0]);
        assertEquals(3400, battery, "dispatch battery");

        int invalidArgCount = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_KICK_WATCHDOG,
                new int[]{1});
        assertEquals(DeterministicPowerNativeModule.ERROR_INVALID_ARGUMENT, invalidArgCount, "invalid arg count");

        int delayStatus = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DELAY_MICROS,
                new int[]{2500});
        assertEquals(DeterministicPowerNativeModule.STATUS_OK, delayStatus, "dispatch delay status");

        int notImplemented = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_GPIO,
                VersionedNativeDispatchTable.METHOD_HASH_GPIO_DIGITAL_READ,
                new int[0]);
        assertEquals(VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND, notImplemented, "non-power symbols not implemented in power handlers");
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
