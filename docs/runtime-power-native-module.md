# Runtime Power Native Module (E2-S2)

This document defines the deterministic host-side behavior for
`wioe5.system.Power` natives implemented by `DeterministicPowerNativeModule`.

## Goals

- Provide a deterministic implementation seam for all `Power` natives.
- Enforce explicit return-code behavior for invalid arguments and unsafe ranges.
- Preserve runtime safety invariants expected by constrained Wio-E5 operation.

## Implemented Native Behaviors

- `deepSleep(int ms)`
  - Validates non-negative sleep duration.
  - Simulates STOP2 sleep entry/exit with explicit wake clock restore signal.
  - Advances `millis()` by `ms` and tracks call/duration metrics.
- `readBatteryMV()`
  - Returns configured battery millivolts.
  - Configurable range is constrained to `1800..5000 mV`.
- `getResetReason()`
  - Returns current reset reason code.
- `kickWatchdog()`
  - Increments deterministic kick counter and returns success.
- `millis()`
  - Returns 32-bit monotonic runtime tick value (`int` wrap behavior preserved).
- `delayMicros(int us)`
  - Validates non-negative delay.
  - Accumulates microseconds and advances `millis()` when crossing millisecond boundaries.

## Dispatch Integration

`createDefaultDispatchHandlers()` returns a full handler array compatible with
`VersionedNativeDispatchTable.defaultBindingCount()`.

- Power native indexes (`0..5`) route to concrete `DeterministicPowerNativeModule` behavior.
- Non-power entries intentionally return `ERROR_SYMBOL_NOT_FOUND` until their
  stories are implemented.

## Result Codes

- `0`: success
- `-1`: invalid argument
- `-2`: battery millivolts out of allowed range
- `-3`: deep sleep duration out of range
- `-4`: microsecond delay out of range

## Determinism and Safety Notes

- No dynamic allocations are performed in hot paths.
- Failure paths are explicit, bounded, and return-code-based.
- Simulated wake restore state is observable for test verification of
  deep-sleep recovery semantics.
