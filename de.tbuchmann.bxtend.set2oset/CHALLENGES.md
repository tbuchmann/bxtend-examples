# Challenges: Set ↔ Ordered Set

## The challenge

The target metamodel adds information the source simply doesn't have: `MyOrderedSet`
elements form a doubly-linked list (`next`/`previous`, declared as an EMF `eOpposite`
pair), while `MySet` is a plain unordered containment collection. Two problems follow:

- **Non-determinism:** a source set `{A, B}` is equally well represented by `A→B` or
  `B→A` in the target — there's no "correct" order to derive.
- **Preservation vs. invention:** once an order exists in the target, the
  transformation must not disturb it on subsequent forward runs, but new elements
  still need to be inserted somewhere sensible.

## How BXtend solved it

- **Append-at-tail policy** resolves the non-determinism deterministically: a new
  source element is always linked in after the current tail of the existing list,
  leaving previously-established ordering untouched.
- **Backward propagation is intentionally lossy**, exactly like `pn2pnw`'s weight
  attribute — the ordering has no source-side counterpart, so it's simply not
  propagated back; only `value` and set membership are.
- **Manual deletion-time list repair:** because EMF's `eOpposite` machinery only keeps
  the two ends of a broken link consistent with each other, not with the rest of the
  list, a deleted element's predecessor and successor have to be manually re-linked
  around the gap before the element is removed.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

- **A genuine Xtend compile error**, not a logic bug: `synch()` needed a running "tail
  pointer" local variable, mutated across loop iterations while appending new
  elements. Xtend compiles a `.forEach[...]` closure to a native Java lambda, which —
  unlike Xtend's own closures elsewhere — requires captured locals to be effectively
  final, so reassigning the tail pointer inside a `forEach` closure simply didn't
  compile. Switching the loop to a plain `for (x : list) { ... }` loop (not a lambda)
  fixed it.
- **A snapshot-seeding gap:** the identity-key snapshot map (`corrToName`) used to
  decide push-vs-pull in `synch()` was only ever populated inside `synch()` itself,
  not in the plain `sourceToTarget()`/`targetToSource()` paths. A correspondence built
  purely by batch propagation therefore looked "never synchronised" to a later
  `synch()` call, which treated an *unchanged* source value as a fresh push and
  silently overwrote — and thereby reverted — a legitimate concurrent rename on the
  target side. Fixed by seeding the snapshot on every propagation path, not just
  `synch()`.
