# Runtime Frame Stack Model (E1-S3)

This document defines the deterministic frame/stack model implemented by `DeterministicFrameStackModule`.

## Capacity and Determinism

- Frame stack is fixed-capacity and pre-allocated at construction time.
- Locals and operand stacks are fixed-size arrays per frame slot.
- No runtime resizing or dynamic allocation occurs during push/pop/get/set operations.

## Enforced Rules

1. **Frame depth bounds**
   - `pushFrame` fails with `ERROR_FRAME_STACK_OVERFLOW` when depth reaches configured max.
   - `popFrame` fails with `ERROR_FRAME_STACK_UNDERFLOW` when depth is zero.
2. **Local slot bounds**
   - Active-frame local access is limited to configured local slots for that frame.
   - Out-of-range access fails with `ERROR_LOCAL_SLOT_OUT_OF_RANGE`.
3. **Operand stack bounds**
   - Operand push obeys per-frame operand capacity and fails with `ERROR_OPERAND_STACK_OVERFLOW`.
   - Operand pop on an empty operand stack fails with `ERROR_OPERAND_STACK_UNDERFLOW`.
4. **Frame configuration validity**
   - Per-frame local/operand slot counts must be `> 0` and `<=` module capacity.
   - Invalid requests fail with `ERROR_INVALID_SLOT_CONFIGURATION`.

## Safety Behavior

- Popped frames are zeroed before reuse to avoid stale-data leaks across frame reuse.
- Calls that require an active frame return `ERROR_NO_ACTIVE_FRAME` when depth is zero.
