package wioe5.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Deterministic ROMizer for building and validating a fixed binary artifact.
 *
 * <p>Format sections:
 * class table, bytecode pool, native table, static data initializers.
 */
public final class DeterministicRomizer {
    public static final int STATUS_OK = 0;

    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_CAPACITY_EXCEEDED = -2;
    public static final int ERROR_DUPLICATE_CLASS_HASH = -3;
    public static final int ERROR_DUPLICATE_METHOD_HASH = -4;
    public static final int ERROR_INVALID_METHOD_DEFINITION = -5;
    public static final int ERROR_NATIVE_BINDING_NOT_FOUND = -6;
    public static final int ERROR_NATIVE_BINDING_MISMATCH = -7;
    public static final int ERROR_VALIDATION_FAILED = -8;
    public static final int ERROR_DUPLICATE_STATIC_FIELD = -9;

    public static final int ERROR_INVALID_ARTIFACT = -20;
    public static final int ERROR_UNSUPPORTED_FORMAT_VERSION = -21;
    public static final int ERROR_SECTION_LAYOUT = -22;
    public static final int ERROR_SECTION_BOUNDS = -23;
    public static final int ERROR_CLASS_TABLE_CORRUPT = -24;
    public static final int ERROR_NATIVE_TABLE_CORRUPT = -25;
    public static final int ERROR_STATIC_TABLE_CORRUPT = -26;

    public static final int FORMAT_MAGIC = 0x57494f35; // "WIO5"
    public static final int FORMAT_VERSION = 1;
    public static final int HEADER_SIZE_BYTES = 44;

    public static final int METHOD_FLAG_BYTECODE = 0;
    public static final int METHOD_FLAG_NATIVE = 1;
    public static final int UNSET_NATIVE_INDEX = 0xffff;
    public static final int UNSET_BYTECODE_OFFSET = 0xffffffff;

    public static final int MAX_CLASSES = 64;
    public static final int MAX_METHODS_PER_CLASS = 64;
    public static final int MAX_TOTAL_METHODS = 256;
    public static final int MAX_BYTECODE_POOL_BYTES = 32768;
    public static final int MAX_NATIVE_BINDINGS = 256;
    public static final int MAX_STATIC_FIELDS = 256;

    public RomizeResult romize(
            RomizedClassDefinition[] classes,
            NativeSymbolBinding[] nativeBindings,
            int romizedNativeTableVersion,
            StaticFieldInitializer[] staticFields) {
        if (classes == null || nativeBindings == null || staticFields == null) {
            return RomizeResult.failure(ERROR_INVALID_ARGUMENT, "romizer inputs must not be null");
        }
        if (romizedNativeTableVersion <= 0) {
            return RomizeResult.failure(ERROR_INVALID_ARGUMENT, "romizedNativeTableVersion must be > 0");
        }
        if (classes.length > MAX_CLASSES || nativeBindings.length > MAX_NATIVE_BINDINGS || staticFields.length > MAX_STATIC_FIELDS) {
            return RomizeResult.failure(ERROR_CAPACITY_EXCEEDED, "input capacity exceeded");
        }

        RomizedClassDefinition[] sortedClasses = copyAndSortClasses(classes);
        if (sortedClasses == null) {
            return RomizeResult.failure(ERROR_INVALID_ARGUMENT, "class entry must not be null");
        }
        int classValidation = validateClassSet(sortedClasses);
        if (classValidation != STATUS_OK) {
            return RomizeResult.failure(classValidation, "class definition validation failed");
        }

        NativeSymbolBinding[] sortedBindings = copyAndSortBindings(nativeBindings);
        if (sortedBindings == null) {
            return RomizeResult.failure(ERROR_INVALID_ARGUMENT, "native binding entry must not be null");
        }
        int bindingValidation = validateBindings(sortedBindings);
        if (bindingValidation != STATUS_OK) {
            return RomizeResult.failure(bindingValidation, "native binding validation failed");
        }

        StaticFieldInitializer[] sortedStaticFields = copyAndSortStaticFields(staticFields);
        if (sortedStaticFields == null) {
            return RomizeResult.failure(ERROR_INVALID_ARGUMENT, "static field entry must not be null");
        }
        int staticValidation = validateStaticFields(sortedStaticFields);
        if (staticValidation != STATUS_OK) {
            return RomizeResult.failure(staticValidation, "static field validation failed");
        }

        int totalMethodCount = countTotalMethods(sortedClasses);
        if (totalMethodCount > MAX_TOTAL_METHODS) {
            return RomizeResult.failure(ERROR_CAPACITY_EXCEEDED, "total method capacity exceeded");
        }

        int bytecodePoolLength = countBytecodePoolLength(sortedClasses);
        if (bytecodePoolLength < 0) {
            return RomizeResult.failure(ERROR_INVALID_METHOD_DEFINITION, "method encoding is invalid");
        }
        if (bytecodePoolLength > MAX_BYTECODE_POOL_BYTES) {
            return RomizeResult.failure(ERROR_CAPACITY_EXCEEDED, "bytecode pool capacity exceeded");
        }

        int classTableLength = computeClassTableLength(sortedClasses);
        int nativeTableLength = computeNativeTableLength(sortedBindings);
        int staticTableLength = computeStaticTableLength(sortedStaticFields);

        int classTableOffset = HEADER_SIZE_BYTES;
        int bytecodePoolOffset = classTableOffset + classTableLength;
        int nativeTableOffset = bytecodePoolOffset + bytecodePoolLength;
        int staticTableOffset = nativeTableOffset + nativeTableLength;
        int totalImageLength = staticTableOffset + staticTableLength;

        byte[] image = new byte[totalImageLength];
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.BIG_ENDIAN);

        writeHeader(buffer, classTableOffset, classTableLength, bytecodePoolOffset, bytecodePoolLength, nativeTableOffset, nativeTableLength, staticTableOffset, staticTableLength, totalImageLength);
        int nativeBindingCheck = writeClassAndBytecodeSections(buffer, classTableOffset, bytecodePoolOffset, sortedClasses, sortedBindings);
        if (nativeBindingCheck != STATUS_OK) {
            return RomizeResult.failure(nativeBindingCheck, "native binding linkage failed");
        }
        writeNativeTableSection(buffer, nativeTableOffset, romizedNativeTableVersion, sortedBindings);
        writeStaticTableSection(buffer, staticTableOffset, sortedStaticFields);

        ValidationResult validation = validate(image);
        if (validation.statusCode() != STATUS_OK) {
            return RomizeResult.failure(ERROR_VALIDATION_FAILED, "artifact failed self-validation: " + validation.errorMessage());
        }
        return RomizeResult.success(image);
    }

    public ValidationResult validate(byte[] artifactImage) {
        if (artifactImage == null) {
            return ValidationResult.failure(ERROR_INVALID_ARGUMENT, "artifactImage must not be null");
        }
        if (artifactImage.length < HEADER_SIZE_BYTES) {
            return ValidationResult.failure(ERROR_INVALID_ARTIFACT, "artifact shorter than header");
        }

        ByteBuffer header = ByteBuffer.wrap(artifactImage).order(ByteOrder.BIG_ENDIAN);
        int magic = header.getInt();
        if (magic != FORMAT_MAGIC) {
            return ValidationResult.failure(ERROR_INVALID_ARTIFACT, "invalid artifact magic");
        }
        int formatVersion = header.getShort() & 0xffff;
        if (formatVersion != FORMAT_VERSION) {
            return ValidationResult.failure(ERROR_UNSUPPORTED_FORMAT_VERSION, "unsupported format version");
        }
        header.getShort(); // reserved

        int classOffset = header.getInt();
        int classLength = header.getInt();
        int bytecodeOffset = header.getInt();
        int bytecodeLength = header.getInt();
        int nativeOffset = header.getInt();
        int nativeLength = header.getInt();
        int staticOffset = header.getInt();
        int staticLength = header.getInt();
        int totalLength = header.getInt();

        if (totalLength != artifactImage.length) {
            return ValidationResult.failure(ERROR_SECTION_LAYOUT, "header total length mismatch");
        }
        if (classOffset != HEADER_SIZE_BYTES
                || bytecodeOffset != classOffset + classLength
                || nativeOffset != bytecodeOffset + bytecodeLength
                || staticOffset != nativeOffset + nativeLength
                || staticOffset + staticLength != totalLength) {
            return ValidationResult.failure(ERROR_SECTION_LAYOUT, "section offsets are not contiguous");
        }
        if (!withinBounds(classOffset, classLength, artifactImage.length)
                || !withinBounds(bytecodeOffset, bytecodeLength, artifactImage.length)
                || !withinBounds(nativeOffset, nativeLength, artifactImage.length)
                || !withinBounds(staticOffset, staticLength, artifactImage.length)) {
            return ValidationResult.failure(ERROR_SECTION_BOUNDS, "section exceeds artifact bounds");
        }

        SectionMetrics metrics = parseClassTableAndValidateBytecode(artifactImage, classOffset, classLength, bytecodeLength);
        if (metrics.errorCode != STATUS_OK) {
            return ValidationResult.failure(metrics.errorCode, metrics.errorMessage);
        }

        int nativeValidation = parseAndValidateNativeTable(
                artifactImage,
                nativeOffset,
                nativeLength,
                metrics.nativeClassHashes,
                metrics.nativeMethodHashes,
                metrics.nativeIndexes,
                metrics.nativeReferenceCount);
        if (nativeValidation != STATUS_OK) {
            return ValidationResult.failure(nativeValidation, "native table validation failed");
        }

        StaticTableParseResult staticParse = parseAndValidateStaticTable(artifactImage, staticOffset, staticLength);
        if (staticParse.errorCode != STATUS_OK) {
            return ValidationResult.failure(staticParse.errorCode, "static table validation failed");
        }

        return ValidationResult.success(metrics.classCount, metrics.methodCount, metrics.nativeReferenceCount, staticParse.staticFieldCount);
    }

    private static void writeHeader(
            ByteBuffer buffer,
            int classOffset,
            int classLength,
            int bytecodeOffset,
            int bytecodeLength,
            int nativeOffset,
            int nativeLength,
            int staticOffset,
            int staticLength,
            int totalLength) {
        buffer.position(0);
        buffer.putInt(FORMAT_MAGIC);
        buffer.putShort((short) FORMAT_VERSION);
        buffer.putShort((short) 0);
        buffer.putInt(classOffset);
        buffer.putInt(classLength);
        buffer.putInt(bytecodeOffset);
        buffer.putInt(bytecodeLength);
        buffer.putInt(nativeOffset);
        buffer.putInt(nativeLength);
        buffer.putInt(staticOffset);
        buffer.putInt(staticLength);
        buffer.putInt(totalLength);
    }

    private static int writeClassAndBytecodeSections(
            ByteBuffer buffer,
            int classTableOffset,
            int bytecodePoolOffset,
            RomizedClassDefinition[] classes,
            NativeSymbolBinding[] bindings) {
        buffer.position(classTableOffset);
        putUnsignedShort(buffer, classes.length);

        int bytecodeCursor = 0;
        for (int i = 0; i < classes.length; i++) {
            RomizedClassDefinition classDef = classes[i];
            buffer.putInt(classDef.classHash());
            putUnsignedShort(buffer, classDef.methodCount());
            for (int j = 0; j < classDef.methodCount(); j++) {
                RomizedMethodDefinition method = classDef.methodAt(j);
                buffer.putInt(method.methodHash());
                if (method.isNativeMethod()) {
                    int nativeIndex = findBindingIndex(bindings, classDef.classHash(), method.methodHash());
                    if (nativeIndex < 0) {
                        return ERROR_NATIVE_BINDING_NOT_FOUND;
                    }
                    if (method.nativeIndex() != nativeIndex) {
                        return ERROR_NATIVE_BINDING_MISMATCH;
                    }
                    buffer.put((byte) METHOD_FLAG_NATIVE);
                    buffer.putInt(UNSET_BYTECODE_OFFSET);
                    putUnsignedShort(buffer, 0);
                    putUnsignedShort(buffer, nativeIndex);
                } else {
                    byte[] bytecode = method.bytecode();
                    buffer.put((byte) METHOD_FLAG_BYTECODE);
                    buffer.putInt(bytecodeCursor);
                    putUnsignedShort(buffer, bytecode.length);
                    putUnsignedShort(buffer, UNSET_NATIVE_INDEX);
                    int bytecodeTarget = bytecodePoolOffset + bytecodeCursor;
                    int savedPosition = buffer.position();
                    buffer.position(bytecodeTarget);
                    buffer.put(bytecode);
                    buffer.position(savedPosition);
                    bytecodeCursor += bytecode.length;
                }
            }
        }
        return STATUS_OK;
    }

    private static void writeNativeTableSection(
            ByteBuffer buffer,
            int nativeTableOffset,
            int romizedNativeTableVersion,
            NativeSymbolBinding[] bindings) {
        buffer.position(nativeTableOffset);
        putUnsignedShort(buffer, romizedNativeTableVersion);
        putUnsignedShort(buffer, bindings.length);
        for (int i = 0; i < bindings.length; i++) {
            NativeSymbolBinding binding = bindings[i];
            buffer.putInt(binding.classHash());
            buffer.putInt(binding.methodHash());
            putUnsignedShort(buffer, binding.nativeIndex());
        }
    }

    private static void writeStaticTableSection(
            ByteBuffer buffer,
            int staticTableOffset,
            StaticFieldInitializer[] staticFields) {
        buffer.position(staticTableOffset);
        putUnsignedShort(buffer, staticFields.length);
        for (int i = 0; i < staticFields.length; i++) {
            StaticFieldInitializer field = staticFields[i];
            buffer.putInt(field.classHash());
            buffer.putInt(field.fieldHash());
            buffer.putInt(field.initialValue());
        }
    }

    private static SectionMetrics parseClassTableAndValidateBytecode(
            byte[] image,
            int classOffset,
            int classLength,
            int bytecodeLength) {
        ByteBuffer section = ByteBuffer.wrap(image, classOffset, classLength).order(ByteOrder.BIG_ENDIAN);
        if (classLength < 2) {
            return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "class table too short");
        }
        int classCount = section.getShort() & 0xffff;
        if (classCount > MAX_CLASSES) {
            return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "class count exceeds max");
        }

        int[] nativeClassHashes = new int[MAX_TOTAL_METHODS];
        int[] nativeMethodHashes = new int[MAX_TOTAL_METHODS];
        int[] nativeIndexes = new int[MAX_TOTAL_METHODS];
        int nativeReferenceCount = 0;
        int methodCount = 0;
        int previousClassHash = Integer.MIN_VALUE;

        for (int classIdx = 0; classIdx < classCount; classIdx++) {
            if (section.remaining() < 6) {
                return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "class entry truncated");
            }
            int classHash = section.getInt();
            if (classHash <= previousClassHash) {
                return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "class hashes not strictly increasing");
            }
            previousClassHash = classHash;

            int methodPerClass = section.getShort() & 0xffff;
            if (methodPerClass > MAX_METHODS_PER_CLASS) {
                return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "method count per class exceeds max");
            }

            int previousMethodHash = Integer.MIN_VALUE;
            for (int methodIdx = 0; methodIdx < methodPerClass; methodIdx++) {
                if (section.remaining() < 13) {
                    return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "method entry truncated");
                }
                int methodHash = section.getInt();
                if (methodHash <= previousMethodHash) {
                    return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "method hashes not strictly increasing");
                }
                previousMethodHash = methodHash;
                int methodFlag = section.get() & 0xff;
                int bytecodeOffset = section.getInt();
                int bytecodeLen = section.getShort() & 0xffff;
                int nativeIndex = section.getShort() & 0xffff;
                methodCount++;
                if (methodCount > MAX_TOTAL_METHODS) {
                    return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "total method count exceeds max");
                }

                if (methodFlag == METHOD_FLAG_BYTECODE) {
                    if (nativeIndex != UNSET_NATIVE_INDEX || bytecodeLen <= 0) {
                        return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "invalid bytecode method entry");
                    }
                    if (!withinBounds(bytecodeOffset, bytecodeLen, bytecodeLength)) {
                        return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "bytecode method points out of pool bounds");
                    }
                } else if (methodFlag == METHOD_FLAG_NATIVE) {
                    if (bytecodeOffset != UNSET_BYTECODE_OFFSET || bytecodeLen != 0 || nativeIndex == UNSET_NATIVE_INDEX) {
                        return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "invalid native method entry");
                    }
                    nativeClassHashes[nativeReferenceCount] = classHash;
                    nativeMethodHashes[nativeReferenceCount] = methodHash;
                    nativeIndexes[nativeReferenceCount] = nativeIndex;
                    nativeReferenceCount++;
                } else {
                    return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "unsupported method flag");
                }
            }
        }
        if (section.remaining() != 0) {
            return SectionMetrics.failure(ERROR_CLASS_TABLE_CORRUPT, "class table has trailing bytes");
        }

        return SectionMetrics.success(classCount, methodCount, nativeReferenceCount, nativeClassHashes, nativeMethodHashes, nativeIndexes);
    }

    private static int parseAndValidateNativeTable(
            byte[] image,
            int nativeOffset,
            int nativeLength,
            int[] nativeClassHashes,
            int[] nativeMethodHashes,
            int[] nativeIndexes,
            int nativeReferenceCount) {
        ByteBuffer section = ByteBuffer.wrap(image, nativeOffset, nativeLength).order(ByteOrder.BIG_ENDIAN);
        if (nativeLength < 4) {
            return ERROR_NATIVE_TABLE_CORRUPT;
        }
        int nativeTableVersion = section.getShort() & 0xffff;
        if (nativeTableVersion == 0) {
            return ERROR_NATIVE_TABLE_CORRUPT;
        }
        int bindingCount = section.getShort() & 0xffff;
        if (bindingCount > MAX_NATIVE_BINDINGS) {
            return ERROR_NATIVE_TABLE_CORRUPT;
        }

        int[] bindingClassHashes = new int[bindingCount];
        int[] bindingMethodHashes = new int[bindingCount];
        int[] bindingIndexes = new int[bindingCount];

        int prevClassHash = Integer.MIN_VALUE;
        int prevMethodHash = Integer.MIN_VALUE;
        for (int i = 0; i < bindingCount; i++) {
            if (section.remaining() < 10) {
                return ERROR_NATIVE_TABLE_CORRUPT;
            }
            int classHash = section.getInt();
            int methodHash = section.getInt();
            int nativeIndex = section.getShort() & 0xffff;
            if (i == 0) {
                prevClassHash = classHash;
                prevMethodHash = methodHash;
            } else {
                if (classHash < prevClassHash || (classHash == prevClassHash && methodHash <= prevMethodHash)) {
                    return ERROR_NATIVE_TABLE_CORRUPT;
                }
                prevClassHash = classHash;
                prevMethodHash = methodHash;
            }
            bindingClassHashes[i] = classHash;
            bindingMethodHashes[i] = methodHash;
            bindingIndexes[i] = nativeIndex;
        }
        if (section.remaining() != 0) {
            return ERROR_NATIVE_TABLE_CORRUPT;
        }

        for (int i = 0; i < nativeReferenceCount; i++) {
            boolean found = false;
            for (int j = 0; j < bindingCount; j++) {
                if (nativeClassHashes[i] == bindingClassHashes[j] && nativeMethodHashes[i] == bindingMethodHashes[j]) {
                    if (nativeIndexes[i] != bindingIndexes[j]) {
                        return ERROR_NATIVE_TABLE_CORRUPT;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                return ERROR_NATIVE_TABLE_CORRUPT;
            }
        }
        return STATUS_OK;
    }

    private static StaticTableParseResult parseAndValidateStaticTable(byte[] image, int staticOffset, int staticLength) {
        ByteBuffer section = ByteBuffer.wrap(image, staticOffset, staticLength).order(ByteOrder.BIG_ENDIAN);
        if (staticLength < 2) {
            return StaticTableParseResult.failure(ERROR_STATIC_TABLE_CORRUPT);
        }
        int count = section.getShort() & 0xffff;
        if (count > MAX_STATIC_FIELDS) {
            return StaticTableParseResult.failure(ERROR_STATIC_TABLE_CORRUPT);
        }

        int prevClassHash = Integer.MIN_VALUE;
        int prevFieldHash = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (section.remaining() < 12) {
                return StaticTableParseResult.failure(ERROR_STATIC_TABLE_CORRUPT);
            }
            int classHash = section.getInt();
            int fieldHash = section.getInt();
            section.getInt();

            if (i == 0) {
                prevClassHash = classHash;
                prevFieldHash = fieldHash;
            } else {
                if (classHash < prevClassHash || (classHash == prevClassHash && fieldHash <= prevFieldHash)) {
                    return StaticTableParseResult.failure(ERROR_STATIC_TABLE_CORRUPT);
                }
                prevClassHash = classHash;
                prevFieldHash = fieldHash;
            }
        }
        if (section.remaining() != 0) {
            return StaticTableParseResult.failure(ERROR_STATIC_TABLE_CORRUPT);
        }
        return StaticTableParseResult.success(count);
    }

    private static boolean withinBounds(int offset, int length, int maxLength) {
        if (offset < 0 || length < 0) {
            return false;
        }
        long end = (long) offset + (long) length;
        return end <= maxLength;
    }

    private static int validateClassSet(RomizedClassDefinition[] classes) {
        for (int i = 0; i < classes.length; i++) {
            RomizedClassDefinition classDef = classes[i];
            if (classDef == null) {
                return ERROR_INVALID_ARGUMENT;
            }
            if (i > 0 && classes[i - 1].classHash() == classDef.classHash()) {
                return ERROR_DUPLICATE_CLASS_HASH;
            }
            if (classDef.methodCount() <= 0 || classDef.methodCount() > MAX_METHODS_PER_CLASS) {
                return ERROR_INVALID_METHOD_DEFINITION;
            }
            for (int j = 0; j < classDef.methodCount(); j++) {
                RomizedMethodDefinition method = classDef.methodAt(j);
                if (method == null) {
                    return ERROR_INVALID_METHOD_DEFINITION;
                }
                if (j > 0 && classDef.methodAt(j - 1).methodHash() == method.methodHash()) {
                    return ERROR_DUPLICATE_METHOD_HASH;
                }
                byte[] bytecode = method.bytecode();
                if (method.isNativeMethod()) {
                    if (bytecode != null || method.nativeIndex() < 0 || method.nativeIndex() >= UNSET_NATIVE_INDEX) {
                        return ERROR_INVALID_METHOD_DEFINITION;
                    }
                } else {
                    if (bytecode == null || bytecode.length <= 0 || bytecode.length > UNSET_NATIVE_INDEX) {
                        return ERROR_INVALID_METHOD_DEFINITION;
                    }
                    if (method.nativeIndex() != UNSET_NATIVE_INDEX) {
                        return ERROR_INVALID_METHOD_DEFINITION;
                    }
                }
            }
        }
        return STATUS_OK;
    }

    private static int validateBindings(NativeSymbolBinding[] bindings) {
        for (int i = 0; i < bindings.length; i++) {
            NativeSymbolBinding binding = bindings[i];
            if (binding == null || binding.nativeIndex() < 0 || binding.nativeIndex() >= UNSET_NATIVE_INDEX) {
                return ERROR_INVALID_ARGUMENT;
            }
            if (i > 0 && bindings[i - 1].classHash() == binding.classHash() && bindings[i - 1].methodHash() == binding.methodHash()) {
                return ERROR_DUPLICATE_METHOD_HASH;
            }
        }
        return STATUS_OK;
    }

    private static int validateStaticFields(StaticFieldInitializer[] staticFields) {
        for (int i = 0; i < staticFields.length; i++) {
            StaticFieldInitializer current = staticFields[i];
            if (current == null) {
                return ERROR_INVALID_ARGUMENT;
            }
            if (i > 0) {
                StaticFieldInitializer prev = staticFields[i - 1];
                if (prev.classHash() == current.classHash() && prev.fieldHash() == current.fieldHash()) {
                    return ERROR_DUPLICATE_STATIC_FIELD;
                }
            }
        }
        return STATUS_OK;
    }

    private static int countTotalMethods(RomizedClassDefinition[] classes) {
        int total = 0;
        for (int i = 0; i < classes.length; i++) {
            total += classes[i].methodCount();
        }
        return total;
    }

    private static int countBytecodePoolLength(RomizedClassDefinition[] classes) {
        int total = 0;
        for (int i = 0; i < classes.length; i++) {
            RomizedClassDefinition classDef = classes[i];
            for (int j = 0; j < classDef.methodCount(); j++) {
                RomizedMethodDefinition method = classDef.methodAt(j);
                if (!method.isNativeMethod()) {
                    total += method.bytecode().length;
                    if (total > MAX_BYTECODE_POOL_BYTES) {
                        return total;
                    }
                }
            }
        }
        return total;
    }

    private static int computeClassTableLength(RomizedClassDefinition[] classes) {
        int methodCount = countTotalMethods(classes);
        return 2 + (classes.length * 6) + (methodCount * 13);
    }

    private static int computeNativeTableLength(NativeSymbolBinding[] nativeBindings) {
        return 4 + (nativeBindings.length * 10);
    }

    private static int computeStaticTableLength(StaticFieldInitializer[] staticFields) {
        return 2 + (staticFields.length * 12);
    }

    private static int findBindingIndex(NativeSymbolBinding[] bindings, int classHash, int methodHash) {
        int low = 0;
        int high = bindings.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            NativeSymbolBinding binding = bindings[mid];
            int compare = compareSymbol(classHash, methodHash, binding.classHash(), binding.methodHash());
            if (compare == 0) {
                return binding.nativeIndex();
            }
            if (compare < 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return -1;
    }

    private static RomizedClassDefinition[] copyAndSortClasses(RomizedClassDefinition[] source) {
        RomizedClassDefinition[] copy = new RomizedClassDefinition[source.length];
        for (int i = 0; i < source.length; i++) {
            RomizedClassDefinition classDef = source[i];
            if (classDef == null) {
                return null;
            }
            RomizedMethodDefinition[] methods = classDef.methodsCopy();
            sortMethods(methods);
            copy[i] = new RomizedClassDefinition(classDef.classHash(), methods);
        }
        sortClasses(copy);
        return copy;
    }

    private static NativeSymbolBinding[] copyAndSortBindings(NativeSymbolBinding[] source) {
        NativeSymbolBinding[] copy = new NativeSymbolBinding[source.length];
        for (int i = 0; i < source.length; i++) {
            NativeSymbolBinding binding = source[i];
            if (binding == null) {
                return null;
            }
            copy[i] = new NativeSymbolBinding(binding.classHash(), binding.methodHash(), binding.nativeIndex());
        }
        sortBindings(copy);
        return copy;
    }

    private static StaticFieldInitializer[] copyAndSortStaticFields(StaticFieldInitializer[] source) {
        StaticFieldInitializer[] copy = new StaticFieldInitializer[source.length];
        for (int i = 0; i < source.length; i++) {
            StaticFieldInitializer field = source[i];
            if (field == null) {
                return null;
            }
            copy[i] = new StaticFieldInitializer(field.classHash(), field.fieldHash(), field.initialValue());
        }
        sortStaticFields(copy);
        return copy;
    }

    private static void sortClasses(RomizedClassDefinition[] classes) {
        for (int i = 1; i < classes.length; i++) {
            RomizedClassDefinition current = classes[i];
            int j = i - 1;
            while (j >= 0 && classes[j].classHash() > current.classHash()) {
                classes[j + 1] = classes[j];
                j--;
            }
            classes[j + 1] = current;
        }
    }

    private static void sortMethods(RomizedMethodDefinition[] methods) {
        for (int i = 1; i < methods.length; i++) {
            RomizedMethodDefinition current = methods[i];
            int j = i - 1;
            while (j >= 0 && methods[j].methodHash() > current.methodHash()) {
                methods[j + 1] = methods[j];
                j--;
            }
            methods[j + 1] = current;
        }
    }

    private static void sortBindings(NativeSymbolBinding[] bindings) {
        for (int i = 1; i < bindings.length; i++) {
            NativeSymbolBinding current = bindings[i];
            int j = i - 1;
            while (j >= 0 && compareSymbol(bindings[j].classHash(), bindings[j].methodHash(), current.classHash(), current.methodHash()) > 0) {
                bindings[j + 1] = bindings[j];
                j--;
            }
            bindings[j + 1] = current;
        }
    }

    private static void sortStaticFields(StaticFieldInitializer[] staticFields) {
        for (int i = 1; i < staticFields.length; i++) {
            StaticFieldInitializer current = staticFields[i];
            int j = i - 1;
            while (j >= 0 && compareSymbol(staticFields[j].classHash(), staticFields[j].fieldHash(), current.classHash(), current.fieldHash()) > 0) {
                staticFields[j + 1] = staticFields[j];
                j--;
            }
            staticFields[j + 1] = current;
        }
    }

    private static int compareSymbol(int leftClass, int leftMethod, int rightClass, int rightMethod) {
        if (leftClass != rightClass) {
            return leftClass < rightClass ? -1 : 1;
        }
        if (leftMethod == rightMethod) {
            return 0;
        }
        return leftMethod < rightMethod ? -1 : 1;
    }

    private static void putUnsignedShort(ByteBuffer buffer, int value) {
        buffer.putShort((short) (value & 0xffff));
    }

    public static final class RomizedClassDefinition {
        private final int classHash;
        private final RomizedMethodDefinition[] methods;

        public RomizedClassDefinition(int classHash, RomizedMethodDefinition[] methods) {
            if (methods == null || methods.length == 0) {
                throw new IllegalArgumentException("methods must not be null or empty");
            }
            this.classHash = classHash;
            this.methods = new RomizedMethodDefinition[methods.length];
            for (int i = 0; i < methods.length; i++) {
                if (methods[i] == null) {
                    throw new IllegalArgumentException("method entry must not be null");
                }
                this.methods[i] = methods[i];
            }
        }

        public int classHash() {
            return classHash;
        }

        public int methodCount() {
            return methods.length;
        }

        public RomizedMethodDefinition methodAt(int index) {
            return methods[index];
        }

        private RomizedMethodDefinition[] methodsCopy() {
            RomizedMethodDefinition[] copy = new RomizedMethodDefinition[methods.length];
            for (int i = 0; i < methods.length; i++) {
                copy[i] = methods[i];
            }
            return copy;
        }
    }

    public static final class RomizedMethodDefinition {
        private final int methodHash;
        private final byte[] bytecode;
        private final int nativeIndex;

        private RomizedMethodDefinition(int methodHash, byte[] bytecode, int nativeIndex) {
            this.methodHash = methodHash;
            this.bytecode = bytecode;
            this.nativeIndex = nativeIndex;
        }

        public static RomizedMethodDefinition bytecodeMethod(int methodHash, byte[] bytecode) {
            if (bytecode == null || bytecode.length == 0) {
                throw new IllegalArgumentException("bytecode method requires non-empty bytecode");
            }
            byte[] copy = new byte[bytecode.length];
            for (int i = 0; i < bytecode.length; i++) {
                copy[i] = bytecode[i];
            }
            return new RomizedMethodDefinition(methodHash, copy, UNSET_NATIVE_INDEX);
        }

        public static RomizedMethodDefinition nativeMethod(int methodHash, int nativeIndex) {
            if (nativeIndex < 0 || nativeIndex >= UNSET_NATIVE_INDEX) {
                throw new IllegalArgumentException("nativeIndex out of range");
            }
            return new RomizedMethodDefinition(methodHash, null, nativeIndex);
        }

        public int methodHash() {
            return methodHash;
        }

        public boolean isNativeMethod() {
            return bytecode == null;
        }

        public byte[] bytecode() {
            if (bytecode == null) {
                return null;
            }
            byte[] copy = new byte[bytecode.length];
            for (int i = 0; i < bytecode.length; i++) {
                copy[i] = bytecode[i];
            }
            return copy;
        }

        public int nativeIndex() {
            return nativeIndex;
        }
    }

    public static final class NativeSymbolBinding {
        private final int classHash;
        private final int methodHash;
        private final int nativeIndex;

        public NativeSymbolBinding(int classHash, int methodHash, int nativeIndex) {
            this.classHash = classHash;
            this.methodHash = methodHash;
            this.nativeIndex = nativeIndex;
        }

        public int classHash() {
            return classHash;
        }

        public int methodHash() {
            return methodHash;
        }

        public int nativeIndex() {
            return nativeIndex;
        }
    }

    public static final class StaticFieldInitializer {
        private final int classHash;
        private final int fieldHash;
        private final int initialValue;

        public StaticFieldInitializer(int classHash, int fieldHash, int initialValue) {
            this.classHash = classHash;
            this.fieldHash = fieldHash;
            this.initialValue = initialValue;
        }

        public int classHash() {
            return classHash;
        }

        public int fieldHash() {
            return fieldHash;
        }

        public int initialValue() {
            return initialValue;
        }
    }

    public static final class RomizeResult {
        private final int statusCode;
        private final String errorMessage;
        private final byte[] artifactImage;

        private RomizeResult(int statusCode, String errorMessage, byte[] artifactImage) {
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.artifactImage = artifactImage;
        }

        public static RomizeResult success(byte[] artifactImage) {
            return new RomizeResult(STATUS_OK, "", artifactImage);
        }

        public static RomizeResult failure(int statusCode, String errorMessage) {
            return new RomizeResult(statusCode, errorMessage, null);
        }

        public int statusCode() {
            return statusCode;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public byte[] artifactImage() {
            if (artifactImage == null) {
                return null;
            }
            byte[] copy = new byte[artifactImage.length];
            for (int i = 0; i < artifactImage.length; i++) {
                copy[i] = artifactImage[i];
            }
            return copy;
        }
    }

    public static final class ValidationResult {
        private final int statusCode;
        private final String errorMessage;
        private final int classCount;
        private final int methodCount;
        private final int nativeMethodCount;
        private final int staticFieldCount;

        private ValidationResult(
                int statusCode,
                String errorMessage,
                int classCount,
                int methodCount,
                int nativeMethodCount,
                int staticFieldCount) {
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.nativeMethodCount = nativeMethodCount;
            this.staticFieldCount = staticFieldCount;
        }

        public static ValidationResult success(int classCount, int methodCount, int nativeMethodCount, int staticFieldCount) {
            return new ValidationResult(STATUS_OK, "", classCount, methodCount, nativeMethodCount, staticFieldCount);
        }

        public static ValidationResult failure(int statusCode, String errorMessage) {
            return new ValidationResult(statusCode, errorMessage, 0, 0, 0, 0);
        }

        public int statusCode() {
            return statusCode;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public int classCount() {
            return classCount;
        }

        public int methodCount() {
            return methodCount;
        }

        public int nativeMethodCount() {
            return nativeMethodCount;
        }

        public int staticFieldCount() {
            return staticFieldCount;
        }
    }

    private static final class SectionMetrics {
        private final int errorCode;
        private final String errorMessage;
        private final int classCount;
        private final int methodCount;
        private final int nativeReferenceCount;
        private final int[] nativeClassHashes;
        private final int[] nativeMethodHashes;
        private final int[] nativeIndexes;

        private SectionMetrics(
                int errorCode,
                String errorMessage,
                int classCount,
                int methodCount,
                int nativeReferenceCount,
                int[] nativeClassHashes,
                int[] nativeMethodHashes,
                int[] nativeIndexes) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.nativeReferenceCount = nativeReferenceCount;
            this.nativeClassHashes = nativeClassHashes;
            this.nativeMethodHashes = nativeMethodHashes;
            this.nativeIndexes = nativeIndexes;
        }

        private static SectionMetrics success(
                int classCount,
                int methodCount,
                int nativeReferenceCount,
                int[] nativeClassHashes,
                int[] nativeMethodHashes,
                int[] nativeIndexes) {
            return new SectionMetrics(
                    STATUS_OK,
                    "",
                    classCount,
                    methodCount,
                    nativeReferenceCount,
                    nativeClassHashes,
                    nativeMethodHashes,
                    nativeIndexes);
        }

        private static SectionMetrics failure(int errorCode, String errorMessage) {
            return new SectionMetrics(errorCode, errorMessage, 0, 0, 0, null, null, null);
        }
    }

    private static final class StaticTableParseResult {
        private final int errorCode;
        private final int staticFieldCount;

        private StaticTableParseResult(int errorCode, int staticFieldCount) {
            this.errorCode = errorCode;
            this.staticFieldCount = staticFieldCount;
        }

        private static StaticTableParseResult success(int staticFieldCount) {
            return new StaticTableParseResult(STATUS_OK, staticFieldCount);
        }

        private static StaticTableParseResult failure(int errorCode) {
            return new StaticTableParseResult(errorCode, 0);
        }
    }
}
