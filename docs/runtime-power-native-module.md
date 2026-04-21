# Runtime Power Native Module (E2-S2)

This document defines the deterministic host-side implementation contract for
`wioe5.system.Power` native handlers.

## Goals

- Provide explicit deterministic behavior for all `Power` native methods.
- Preserve bounded-state operation suitable for constrained targets.
- Expose clear negative-path error codes for argument/range failures.

## Implemented Surface

`DeterministicPowerNativeModule` models:

- `deepSleep(int ms)`
- `readBatteryMV()`
- `getResetReason()`
- `kickWatchdog()`
- `millis()`
- `delayMicros(int us)`

## Deterministic Behavior Contract

- `deepSleep(ms)`:
  - increments internal sleep count
  - increments wake clock-restore count (STOP2 wake model)
  - advances internal runtime millisecond clock by `ms`
- `delayMicros(us)`:
  - tracks sub-millisecond carry (`0..999`)
  - advances runtime millisecond clock by `us / 1000`
- `kickWatchdog()` increments a watchdog kick counter.
- battery and reset-reason values are readable and can be set in deterministic tests.

## Error Model

- `0`: success
- `-1`: invalid argument
- `-2`: out-of-range argument/value

## Dispatch Integration

`installInto(NativeHandler[])` binds the six `Power` handlers into the existing
`VersionedNativeDispatchTable` index range (`0..5`), preserving the stable
symbol-to-index mapping introduced in E2-S1.
