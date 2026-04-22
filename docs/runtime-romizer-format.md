# Deterministic ROMizer Artifact Format

This document defines the host-side deterministic ROMizer artifact format used by `DeterministicRomizer`.

## Goals

- Produce one static binary image for runtime loading with no dynamic class parsing.
- Keep section boundaries explicit and fixed-order for deterministic loader behavior.
- Fail fast on malformed/corrupt artifacts before execution begins.

## Binary Layout

All fields are big-endian.

### Header (44 bytes)

1. `magic` (`u32`) = `0x57494F35` (`"WIO5"`)
2. `formatVersion` (`u16`) = `1`
3. `reserved` (`u16`) = `0`
4. `classTableOffset` (`u32`)
5. `classTableLength` (`u32`)
6. `bytecodePoolOffset` (`u32`)
7. `bytecodePoolLength` (`u32`)
8. `nativeTableOffset` (`u32`)
9. `nativeTableLength` (`u32`)
10. `staticDataOffset` (`u32`)
11. `staticDataLength` (`u32`)
12. `totalLength` (`u32`)

Sections are contiguous in this strict order:

`header -> classTable -> bytecodePool -> nativeTable -> staticData`

### Class Table Section

- `classCount` (`u16`)
- Repeated classes sorted by `classHash` ascending:
  - `classHash` (`u32`)
  - `methodCount` (`u16`)
  - Repeated methods sorted by `methodHash` ascending:
    - `methodHash` (`u32`)
    - `methodFlag` (`u8`): `0=bytecode`, `1=native`
    - `bytecodeOffset` (`u32`): offset inside bytecode pool (`0xFFFFFFFF` for native methods)
    - `bytecodeLength` (`u16`): `>0` for bytecode methods (`0` for native methods)
    - `nativeIndex` (`u16`): native function index (`0xFFFF` for bytecode methods)

### Bytecode Pool Section

Raw concatenated method bytecode payloads.

### Native Table Section

- `romizedNativeTableVersion` (`u16`)
- `bindingCount` (`u16`)
- Repeated bindings sorted by `(classHash, methodHash)`:
  - `classHash` (`u32`)
  - `methodHash` (`u32`)
  - `nativeIndex` (`u16`)

### Static Data Section

- `staticFieldCount` (`u16`)
- Repeated entries sorted by `(classHash, fieldHash)`:
  - `classHash` (`u32`)
  - `fieldHash` (`u32`)
  - `initialValue` (`u32`)

## Determinism and Bounds

`DeterministicRomizer` enforces fixed limits and deterministic sorting:

- max classes: 64
- max methods per class: 64
- max total methods: 256
- max bytecode pool: 32 KB
- max native bindings: 256
- max static fields: 256

## Validation Tooling

`DeterministicRomizer.validate(...)` verifies:

- header magic/version/contiguous layout
- section bounds and lengths
- class/method sorting and duplicate rejection
- bytecode method offset/length bounds
- native method linkage to native table entries
- static initializer sorting and duplicate rejection

Validation is deterministic and returns explicit error codes for loader-safe fail-fast behavior.
