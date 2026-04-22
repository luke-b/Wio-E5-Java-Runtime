package wioe5.runtime;

public final class DeterministicRomizerTest {
    private DeterministicRomizerTest() {
    }

    public static void main(String[] args) {
        testRomizeProducesDeterministicArtifact();
        testRomizeFailurePathsAndCapacityBoundaries();
        testValidatorRejectsCorruptArtifact();
    }

    private static void testRomizeProducesDeterministicArtifact() {
        DeterministicRomizer romizer = new DeterministicRomizer();

        DeterministicRomizer.RomizedClassDefinition[] unorderedClassesA = new DeterministicRomizer.RomizedClassDefinition[]{
                classSensor(),
                classPower()
        };
        DeterministicRomizer.RomizedClassDefinition[] unorderedClassesB = new DeterministicRomizer.RomizedClassDefinition[]{
                classPower(),
                classSensor()
        };

        DeterministicRomizer.NativeSymbolBinding[] bindingsA = new DeterministicRomizer.NativeSymbolBinding[]{
                new DeterministicRomizer.NativeSymbolBinding(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_READ_BATTERY_MV,
                        1),
                new DeterministicRomizer.NativeSymbolBinding(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                        4)
        };
        DeterministicRomizer.NativeSymbolBinding[] bindingsB = new DeterministicRomizer.NativeSymbolBinding[]{
                new DeterministicRomizer.NativeSymbolBinding(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                        4),
                new DeterministicRomizer.NativeSymbolBinding(
                        VersionedNativeDispatchTable.CLASS_HASH_POWER,
                        VersionedNativeDispatchTable.METHOD_HASH_POWER_READ_BATTERY_MV,
                        1)
        };

        DeterministicRomizer.StaticFieldInitializer[] staticFieldsA = new DeterministicRomizer.StaticFieldInitializer[]{
                new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5002, 17),
                new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5001, 11)
        };
        DeterministicRomizer.StaticFieldInitializer[] staticFieldsB = new DeterministicRomizer.StaticFieldInitializer[]{
                new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5001, 11),
                new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5002, 17)
        };

        DeterministicRomizer.RomizeResult first = romizer.romize(unorderedClassesA, bindingsA, 1, staticFieldsA);
        DeterministicRomizer.RomizeResult second = romizer.romize(unorderedClassesB, bindingsB, 1, staticFieldsB);
        assertEquals(DeterministicRomizer.STATUS_OK, first.statusCode(), "first romize status");
        assertEquals(DeterministicRomizer.STATUS_OK, second.statusCode(), "second romize status");

        byte[] firstImage = first.artifactImage();
        byte[] secondImage = second.artifactImage();
        assertArrayEquals(firstImage, secondImage, "deterministic artifact bytes");

        DeterministicRomizer.ValidationResult validation = romizer.validate(firstImage);
        assertEquals(DeterministicRomizer.STATUS_OK, validation.statusCode(), "validation status");
        assertEquals(2, validation.classCount(), "class count");
        assertEquals(3, validation.methodCount(), "method count");
        assertEquals(1, validation.nativeMethodCount(), "native method count");
        assertEquals(2, validation.staticFieldCount(), "static field count");
    }

    private static void testRomizeFailurePathsAndCapacityBoundaries() {
        DeterministicRomizer romizer = new DeterministicRomizer();

        DeterministicRomizer.RomizedClassDefinition duplicateA = new DeterministicRomizer.RomizedClassDefinition(
                0x4500,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.bytecodeMethod(0x6001, new byte[]{0x03, (byte) 0xac})
                });
        DeterministicRomizer.RomizedClassDefinition duplicateB = new DeterministicRomizer.RomizedClassDefinition(
                0x4500,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.bytecodeMethod(0x6002, new byte[]{0x04, (byte) 0xac})
                });

        DeterministicRomizer.RomizeResult duplicateClassResult = romizer.romize(
                new DeterministicRomizer.RomizedClassDefinition[]{duplicateA, duplicateB},
                new DeterministicRomizer.NativeSymbolBinding[0],
                1,
                new DeterministicRomizer.StaticFieldInitializer[0]);
        assertEquals(DeterministicRomizer.ERROR_DUPLICATE_CLASS_HASH, duplicateClassResult.statusCode(), "duplicate class rejected");

        DeterministicRomizer.RomizedClassDefinition mismatchedNativeClass = new DeterministicRomizer.RomizedClassDefinition(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.nativeMethod(
                                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                                5)
                });
        DeterministicRomizer.RomizeResult mismatchResult = romizer.romize(
                new DeterministicRomizer.RomizedClassDefinition[]{mismatchedNativeClass},
                new DeterministicRomizer.NativeSymbolBinding[]{
                        new DeterministicRomizer.NativeSymbolBinding(
                                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                                4)
                },
                1,
                new DeterministicRomizer.StaticFieldInitializer[0]);
        assertEquals(DeterministicRomizer.ERROR_NATIVE_BINDING_MISMATCH, mismatchResult.statusCode(), "native index mismatch rejected");

        byte[] oversizedBytecode = new byte[DeterministicRomizer.MAX_BYTECODE_POOL_BYTES + 1];
        DeterministicRomizer.RomizedClassDefinition oversizedClass = new DeterministicRomizer.RomizedClassDefinition(
                0x4600,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.bytecodeMethod(0x6101, oversizedBytecode)
                });
        DeterministicRomizer.RomizeResult oversizedResult = romizer.romize(
                new DeterministicRomizer.RomizedClassDefinition[]{oversizedClass},
                new DeterministicRomizer.NativeSymbolBinding[0],
                1,
                new DeterministicRomizer.StaticFieldInitializer[0]);
        assertEquals(DeterministicRomizer.ERROR_CAPACITY_EXCEEDED, oversizedResult.statusCode(), "bytecode pool overflow rejected");

        DeterministicRomizer.RomizeResult duplicateStaticResult = romizer.romize(
                new DeterministicRomizer.RomizedClassDefinition[]{classSensor()},
                new DeterministicRomizer.NativeSymbolBinding[0],
                1,
                new DeterministicRomizer.StaticFieldInitializer[]{
                        new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5001, 1),
                        new DeterministicRomizer.StaticFieldInitializer(0x4100, 0x5001, 2)
                });
        assertEquals(DeterministicRomizer.ERROR_DUPLICATE_STATIC_FIELD, duplicateStaticResult.statusCode(), "duplicate static field rejected");
    }

    private static void testValidatorRejectsCorruptArtifact() {
        DeterministicRomizer romizer = new DeterministicRomizer();
        DeterministicRomizer.RomizeResult result = romizer.romize(
                new DeterministicRomizer.RomizedClassDefinition[]{classPower()},
                new DeterministicRomizer.NativeSymbolBinding[]{
                        new DeterministicRomizer.NativeSymbolBinding(
                                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                                4)
                },
                1,
                new DeterministicRomizer.StaticFieldInitializer[0]);
        assertEquals(DeterministicRomizer.STATUS_OK, result.statusCode(), "fixture artifact generated");

        byte[] corrupt = result.artifactImage();
        // Corrupt first magic byte to verify validator fail-fast behavior.
        corrupt[0] = 0;
        DeterministicRomizer.ValidationResult validation = romizer.validate(corrupt);
        assertEquals(DeterministicRomizer.ERROR_INVALID_ARTIFACT, validation.statusCode(), "corrupt magic rejected");
    }

    private static DeterministicRomizer.RomizedClassDefinition classPower() {
        return new DeterministicRomizer.RomizedClassDefinition(
                VersionedNativeDispatchTable.CLASS_HASH_POWER,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.bytecodeMethod(0x3302, new byte[]{0x10, 0x2a, (byte) 0xac}),
                        DeterministicRomizer.RomizedMethodDefinition.nativeMethod(
                                VersionedNativeDispatchTable.METHOD_HASH_POWER_MILLIS,
                                4)
                });
    }

    private static DeterministicRomizer.RomizedClassDefinition classSensor() {
        return new DeterministicRomizer.RomizedClassDefinition(
                0x4100,
                new DeterministicRomizer.RomizedMethodDefinition[]{
                        DeterministicRomizer.RomizedMethodDefinition.bytecodeMethod(0x3301, new byte[]{0x03, (byte) 0xac})
                });
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String label) {
        if (expected == null || actual == null) {
            throw new AssertionError(label + " expected and actual must both be non-null");
        }
        if (expected.length != actual.length) {
            throw new AssertionError(label + " length expected " + expected.length + " but was " + actual.length);
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError(label + " differs at index " + i);
            }
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
