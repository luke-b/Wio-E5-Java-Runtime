# Wio-E5 Java Runtime

Embedded Java runtime foundation for the **Seeed Studio Wio-E5 (STM32WLE5JC)**.

This project defines a constrained, deterministic runtime model intended for ROMized Java applications running on a low-power LoRaWAN device.

## Why this repository exists

The long-term goal is a production-ready embedded Java runtime that can:

- run a bounded CLDC-style bytecode subset
- bridge Java APIs to native STM32/LoRaWAN functionality
- support deterministic behavior under strict flash/SRAM limits

## Current implementation status

Today, the repository includes:

1. **Embedded Java API surface (native stubs)** for:
   - `wioe5.system.Power`
   - `wioe5.io.GPIO`
   - `wioe5.lora.LoRaWAN`
   - `wioe5.bus.I2C`
   - `wioe5.bus.UART`
   - `wioe5.storage.NVConfig`
2. **Runtime core abstractions**:
   - interpreter, heap manager, frame stack, native dispatch interfaces
   - immutable runtime module registry
3. **Deterministic runtime components**:
   - `BytecodeInterpreterModule` with explicit supported-opcode subset and fail-fast error model
   - `DeterministicFrameStackModule` with fixed-capacity frame/local/operand bounds
4. **Host-side deterministic tests** using Java `main` harnesses.

## Repository layout

```text
docs/
  architecture.md
  runtime-module-boundaries.md
  runtime-bytecode-subset.md
  runtime-frame-stack-model.md
  runtime-heap-gc-model.md
  runtime-native-dispatch-table.md
  runtime-power-native-module.md
  runtime-stability-soak.md
src/main/java/wioe5/
  bus/      io/      lora/      runtime/      storage/      system/
src/test/java/wioe5/runtime/
  RuntimeModuleRegistryTest.java
  BytecodeInterpreterModuleTest.java
  DeterministicFrameStackModuleTest.java
  DeterministicPowerNativeModuleTest.java
  VersionedNativeDispatchTableTest.java
plan/
  production-readiness-implementation-plan.md
  progress-tracking.md
  implementation-notes.md
```

## Run the current validation suite

This repository currently uses direct `javac`/`java` commands (no dedicated build tool config yet):

```bash
rm -rf build/test-classes
mkdir -p build/test-classes
javac -d build/test-classes $(find src/main/java -name '*.java' | sort) $(find src/test/java -name '*.java' | sort)
java -cp build/test-classes wioe5.runtime.RuntimeModuleRegistryTest
java -cp build/test-classes wioe5.runtime.BytecodeInterpreterModuleTest
java -cp build/test-classes wioe5.runtime.DeterministicFrameStackModuleTest
java -cp build/test-classes wioe5.runtime.DeterministicHeapManagerModuleTest
java -cp build/test-classes wioe5.runtime.DeterministicPowerNativeModuleTest
java -cp build/test-classes wioe5.runtime.VersionedNativeDispatchTableTest
java -cp build/test-classes wioe5.runtime.RuntimeStabilitySoakTest
```

## Key documentation

- Architecture and system context: `docs/architecture.md`
- Runtime module contracts: `docs/runtime-module-boundaries.md`
- Supported bytecode subset and interpreter errors: `docs/runtime-bytecode-subset.md`
- Frame/stack deterministic model: `docs/runtime-frame-stack-model.md`
- Heap and GC deterministic model: `docs/runtime-heap-gc-model.md`
- Native binding dispatch table and compatibility contract: `docs/runtime-native-dispatch-table.md`
- Deterministic `Power` native behavior and dispatch handlers: `docs/runtime-power-native-module.md`
- Runtime long-loop soak profile: `docs/runtime-stability-soak.md`
- Delivery roadmap and status tracking: `plan/production-readiness-implementation-plan.md`, `plan/progress-tracking.md`

## Notes

- `build/` is ignored and should not be committed.
- APIs under `wioe5.*` are designed as `static native` boundaries; hardware behavior is implemented in native runtime layers.
