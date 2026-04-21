# Runtime Stability Soak Model (E1-S5)

This document defines the deterministic long-running soak profile used to validate runtime stability in host-side tests.

## Goal

Validate that the runtime core can execute a 24-hour equivalent cooperative loop without crashes, memory corruption, or unbounded GC behavior.

## Soak Profile

- Test: `wioe5.runtime.RuntimeStabilitySoakTest`
- Simulated runtime window: `24h` (`86,400,000 ms`)
- Loop cadence: `1,000 ms` per loop iteration
- Iterations: `86,400`

Each iteration performs:

1. Push deterministic frame with bounded slots.
2. Allocate rooted and unrooted objects.
3. Run mark-sweep while roots are active (garbage must be reclaimed, roots preserved).
4. Pop frame and run mark-sweep again (all objects reclaimed).

## Deterministic Acceptance Invariants

- No runtime call returns an error in the soak loop.
- Frame depth returns to zero every iteration (`no stack corruption`).
- Live objects are bounded while roots are active and return to zero after frame pop.
- Used heap bytes and bump pointer return to zero after post-pop collection.
- Collection pause ticks remain bounded by `2 * maxObjects`.
- Sweep width remains fixed at `maxObjects`.

## Constraint Alignment

- Uses fixed-capacity frame and heap modules only.
- Avoids dynamic resizing/unbounded allocations.
- Uses deterministic operation-count pause metrics (not wall-clock timing) for reproducible validation.
