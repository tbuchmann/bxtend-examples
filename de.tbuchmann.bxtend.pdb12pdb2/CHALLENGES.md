# Challenges: PDB1 ↔ PDB2

## The challenge

PDB2 stores a person's identity as a single `name` string; PDB1 splits it into
`firstName`/`lastName`. Forward propagation (concatenation) is deterministic, but the
**backward split is inherently ambiguous**: a name like `"Konrad Hermann Joseph
Adenauer"` could be split at any of its three spaces, and there is no way to derive
the "correct" split from the data alone.

A second, easy-to-miss challenge only surfaced once concurrent-edit tests were run for
real: `birthday`, `placeOfBirth`, and `id` are **independent of the name** and can
each change on their own, without the name changing at all — a naive design that only
tracks the name as the "did anything change" signal silently drops those edits.

## How BXtend solved it

- **The split ambiguity is externalised** into a pluggable `TargetToSourceDecision`
  strategy (mirroring `f2p`'s decision-strategy pattern), configurable per split
  position (e.g. first-space vs. last-space), rather than hard-coded into the rule.
- **Independent attributes get independent snapshots.** `birthday`, `placeOfBirth`,
  and `id` are each tracked in their own snapshot map (`corrToBirthday`,
  `corrToPlaceOfBirth`, `corrToId`) and resolved separately from the name-key
  push/pull decision, so a concurrent edit to one doesn't get silently absorbed or
  ignored by whatever the name happens to be doing.
- **Rule ordering matters:** `Database2Database` always runs before `Person2Person` so
  the parent container correspondence exists before persons try to resolve their
  containing database through it.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

- A missing null-guard in the deletion cleanup caused an NPE under a *combined*
  parent+child deletion, where cascading EMF cross-reference cleanup could null out a
  sibling correspondence's element before the same cleanup batch reached it.
- `synch()` initially gated **all** attribute reconciliation on the name key changing
  — a target-side `placeOfBirth` edit with an unchanged name was silently dropped.
  Fixed by giving the independent attributes their own snapshot-based push/pull,
  decoupled from the name.
- The subtlest bug: deciding *when* to re-derive the ambiguous name split. Neither
  "only when the name text changed" nor "unconditionally on every call" survived real
  testing — three independent fixtures showed the actual rule is "re-split whenever
  *anything* about this person changed since the last backward call" (verified by a
  person touched only via `id`, which still needed a fresh split under a newly-changed
  decision config, while a completely untouched person had to keep its old split even
  after the config changed).
