# Runtime Heap and GC Model (E1-S4)

This document defines the deterministic heap allocator and mark-sweep collector implemented by `DeterministicHeapManagerModule`.

## Allocation Model

- Heap is fixed-capacity and configured at construction time.
- Allocation uses a bump pointer and fixed metadata arrays (`maxObjects` object slots).
- No runtime resizing or unbounded structures are used.
- Allocation fails explicitly with `ERROR_HEAP_FULL` when capacity or slot limits are exceeded.

## Root Discovery Contract

- `FrameStackModule` exposes `gcRootCount()` and `gcRootAt(index)` for root enumeration.
- `DeterministicFrameStackModule` marks reference-bearing locals/operands explicitly via:
  - `setLocalReference(int index, int referenceHandle)`
  - `pushOperandReference(int referenceHandle)`
- Non-reference locals/operands are excluded from GC root traversal.

## Mark-Sweep Behavior

1. **Mark**
   - Mark bits are reset for all object slots.
   - Collector traverses frame roots and object-to-object references using a fixed traversal stack.
   - Only allocated handles are marked live.
2. **Sweep**
   - Unmarked objects are reclaimed and their metadata/reference slots are cleared.
   - Bump pointer is reset to the highest end offset among live objects (non-moving objects, no compaction).

## Deterministic Pause Metrics

- Every collection records deterministic operation-count metrics:
  - `lastCollectionMarkedCount()`
  - `lastCollectionSweepCount()`
  - `lastCollectionPauseTicks()` where `pauseTicks = markedCount + sweepCount`
  - `maxObservedCollectionPauseTicks()`
- Sweep width is always bounded by `maxObjects`, providing predictable pause bounds for constrained targets.
