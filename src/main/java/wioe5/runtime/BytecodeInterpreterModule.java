package wioe5.runtime;

/**
 * Deterministic bytecode interpreter for a bounded CLDC subset.
 */
public final class BytecodeInterpreterModule implements InterpreterModule {
    public static final int STEP_OK = 0;
    public static final int STEP_HALTED = 1;

    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_PROGRAM_COUNTER_OUT_OF_RANGE = -2;
    public static final int ERROR_OPERAND_STACK_OVERFLOW = -3;
    public static final int ERROR_OPERAND_STACK_UNDERFLOW = -4;
    public static final int ERROR_LOCAL_INDEX_OUT_OF_RANGE = -5;
    public static final int ERROR_DIVIDE_BY_ZERO = -6;
    public static final int ERROR_HEAP_ALLOCATION_FAILED = -7;
    public static final int ERROR_NATIVE_DISPATCH_FAILED = -8;
    public static final int ERROR_UNSUPPORTED_OPCODE = -9;
    public static final int ERROR_BRANCH_OUT_OF_RANGE = -10;
    public static final int ERROR_INVALID_OPERAND = -11;

    private final byte[] bytecode;
    private final int[] locals;
    private final int[] operandStack;

    private int pc;
    private int stackDepth;
    private boolean halted;
    private int returnValue;
    private int lastErrorCode;
    private String lastErrorMessage;

    public BytecodeInterpreterModule(byte[] bytecode, int localSlots, int operandStackSlots) {
        if (bytecode == null) {
            throw new IllegalArgumentException("bytecode must not be null");
        }
        if (localSlots <= 0) {
            throw new IllegalArgumentException("localSlots must be > 0");
        }
        if (operandStackSlots <= 0) {
            throw new IllegalArgumentException("operandStackSlots must be > 0");
        }
        this.bytecode = bytecode;
        this.locals = new int[localSlots];
        this.operandStack = new int[operandStackSlots];
        this.lastErrorCode = STEP_OK;
        this.lastErrorMessage = "";
    }

    @Override
    public int executeStep(FrameStackModule frameStack, HeapManagerModule heapManager, NativeDispatchModule nativeDispatch) {
        if (frameStack == null || heapManager == null || nativeDispatch == null) {
            return fail(ERROR_INVALID_ARGUMENT, "Runtime modules must not be null");
        }
        if (halted) {
            return STEP_HALTED;
        }
        if (pc < 0 || pc >= bytecode.length) {
            return fail(ERROR_PROGRAM_COUNTER_OUT_OF_RANGE, "Program counter out of range at " + pc);
        }

        int opPc = pc;
        int opcode = readUnsignedByte();
        switch (opcode) {
            case 0x00: // nop
                return STEP_OK;
            case 0x02:
                return pushConst(-1);
            case 0x03:
                return pushConst(0);
            case 0x04:
                return pushConst(1);
            case 0x05:
                return pushConst(2);
            case 0x06:
                return pushConst(3);
            case 0x07:
                return pushConst(4);
            case 0x08:
                return pushConst(5);
            case 0x10: // bipush
                return pushConst(readSignedByte());
            case 0x11: // sipush
                return pushConst(readSignedShort());
            case 0x15: // iload
                return loadLocal(readUnsignedByte());
            case 0x1a:
                return loadLocal(0);
            case 0x1b:
                return loadLocal(1);
            case 0x1c:
                return loadLocal(2);
            case 0x1d:
                return loadLocal(3);
            case 0x36: // istore
                return storeLocal(readUnsignedByte());
            case 0x3b:
                return storeLocal(0);
            case 0x3c:
                return storeLocal(1);
            case 0x3d:
                return storeLocal(2);
            case 0x3e:
                return storeLocal(3);
            case 0x60:
                return binaryArithmetic('+');
            case 0x64:
                return binaryArithmetic('-');
            case 0x68:
                return binaryArithmetic('*');
            case 0x6c:
                return binaryArithmetic('/');
            case 0x70:
                return binaryArithmetic('%');
            case 0x99:
                return ifZero(opPc, true);
            case 0x9a:
                return ifZero(opPc, false);
            case 0x9f:
                return ifIcmp(opPc, 0);
            case 0xa0:
                return ifIcmp(opPc, 1);
            case 0xa1:
                return ifIcmp(opPc, 2);
            case 0xa2:
                return ifIcmp(opPc, 3);
            case 0xa3:
                return ifIcmp(opPc, 4);
            case 0xa4:
                return ifIcmp(opPc, 5);
            case 0xa7:
                return branch(opPc, readSignedShort());
            case 0xac:
                return returnValue(pop());
            case 0xb0:
                return returnValue(pop());
            case 0xb1:
                halted = true;
                return STEP_HALTED;
            case 0xb7:
            case 0xb8:
                return invokeNative(nativeDispatch);
            case 0xbb:
                return allocateObject(heapManager);
            case 0xc6:
                return ifNull(opPc, true);
            case 0xc7:
                return ifNull(opPc, false);
            default:
                return fail(ERROR_UNSUPPORTED_OPCODE, "Unsupported opcode 0x" + toHex(opcode) + " at pc " + opPc);
        }
    }

    public boolean isHalted() {
        return halted;
    }

    public int getProgramCounter() {
        return pc;
    }

    public int getStackDepth() {
        return stackDepth;
    }

    public int getReturnValue() {
        return returnValue;
    }

    public int getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    private int pushConst(int value) {
        return push(value);
    }

    private int loadLocal(int index) {
        if (index < 0 || index >= locals.length) {
            return fail(ERROR_LOCAL_INDEX_OUT_OF_RANGE, "Local index out of range: " + index);
        }
        return push(locals[index]);
    }

    private int storeLocal(int index) {
        if (index < 0 || index >= locals.length) {
            return fail(ERROR_LOCAL_INDEX_OUT_OF_RANGE, "Local index out of range: " + index);
        }
        int value = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        locals[index] = value;
        return STEP_OK;
    }

    private int binaryArithmetic(char op) {
        int right = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        int left = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }

        if ((op == '/' || op == '%') && right == 0) {
            return fail(ERROR_DIVIDE_BY_ZERO, "Division by zero");
        }

        int result;
        switch (op) {
            case '+':
                result = left + right;
                break;
            case '-':
                result = left - right;
                break;
            case '*':
                result = left * right;
                break;
            case '/':
                result = left / right;
                break;
            case '%':
                result = left % right;
                break;
            default:
                return fail(ERROR_INVALID_OPERAND, "Unsupported arithmetic operator: " + op);
        }
        return push(result);
    }

    private int ifZero(int opPc, boolean branchOnZero) {
        int offset = readSignedShort();
        int value = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        boolean condition = value == 0;
        if (condition == branchOnZero) {
            return branch(opPc, offset);
        }
        return STEP_OK;
    }

    private int ifIcmp(int opPc, int mode) {
        int offset = readSignedShort();
        int right = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        int left = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }

        boolean take;
        switch (mode) {
            case 0:
                take = left == right;
                break;
            case 1:
                take = left != right;
                break;
            case 2:
                take = left < right;
                break;
            case 3:
                take = left >= right;
                break;
            case 4:
                take = left > right;
                break;
            case 5:
                take = left <= right;
                break;
            default:
                return fail(ERROR_INVALID_OPERAND, "Invalid if_icmp mode: " + mode);
        }

        if (take) {
            return branch(opPc, offset);
        }
        return STEP_OK;
    }

    private int ifNull(int opPc, boolean branchOnNull) {
        int offset = readSignedShort();
        int value = pop();
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        boolean isNull = value == 0;
        if (isNull == branchOnNull) {
            return branch(opPc, offset);
        }
        return STEP_OK;
    }

    private int branch(int opPc, int offset) {
        int target = opPc + offset;
        if (target < 0 || target >= bytecode.length) {
            return fail(ERROR_BRANCH_OUT_OF_RANGE, "Branch target out of range: " + target);
        }
        pc = target;
        return STEP_OK;
    }

    private int invokeNative(NativeDispatchModule nativeDispatch) {
        int classHash = readSignedShort();
        int methodHash = readSignedShort();
        int argCount = readUnsignedByte();
        if (argCount > stackDepth) {
            return fail(ERROR_OPERAND_STACK_UNDERFLOW, "Not enough arguments for native dispatch: " + argCount);
        }

        int[] args = new int[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = pop();
            if (lastErrorCode < 0) {
                return lastErrorCode;
            }
        }

        int dispatchResult = nativeDispatch.dispatch(classHash, methodHash, args);
        if (dispatchResult < 0) {
            return fail(ERROR_NATIVE_DISPATCH_FAILED, "Native dispatch failed with code " + dispatchResult);
        }
        return push(dispatchResult);
    }

    private int allocateObject(HeapManagerModule heapManager) {
        int sizeBytes = readUnsignedByte();
        if (sizeBytes <= 0) {
            return fail(ERROR_INVALID_OPERAND, "Allocation size must be > 0");
        }
        int handle = heapManager.allocate(sizeBytes);
        if (handle < 0) {
            return fail(ERROR_HEAP_ALLOCATION_FAILED, "Heap allocation failed with code " + handle);
        }
        return push(handle);
    }

    private int returnValue(int value) {
        if (lastErrorCode < 0) {
            return lastErrorCode;
        }
        this.returnValue = value;
        this.halted = true;
        return STEP_HALTED;
    }

    private int push(int value) {
        if (stackDepth >= operandStack.length) {
            return fail(ERROR_OPERAND_STACK_OVERFLOW, "Operand stack overflow");
        }
        operandStack[stackDepth++] = value;
        return STEP_OK;
    }

    private int pop() {
        if (stackDepth <= 0) {
            fail(ERROR_OPERAND_STACK_UNDERFLOW, "Operand stack underflow");
            return 0;
        }
        return operandStack[--stackDepth];
    }

    private int readUnsignedByte() {
        return bytecode[pc++] & 0xff;
    }

    private int readSignedByte() {
        return (byte) readUnsignedByte();
    }

    private int readSignedShort() {
        int high = readUnsignedByte();
        int low = readUnsignedByte();
        return (short) ((high << 8) | low);
    }

    private int fail(int code, String message) {
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.halted = true;
        return code;
    }

    private static String toHex(int value) {
        String hex = Integer.toHexString(value & 0xff);
        if (hex.length() == 1) {
            return "0" + hex;
        }
        return hex;
    }
}
