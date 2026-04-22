package wioe5.runtime;

/**
 * Deterministic native dispatch table with explicit ROMized-version
 * compatibility checks and stable symbol-to-index mapping.
 */
public final class VersionedNativeDispatchTable implements NativeDispatchModule {
    public static final int STATUS_OK = 0;

    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_INCOMPATIBLE_NATIVE_TABLE_VERSION = -2;
    public static final int ERROR_SYMBOL_NOT_FOUND = -3;
    public static final int ERROR_NATIVE_INDEX_OUT_OF_RANGE = -5;
    public static final int ERROR_DUPLICATE_SYMBOL = -6;

    public static final int CLASS_HASH_POWER = 0x1101;
    public static final int CLASS_HASH_GPIO = 0x1102;
    public static final int CLASS_HASH_LORAWAN = 0x1103;
    public static final int CLASS_HASH_I2C = 0x1104;
    public static final int CLASS_HASH_UART = 0x1105;
    public static final int CLASS_HASH_NVCONFIG = 0x1106;

    public static final int METHOD_HASH_POWER_DEEP_SLEEP = 0x2001;
    public static final int METHOD_HASH_POWER_READ_BATTERY_MV = 0x2002;
    public static final int METHOD_HASH_POWER_GET_RESET_REASON = 0x2003;
    public static final int METHOD_HASH_POWER_KICK_WATCHDOG = 0x2004;
    public static final int METHOD_HASH_POWER_MILLIS = 0x2005;
    public static final int METHOD_HASH_POWER_DELAY_MICROS = 0x2006;

    public static final int METHOD_HASH_GPIO_PIN_MODE = 0x2101;
    public static final int METHOD_HASH_GPIO_DIGITAL_WRITE = 0x2102;
    public static final int METHOD_HASH_GPIO_DIGITAL_READ = 0x2103;
    public static final int METHOD_HASH_GPIO_ANALOG_READ = 0x2104;

    public static final int METHOD_HASH_LORAWAN_INIT = 0x2201;
    public static final int METHOD_HASH_LORAWAN_JOIN_OTAA = 0x2202;
    public static final int METHOD_HASH_LORAWAN_JOIN_ABP = 0x2203;
    public static final int METHOD_HASH_LORAWAN_SEND = 0x2204;
    public static final int METHOD_HASH_LORAWAN_PROCESS = 0x2205;
    public static final int METHOD_HASH_LORAWAN_GET_STATUS = 0x2206;
    public static final int METHOD_HASH_LORAWAN_READ_DOWNLINK = 0x2207;
    public static final int METHOD_HASH_LORAWAN_SET_TX_POWER = 0x2208;
    public static final int METHOD_HASH_LORAWAN_SET_ADR = 0x2209;
    public static final int METHOD_HASH_LORAWAN_GET_LAST_RSSI = 0x220a;
    public static final int METHOD_HASH_LORAWAN_GET_LAST_SNR = 0x220b;

    public static final int METHOD_HASH_I2C_BEGIN = 0x2301;
    public static final int METHOD_HASH_I2C_WRITE = 0x2302;
    public static final int METHOD_HASH_I2C_READ = 0x2303;
    public static final int METHOD_HASH_I2C_WRITE_READ = 0x2304;
    public static final int METHOD_HASH_I2C_END = 0x2305;

    public static final int METHOD_HASH_UART_BEGIN = 0x2401;
    public static final int METHOD_HASH_UART_AVAILABLE = 0x2402;
    public static final int METHOD_HASH_UART_READ = 0x2403;
    public static final int METHOD_HASH_UART_WRITE = 0x2404;
    public static final int METHOD_HASH_UART_PRINT = 0x2405;
    public static final int METHOD_HASH_UART_PRINTLN = 0x2406;

    public static final int METHOD_HASH_NVCONFIG_READ = 0x2501;
    public static final int METHOD_HASH_NVCONFIG_WRITE = 0x2502;

    private static final NativeBinding[] DEFAULT_BINDINGS = new NativeBinding[]{
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_DEEP_SLEEP, 0),
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_READ_BATTERY_MV, 1),
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_GET_RESET_REASON, 2),
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_KICK_WATCHDOG, 3),
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_MILLIS, 4),
            new NativeBinding(CLASS_HASH_POWER, METHOD_HASH_POWER_DELAY_MICROS, 5),

            new NativeBinding(CLASS_HASH_GPIO, METHOD_HASH_GPIO_PIN_MODE, 6),
            new NativeBinding(CLASS_HASH_GPIO, METHOD_HASH_GPIO_DIGITAL_WRITE, 7),
            new NativeBinding(CLASS_HASH_GPIO, METHOD_HASH_GPIO_DIGITAL_READ, 8),
            new NativeBinding(CLASS_HASH_GPIO, METHOD_HASH_GPIO_ANALOG_READ, 9),

            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_INIT, 10),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_JOIN_OTAA, 11),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_JOIN_ABP, 12),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_SEND, 13),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_PROCESS, 14),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_GET_STATUS, 15),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_READ_DOWNLINK, 16),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_SET_TX_POWER, 17),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_SET_ADR, 18),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_GET_LAST_RSSI, 19),
            new NativeBinding(CLASS_HASH_LORAWAN, METHOD_HASH_LORAWAN_GET_LAST_SNR, 20),

            new NativeBinding(CLASS_HASH_I2C, METHOD_HASH_I2C_BEGIN, 21),
            new NativeBinding(CLASS_HASH_I2C, METHOD_HASH_I2C_WRITE, 22),
            new NativeBinding(CLASS_HASH_I2C, METHOD_HASH_I2C_READ, 23),
            new NativeBinding(CLASS_HASH_I2C, METHOD_HASH_I2C_WRITE_READ, 24),
            new NativeBinding(CLASS_HASH_I2C, METHOD_HASH_I2C_END, 25),

            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_BEGIN, 26),
            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_AVAILABLE, 27),
            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_READ, 28),
            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_WRITE, 29),
            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_PRINT, 30),
            new NativeBinding(CLASS_HASH_UART, METHOD_HASH_UART_PRINTLN, 31),

            new NativeBinding(CLASS_HASH_NVCONFIG, METHOD_HASH_NVCONFIG_READ, 32),
            new NativeBinding(CLASS_HASH_NVCONFIG, METHOD_HASH_NVCONFIG_WRITE, 33)
    };
    public static final int DEFAULT_BINDING_COUNT = DEFAULT_BINDINGS.length;

    private final int runtimeNativeTableVersion;
    private final int minSupportedRomizedNativeTableVersion;
    private final int maxSupportedRomizedNativeTableVersion;
    private final NativeBinding[] bindings;
    private final NativeHandler[] nativeHandlers;
    private int activeRomizedNativeTableVersion;

    public VersionedNativeDispatchTable(
            int runtimeNativeTableVersion,
            int minSupportedRomizedNativeTableVersion,
            int maxSupportedRomizedNativeTableVersion,
            NativeBinding[] bindings,
            NativeHandler[] nativeHandlers) {
        if (runtimeNativeTableVersion <= 0
                || minSupportedRomizedNativeTableVersion <= 0
                || maxSupportedRomizedNativeTableVersion <= 0
                || minSupportedRomizedNativeTableVersion > maxSupportedRomizedNativeTableVersion) {
            throw new IllegalArgumentException("invalid native-table version configuration");
        }
        if (bindings == null || nativeHandlers == null) {
            throw new IllegalArgumentException("bindings and nativeHandlers must not be null");
        }

        this.runtimeNativeTableVersion = runtimeNativeTableVersion;
        this.minSupportedRomizedNativeTableVersion = minSupportedRomizedNativeTableVersion;
        this.maxSupportedRomizedNativeTableVersion = maxSupportedRomizedNativeTableVersion;
        this.bindings = copyBindings(bindings);
        this.nativeHandlers = copyHandlers(nativeHandlers);
        validateBindings(this.bindings, this.nativeHandlers.length);
    }

    public static VersionedNativeDispatchTable createDefault(
            int runtimeNativeTableVersion,
            int minSupportedRomizedNativeTableVersion,
            int maxSupportedRomizedNativeTableVersion,
            NativeHandler[] nativeHandlers) {
        return new VersionedNativeDispatchTable(
                runtimeNativeTableVersion,
                minSupportedRomizedNativeTableVersion,
                maxSupportedRomizedNativeTableVersion,
                DEFAULT_BINDINGS,
                nativeHandlers);
    }

    public static int defaultBindingCount() {
        return DEFAULT_BINDING_COUNT;
    }

    @Override
    public int verifyCompatibility(int romizedNativeTableVersion) {
        if (romizedNativeTableVersion < minSupportedRomizedNativeTableVersion
                || romizedNativeTableVersion > maxSupportedRomizedNativeTableVersion) {
            activeRomizedNativeTableVersion = 0;
            return ERROR_INCOMPATIBLE_NATIVE_TABLE_VERSION;
        }
        activeRomizedNativeTableVersion = romizedNativeTableVersion;
        return STATUS_OK;
    }

    @Override
    public int dispatch(int classHash, int methodHash, int[] args) {
        if (args == null) {
            return ERROR_INVALID_ARGUMENT;
        }
        if (activeRomizedNativeTableVersion == 0) {
            return ERROR_INCOMPATIBLE_NATIVE_TABLE_VERSION;
        }

        int nativeIndex = resolveNativeIndex(classHash, methodHash);
        if (nativeIndex < 0) {
            return nativeIndex;
        }

        return nativeHandlers[nativeIndex].invoke(args);
    }

    public int resolveNativeIndex(int classHash, int methodHash) {
        for (int i = 0; i < bindings.length; i++) {
            NativeBinding binding = bindings[i];
            if (binding.classHash == classHash && binding.methodHash == methodHash) {
                if (binding.nativeIndex < 0 || binding.nativeIndex >= nativeHandlers.length) {
                    return ERROR_NATIVE_INDEX_OUT_OF_RANGE;
                }
                return binding.nativeIndex;
            }
        }
        return ERROR_SYMBOL_NOT_FOUND;
    }

    public int runtimeNativeTableVersion() {
        return runtimeNativeTableVersion;
    }

    public int minSupportedRomizedNativeTableVersion() {
        return minSupportedRomizedNativeTableVersion;
    }

    public int maxSupportedRomizedNativeTableVersion() {
        return maxSupportedRomizedNativeTableVersion;
    }

    public int activeRomizedNativeTableVersion() {
        return activeRomizedNativeTableVersion;
    }

    public int bindingCount() {
        return bindings.length;
    }

    private static NativeBinding[] copyBindings(NativeBinding[] source) {
        NativeBinding[] copy = new NativeBinding[source.length];
        for (int i = 0; i < source.length; i++) {
            NativeBinding binding = source[i];
            if (binding == null) {
                throw new IllegalArgumentException("binding entry must not be null");
            }
            copy[i] = binding;
        }
        return copy;
    }

    private static NativeHandler[] copyHandlers(NativeHandler[] source) {
        NativeHandler[] copy = new NativeHandler[source.length];
        for (int i = 0; i < source.length; i++) {
            NativeHandler handler = source[i];
            if (handler == null) {
                throw new IllegalArgumentException("native handler entry must not be null");
            }
            copy[i] = handler;
        }
        return copy;
    }

    private static void validateBindings(NativeBinding[] sourceBindings, int handlerCount) {
        for (int i = 0; i < sourceBindings.length; i++) {
            NativeBinding binding = sourceBindings[i];
            if (binding.nativeIndex < 0 || binding.nativeIndex >= handlerCount) {
                throw new IllegalArgumentException("native index out of range at binding " + i);
            }
            for (int j = i + 1; j < sourceBindings.length; j++) {
                NativeBinding other = sourceBindings[j];
                if (binding.classHash == other.classHash && binding.methodHash == other.methodHash) {
                    throw new IllegalArgumentException("duplicate native symbol mapping");
                }
            }
        }
    }

    public interface NativeHandler {
        int invoke(int[] args);
    }

    public static final class NativeBinding {
        private final int classHash;
        private final int methodHash;
        private final int nativeIndex;

        public NativeBinding(int classHash, int methodHash, int nativeIndex) {
            this.classHash = classHash;
            this.methodHash = methodHash;
            this.nativeIndex = nativeIndex;
        }
    }
}
