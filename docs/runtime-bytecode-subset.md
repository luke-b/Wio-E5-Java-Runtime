# Runtime Bytecode Subset (E1-S2)

This document records the host-validated interpreter subset implemented for **E1-S2** and its explicit error behavior.

## Supported Opcodes in `BytecodeInterpreterModule`

- Constants and stack setup
  - `nop`
  - `iconst_m1..iconst_5`
  - `bipush`
  - `sipush`
- Local variable access
  - `iload`, `iload_0..iload_3`
  - `istore`, `istore_0..istore_3`
- Integer arithmetic
  - `iadd`, `isub`, `imul`, `idiv`, `irem`
- Branch and flow
  - `ifeq`, `ifne`
  - `if_icmpeq`, `if_icmpne`, `if_icmplt`, `if_icmpge`, `if_icmpgt`, `if_icmple`
  - `goto`
  - `ireturn`, `areturn`, `return`
- Object/native integration boundary
  - `new` (delegates allocation to `HeapManagerModule`)
  - `invokestatic`, `invokespecial` (delegates to `NativeDispatchModule`)
  - `ifnull`, `ifnonnull`

## Explicit Runtime Error Model

Interpreter failures are fail-fast and return negative status codes with diagnostic text exposed via `getLastErrorMessage()`:

- Invalid runtime module wiring (`ERROR_INVALID_ARGUMENT`)
- Program counter out of range (`ERROR_PROGRAM_COUNTER_OUT_OF_RANGE`)
- Operand stack overflow/underflow (`ERROR_OPERAND_STACK_OVERFLOW`, `ERROR_OPERAND_STACK_UNDERFLOW`)
- Local index violations (`ERROR_LOCAL_INDEX_OUT_OF_RANGE`)
- Arithmetic divide-by-zero (`ERROR_DIVIDE_BY_ZERO`)
- Heap allocation failure propagated from `HeapManagerModule` (`ERROR_HEAP_ALLOCATION_FAILED`)
- Native dispatch failure propagated from `NativeDispatchModule` (`ERROR_NATIVE_DISPATCH_FAILED`)
- Out-of-range branch target (`ERROR_BRANCH_OUT_OF_RANGE`)
- Invalid instruction operand payload (`ERROR_INVALID_OPERAND`)
- Unsupported opcode (`ERROR_UNSUPPORTED_OPCODE`)

Unsupported bytecodes never execute implicitly; they fail with an explicit opcode/PC diagnostic string.
