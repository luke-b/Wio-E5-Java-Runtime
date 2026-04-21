package wioe5.runtime;

/**
 * Fixed-capacity frame stack with per-frame local and operand slot limits.
 */
public final class DeterministicFrameStackModule implements FrameStackModule {
    public static final int OK = 0;
    public static final int ERROR_NO_ACTIVE_FRAME = -3;
    public static final int ERROR_LOCAL_SLOT_OUT_OF_RANGE = -4;
    public static final int ERROR_OPERAND_STACK_OVERFLOW = -5;
    public static final int ERROR_OPERAND_STACK_UNDERFLOW = -6;
    public static final int ERROR_INVALID_SLOT_CONFIGURATION = -7;

    private final int maxFrames;
    private final int maxLocalSlotsPerFrame;
    private final int maxOperandSlotsPerFrame;

    private final int[] methodIds;
    private final int[] localSlotLimits;
    private final int[] operandSlotLimits;
    private final int[] operandDepthByFrame;
    private final int[][] localsByFrame;
    private final boolean[][] localReferenceByFrame;
    private final int[][] operandByFrame;
    private final boolean[][] operandReferenceByFrame;

    private int depth;

    public DeterministicFrameStackModule(int maxFrames, int maxLocalSlotsPerFrame, int maxOperandSlotsPerFrame) {
        if (maxFrames <= 0) {
            throw new IllegalArgumentException("maxFrames must be > 0");
        }
        if (maxLocalSlotsPerFrame <= 0) {
            throw new IllegalArgumentException("maxLocalSlotsPerFrame must be > 0");
        }
        if (maxOperandSlotsPerFrame <= 0) {
            throw new IllegalArgumentException("maxOperandSlotsPerFrame must be > 0");
        }
        this.maxFrames = maxFrames;
        this.maxLocalSlotsPerFrame = maxLocalSlotsPerFrame;
        this.maxOperandSlotsPerFrame = maxOperandSlotsPerFrame;
        this.methodIds = new int[maxFrames];
        this.localSlotLimits = new int[maxFrames];
        this.operandSlotLimits = new int[maxFrames];
        this.operandDepthByFrame = new int[maxFrames];
        this.localsByFrame = new int[maxFrames][maxLocalSlotsPerFrame];
        this.localReferenceByFrame = new boolean[maxFrames][maxLocalSlotsPerFrame];
        this.operandByFrame = new int[maxFrames][maxOperandSlotsPerFrame];
        this.operandReferenceByFrame = new boolean[maxFrames][maxOperandSlotsPerFrame];
    }

    @Override
    public int pushFrame(int methodId) {
        return pushFrame(methodId, maxLocalSlotsPerFrame, maxOperandSlotsPerFrame);
    }

    public int pushFrame(int methodId, int localSlots, int operandSlots) {
        if (depth >= maxFrames) {
            return ERROR_FRAME_STACK_OVERFLOW;
        }
        if (localSlots <= 0 || localSlots > maxLocalSlotsPerFrame || operandSlots <= 0 || operandSlots > maxOperandSlotsPerFrame) {
            return ERROR_INVALID_SLOT_CONFIGURATION;
        }

        methodIds[depth] = methodId;
        localSlotLimits[depth] = localSlots;
        operandSlotLimits[depth] = operandSlots;
        operandDepthByFrame[depth] = 0;
        clearFrameMemory(depth);
        depth++;
        return OK;
    }

    @Override
    public int popFrame() {
        if (depth <= 0) {
            return ERROR_FRAME_STACK_UNDERFLOW;
        }
        int frameIndex = depth - 1;
        clearFrameMemory(frameIndex);
        methodIds[frameIndex] = 0;
        localSlotLimits[frameIndex] = 0;
        operandSlotLimits[frameIndex] = 0;
        operandDepthByFrame[frameIndex] = 0;
        depth--;
        return OK;
    }

    @Override
    public int currentDepth() {
        return depth;
    }

    @Override
    public int maxDepth() {
        return maxFrames;
    }

    public int currentMethodId() {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        return methodIds[depth - 1];
    }

    public int setLocal(int index, int value) {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        if (index < 0 || index >= localSlotLimits[frameIndex]) {
            return ERROR_LOCAL_SLOT_OUT_OF_RANGE;
        }
        localsByFrame[frameIndex][index] = value;
        localReferenceByFrame[frameIndex][index] = false;
        return OK;
    }

    public int setLocalReference(int index, int referenceHandle) {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        if (index < 0 || index >= localSlotLimits[frameIndex]) {
            return ERROR_LOCAL_SLOT_OUT_OF_RANGE;
        }
        localsByFrame[frameIndex][index] = referenceHandle;
        localReferenceByFrame[frameIndex][index] = true;
        return OK;
    }

    public int getLocal(int index) {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        if (index < 0 || index >= localSlotLimits[frameIndex]) {
            return ERROR_LOCAL_SLOT_OUT_OF_RANGE;
        }
        return localsByFrame[frameIndex][index];
    }

    public int pushOperand(int value) {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        int operandDepth = operandDepthByFrame[frameIndex];
        if (operandDepth >= operandSlotLimits[frameIndex]) {
            return ERROR_OPERAND_STACK_OVERFLOW;
        }
        operandByFrame[frameIndex][operandDepth] = value;
        operandReferenceByFrame[frameIndex][operandDepth] = false;
        operandDepthByFrame[frameIndex] = operandDepth + 1;
        return OK;
    }

    public int pushOperandReference(int referenceHandle) {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        int operandDepth = operandDepthByFrame[frameIndex];
        if (operandDepth >= operandSlotLimits[frameIndex]) {
            return ERROR_OPERAND_STACK_OVERFLOW;
        }
        operandByFrame[frameIndex][operandDepth] = referenceHandle;
        operandReferenceByFrame[frameIndex][operandDepth] = true;
        operandDepthByFrame[frameIndex] = operandDepth + 1;
        return OK;
    }

    public int popOperand() {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        int frameIndex = depth - 1;
        int operandDepth = operandDepthByFrame[frameIndex];
        if (operandDepth <= 0) {
            return ERROR_OPERAND_STACK_UNDERFLOW;
        }
        int newDepth = operandDepth - 1;
        int value = operandByFrame[frameIndex][newDepth];
        operandByFrame[frameIndex][newDepth] = 0;
        operandReferenceByFrame[frameIndex][newDepth] = false;
        operandDepthByFrame[frameIndex] = newDepth;
        return value;
    }

    public int currentOperandDepth() {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        return operandDepthByFrame[depth - 1];
    }

    public int activeLocalSlotLimit() {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        return localSlotLimits[depth - 1];
    }

    public int activeOperandSlotLimit() {
        if (depth <= 0) {
            return ERROR_NO_ACTIVE_FRAME;
        }
        return operandSlotLimits[depth - 1];
    }

    @Override
    public int gcRootCount() {
        int count = 0;
        for (int frameIndex = 0; frameIndex < depth; frameIndex++) {
            for (int localIndex = 0; localIndex < localSlotLimits[frameIndex]; localIndex++) {
                if (localReferenceByFrame[frameIndex][localIndex]) {
                    count++;
                }
            }
            int operandDepth = operandDepthByFrame[frameIndex];
            for (int operandIndex = 0; operandIndex < operandDepth; operandIndex++) {
                if (operandReferenceByFrame[frameIndex][operandIndex]) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public int gcRootAt(int index) {
        if (index < 0) {
            return 0;
        }

        int cursor = 0;
        for (int frameIndex = 0; frameIndex < depth; frameIndex++) {
            for (int localIndex = 0; localIndex < localSlotLimits[frameIndex]; localIndex++) {
                if (localReferenceByFrame[frameIndex][localIndex]) {
                    if (cursor == index) {
                        return localsByFrame[frameIndex][localIndex];
                    }
                    cursor++;
                }
            }
            int operandDepth = operandDepthByFrame[frameIndex];
            for (int operandIndex = 0; operandIndex < operandDepth; operandIndex++) {
                if (operandReferenceByFrame[frameIndex][operandIndex]) {
                    if (cursor == index) {
                        return operandByFrame[frameIndex][operandIndex];
                    }
                    cursor++;
                }
            }
        }
        return 0;
    }

    private void clearFrameMemory(int frameIndex) {
        for (int i = 0; i < maxLocalSlotsPerFrame; i++) {
            localsByFrame[frameIndex][i] = 0;
            localReferenceByFrame[frameIndex][i] = false;
        }
        for (int i = 0; i < maxOperandSlotsPerFrame; i++) {
            operandByFrame[frameIndex][i] = 0;
            operandReferenceByFrame[frameIndex][i] = false;
        }
    }
}
