package wioe5.runtime;

public final class VersionedNativeDispatchTableTest {
    private VersionedNativeDispatchTableTest() {
    }

    public static void main(String[] args) {
        testDefaultBindingCoverageAndStableMapping();
        testCompatibilityGateAndDispatch();
        testDispatchFailureModes();
        testDuplicateSymbolRejected();
    }

    private static void testDefaultBindingCoverageAndStableMapping() {
        VersionedNativeDispatchTable.NativeHandler[] handlers = fixedHandlers(VersionedNativeDispatchTable.defaultBindingCount());
        VersionedNativeDispatchTable table = VersionedNativeDispatchTable.createDefault(1, 1, 2, handlers);

        assertEquals(VersionedNativeDispatchTable.defaultBindingCount(), table.bindingCount(), "default binding count");
        assertEquals(0, table.resolveNativeIndex(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_DEEP_SLEEP), "power.deepSleep native index");
        assertEquals(14, table.resolveNativeIndex(
                VersionedNativeDispatchTable.CLASS_HASH_LORAWAN,
                VersionedNativeDispatchTable.METHOD_HASH_LORAWAN_PROCESS), "lorawan.process native index");
        assertEquals(33, table.resolveNativeIndex(
                VersionedNativeDispatchTable.CLASS_HASH_NVCONFIG,
                VersionedNativeDispatchTable.METHOD_HASH_NVCONFIG_WRITE), "nvconfig.write native index");
    }

    private static void testCompatibilityGateAndDispatch() {
        VersionedNativeDispatchTable.NativeHandler[] handlers = fixedHandlers(VersionedNativeDispatchTable.defaultBindingCount());
        VersionedNativeDispatchTable table = VersionedNativeDispatchTable.createDefault(3, 2, 4, handlers);

        int preVerify = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]);
        assertEquals(VersionedNativeDispatchTable.ERROR_INCOMPATIBLE_NATIVE_TABLE_VERSION, preVerify, "dispatch blocked before compatibility");

        int incompatible = table.verifyCompatibility(1);
        assertEquals(VersionedNativeDispatchTable.ERROR_INCOMPATIBLE_NATIVE_TABLE_VERSION, incompatible, "incompatible version");

        int compatible = table.verifyCompatibility(3);
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, compatible, "compatible version");
        assertEquals(3, table.activeRomizedNativeTableVersion(), "active version");

        int result = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[]{10, 20});
        assertEquals(34, result, "dispatch return from native handler");
    }

    private static void testDispatchFailureModes() {
        VersionedNativeDispatchTable.NativeHandler[] handlers = fixedHandlers(VersionedNativeDispatchTable.defaultBindingCount());
        handlers[4] = args -> -7;
        VersionedNativeDispatchTable table = VersionedNativeDispatchTable.createDefault(1, 1, 1, handlers);
        assertEquals(VersionedNativeDispatchTable.STATUS_OK, table.verifyCompatibility(1), "compatibility");

        int notFound = table.dispatch(0x7777, 0x7777, new int[0]);
        assertEquals(VersionedNativeDispatchTable.ERROR_SYMBOL_NOT_FOUND, notFound, "unknown symbol");

        int invalidArg = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                null);
        assertEquals(VersionedNativeDispatchTable.ERROR_INVALID_ARGUMENT, invalidArg, "null args");

        int handlerFailure = table.dispatch(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                new int[0]);
        assertEquals(-7, handlerFailure, "native failure propagated");
    }

    private static void testDuplicateSymbolRejected() {
        VersionedNativeDispatchTable.NativeBinding[] duplicateBindings = new VersionedNativeDispatchTable.NativeBinding[]{
                new VersionedNativeDispatchTable.NativeBinding(0x1000, 0x2000, 0),
                new VersionedNativeDispatchTable.NativeBinding(0x1000, 0x2000, 1)
        };
        VersionedNativeDispatchTable.NativeHandler[] handlers = fixedHandlers(2);
        try {
            new VersionedNativeDispatchTable(1, 1, 1, duplicateBindings, handlers);
            throw new AssertionError("expected duplicate mapping rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("duplicate native symbol mapping"), "duplicate mapping message");
        }
    }

    private static VersionedNativeDispatchTable.NativeHandler[] fixedHandlers(int count) {
        VersionedNativeDispatchTable.NativeHandler[] handlers = new VersionedNativeDispatchTable.NativeHandler[count];
        for (int i = 0; i < handlers.length; i++) {
            final int nativeIndex = i;
            handlers[i] = args -> {
                int sum = nativeIndex;
                for (int j = 0; j < args.length; j++) {
                    sum += args[j];
                }
                return sum;
            };
        }
        return handlers;
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
