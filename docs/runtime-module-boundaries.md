# Runtime Module Boundaries (E1-S1)

This document defines the runtime-core module boundaries required by the architecture and captured in `wioe5.runtime`.

## Modules and Ownership

1. **InterpreterModule**
   - Owns deterministic bytecode step execution.
   - Consumes `FrameStackModule`, `HeapManagerModule`, and `NativeDispatchModule` as explicit collaborators.

2. **FrameStackModule**
   - Owns frame push/pop lifecycle and depth constraints.
   - Exposes frame depth information needed by runtime checks and GC root discovery.
   - `DeterministicFrameStackModule` provides fixed-capacity frame/local/operand enforcement for constrained targets.

3. **HeapManagerModule**
    - Owns allocation and garbage-collection entry point.
    - Receives `FrameStackModule` to enforce root traversal boundary at the module interface.
   - `DeterministicHeapManagerModule` provides fixed-capacity bump allocation and mark-sweep collection with deterministic pause metrics.

4. **NativeDispatchModule**
     - Owns `(classHash, methodHash)` dispatch contract to native bindings.
     - Provides deterministic return-code contract for runtime callers.
    - `VersionedNativeDispatchTable` provides a stable symbol mapping plus
      ROMized/native table-version compatibility gate.
    - `DeterministicPowerNativeModule` provides deterministic host-side
      implementations for `wioe5.system.Power` native methods and dispatch
      handlers for power-native indexes.
    - `DeterministicPeripheralNativeModule` provides deterministic host-side
      implementations for GPIO/I2C/UART native methods with standardized
      failure codes and dispatch handlers for peripheral-native indexes.

5. **RuntimeModuleRegistry**
   - Owns immutable runtime wiring and integration-point validation.
   - Enforces null-safe startup boundary so the runtime fails early and explicitly if a module is missing.

## Integration Point Rules

- Module wiring is immutable after registry construction.
- Modules communicate only via declared interfaces, preserving static architecture boundaries.
- Failure to provide a required module is rejected during initialization with explicit error messages.
