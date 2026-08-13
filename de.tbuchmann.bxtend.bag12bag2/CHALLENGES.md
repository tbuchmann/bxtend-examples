# Challenges: Bag1 ↔ Bag2

## The challenge

Bag1 represents multiplicity the naive way — five occurrences of `"Beer"` are five
separate `Element` objects. Bag2 compresses this into one `Element(value="Beer",
multiplicity=5)`. This is a genuinely **non-bijective, many-to-one** synchronisation
problem, not a 1:1 element mapping:

- **Forward** must *group* Bag1 elements by value and compute the resulting
  multiplicity.
- **Backward** must *expand* each Bag2 entry back into the right number of individual
  Bag1 elements.
- **Incrementally**, a change to one member of a group (an insertion, a deletion, a
  value edit) must be reflected in the group's multiplicity without disturbing the
  identities of the other members or unrelated groups.

## How BXtend solved it

- **A dedicated many-to-one correspondence type (`MultiElem`)**, distinct from the
  ordinary 1:1 `BasicElem` used elsewhere, holds a *list* of source elements against a
  single target element — the structural backbone that makes the compression
  relationship representable at all.
- **Value and multiplicity are tracked independently**, each with its own snapshot
  (`corrToName` for value, a multiplicity snapshot for count), resolved via
  independent push/pull, since a concurrent edit can touch either without touching the
  other.
- **Deletion of a group defers to the generic correspondence cleanup**: rather than
  special-casing "remove all members when a group disappears," a `MultiElem` whose
  `targetElement` becomes `null` is left to the existing orphan-sweep, which already
  removes all of a dead correspondence's `sourceElements` in one place.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

- Two spots where a manual element-creation loop (outside the standard
  `getOrCreate*Elem` helpers) created new elements without registering them in the
  shared `elementsToCorr` index — invisible until `synch()` needed to look one up.
- A missing null-guard in the deletion cleanup, the same class of NPE found in several
  other examples.
- The subtlest bug: `synch()` initially *resurrected* a group whose target had been
  concurrently deleted, because a concurrent source-side addition to that same group
  was (wrongly) treated as reviving it. The real fixture showed deletion should win —
  the orphaned member is left attached to the dead correspondence and swept up by
  existing cleanup, while a genuinely new addition gets its *own* fresh correspondence
  via value matching rather than being merged into the dying one.

Two smaller issues remain open and documented rather than guessed at — see
[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).
