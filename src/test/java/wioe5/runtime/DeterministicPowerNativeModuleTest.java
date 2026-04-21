package wioe5.runtime;

public final class DeterministicPowerNativeModuleTest {
    private DeterministicPowerNativeModuleTest() {
    }

    public static void main(String[] args) {
        testDirectPowerBehavior();
        testDirectNegativePaths();
        testDispatchIntegration();
    }

    private static void testDirectPowerBehavior() {
        DeterministicPowerNativeModule power = new DeterministicPowerNativeModule(
                60_000,
                2_000_000,
                1800,
                5000,
                3650,
                DeterministicPowerNativeModule.RESET_REASON_POWER_ON);

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.setCurrentMillis(100), "set current millis");
        assertEquals(100, power.millis(), "initial millis");
        assertEquals(3650, power.readBatteryMV(), "initial battery");
        assertEquals(DeterministicPowerNativeModule.RESET_REASON_POWER_ON, power.getResetReason(), "initial reset reason");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.deepSleep(250), "deepSleep");
        assertEquals(350, power.millis(), "millis after deepSleep");
        assertEquals(1, power.deepSleepCallCount(), "deepSleep count");
        assertEquals(1, power.wakeClockRestoreCount(), "wake restore count");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.delayMicros(1500), "delayMicros");
        assertEquals(351, power.millis(), "millis after delayMicros");
        assertEquals(500, power.microsCarry(), "micros carry");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.delayMicros(500), "delayMicros flush carry");
        assertEquals(352, power.millis(), "millis after carry flush");
        assertEquals(0, power.microsCarry(), "carry reset");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.kickWatchdog(), "kickWatchdog");
        assertEquals(1, power.watchdogKickCount(), "watchdog count");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.setBatteryMillivolts(3300), "set battery");
        assertEquals(3300, power.readBatteryMV(), "updated battery");
        assertEquals(DeterministicPowerNativeModule.STATUS_OK, power.setResetReason(DeterministicPowerNativeModule.RESET_REASON_SOFTWARE), "set reset reason");
        assertEquals(DeterministicPowerNativeModule.RESET_REASON_SOFTWARE, power.getResetReason(), "updated reset reason");
    }

    private static void testDirectNegativePaths() {
        DeterministicPowerNativeModule power = new DeterministicPowerNativeModule(
                120,
                5000,
                2000,
                4200,
                3000,
                DeterministicPowerNativeModule.RESET_REASON_WATCHDOG);

        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.deepSleep(-1), "negative deepSleep");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.deepSleep(121), "too large deepSleep");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.delayMicros(-1), "negative delayMicros");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.delayMicros(5001), "too large delayMicros");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.setBatteryMillivolts(1999), "battery below range");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, power.setBatteryMillivolts(4201), "battery above range");
        assertEquals(DeterministicPowerNativeModule.ERROR_INVALID_ARGUMENT, power.setCurrentMillis(-1), "negative millis");
    }

    private static void testDispatchIntegration() {
        VersionedNativeDispatchTable.NativeHandler[] handlers = fixedHandlers(34);
        DeterministicPowerNativeModule power = new DeterministicPowerNativeModule(
                10_000,
                1_000_000,
                1800,
                5000,
                3600,
                DeterministicPowerNativeModule.RESET_REASON_BROWN_OUT);
        power.installInto(handlers);

        VersionedNativeDispatchTable table = VersionedNativeDispatchTable.createDefault(1, 1, 1, handlers);
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(1), "compatibility");

        assertEquals(3600, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_READ_BATTERY_MV,
                new int[0]), "dispatch readBatteryMV");

        assertEquals(DeterministicPowerNativeModule.RESET_REASON_BROWN_OUT, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_GET_RESET_REASON,
                new int[0]), "dispatch getResetReason");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DEEP_SLEEP,
                new int[]{400}), "dispatch deepSleep");
        assertEquals(400, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]), "dispatch millis after sleep");
        assertEquals(1, power.wakeClockRestoreCount(), "wake restore count after dispatch");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DELAY_MICROS,
                new int[]{2500}), "dispatch delayMicros");
        assertEquals(402, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]), "dispatch millis after delay");
        assertEquals(500, power.microsCarry(), "carry after delay");

        assertEquals(DeterministicPowerNativeModule.STATUS_OK, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_KICK_WATCHDOG,
                new int[0]), "dispatch kickWatchdog");
        assertEquals(1, power.watchdogKickCount(), "watchdog count after dispatch");

        assertEquals(DeterministicPowerNativeModule.ERROR_INVALID_ARGUMENT, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DEEP_SLEEP,
                new int[0]), "dispatch deepSleep missing arg");
        assertEquals(DeterministicPowerNativeModule.ERROR_OUT_OF_RANGE, table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DELAY_MICROS,
                new int[]{2_000_000}), "dispatch delay out of range");
    }

    private static VersionedNativeDispatchTable.NativeHandler[] fixedHandlers(int count) {
        VersionedNativeDispatchTable.NativeHandler[] handlers = new VersionedNativeDispatchTable.NativeHandler[count];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = args -> 0;
        }
        return handlers;
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
