# Runtime Native Dispatch Table (E2-S1)

This document defines the deterministic native symbol mapping contract between
ROMized Java artifacts and the runtime native binding layer.

## Goals

- Stable `(classHash, methodHash) -> nativeIndex` mapping for all current
  `static native` APIs.
- Explicit ROMized-native-table version compatibility checks before dispatch.
- Deterministic, bounded dispatch behavior with explicit negative error codes.

## Version Compatibility Contract

`VersionedNativeDispatchTable` enforces:

- runtime table version (`runtimeNativeTableVersion`)
- minimum ROMized version accepted
- maximum ROMized version accepted

Dispatch is blocked until `verifyCompatibility(romizedVersion)` succeeds.

### Result Codes

- `0`: success
- `-1`: invalid argument
- `-2`: incompatible native table version
- `-3`: symbol not found
- `-5`: native index out of range
- any other negative value: propagated native handler error code

## Stable Symbol Mapping

The default table includes all currently defined Java native APIs:

- `wioe5.system.Power` (6 methods)
- `wioe5.io.GPIO` (4 methods)
- `wioe5.lora.LoRaWAN` (11 methods)
- `wioe5.bus.I2C` (5 methods)
- `wioe5.bus.UART` (6 methods)
- `wioe5.storage.NVConfig` (2 methods)

Total default bindings: **34**.

Native indexes are explicit and contiguous (`0..33`) to support compact
romized metadata and static dispatch table generation in the future C runtime.
