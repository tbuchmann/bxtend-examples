# Challenges: Gantt ↔ CPM

## The challenge

Gantt and CPM model the same scheduling information with incompatible node/edge
structure:

- In **Gantt**, activities are nodes and dependencies are separate edge objects
  connecting them.
- In **CPM**, everything is either an *Event* (node) or an *Activity* (arc) — there is
  no separate dependency object.

This means a single `gantt.Activity` must expand into **three** CPM elements (one
`cpm.Activity` arc plus two bounding `cpm.Event`s), and a single `gantt.Dependency`
collapses into **one** `cpm.Activity` arc that has to *share* its endpoint events with
the CPM activities of its predecessor and successor — with the specific pair of shared
events depending on the dependency's `DependencyType` (`StartStart`/`StartEnd`/
`EndStart`/`EndEnd`). Since both Gantt activities and Gantt dependencies map to the
same target type (`cpm.Activity`), the backward direction also needs a way to tell
which kind of CPM arc it's looking at.

## How BXtend solved it

- **Atomic multi-element creation:** `Activity2Activity` overrides the target-element
  factory to create the arc *and* its two events together in one correspondence, so
  the three-element bundle is never left half-built.
- **Endpoint sharing via `DependencyType`:** `Dependency2Activity` looks up the
  predecessor's and successor's already-created events through the correspondence
  model and wires the new arc's `sourceEvent`/`targetEvent` according to the
  dependency type table, rather than creating fresh events for dependencies.
- **Disambiguation by naming convention:** CPM activities derived from Gantt
  activities keep their original name; those derived from dependencies get an
  arrow-encoded name (`"Design->Build"`). This avoids needing a typed correspondence
  subclass just to tell the two cases apart on the way back.
- **Incremental sync (`synch()`):** `duration` is independently mutable from the
  activity's identity/name key, so it gets its own snapshot map (`corrToDuration`)
  rather than being lumped in with the name-based push/pull decision.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

- `Activity2Activity`'s custom multi-element factory override didn't register the new
  `Event`s in the shared `elementsToCorr` index — a pre-existing gap in the original
  (non-incremental) code that only surfaced once `synch()` needed to look up a plain
  Event's correspondence for the first time, producing an NPE.
- The initial `synch()` design conflated "never synchronised yet" (`null` snapshot)
  with "target changed" for the duration attribute; fixed by making the *push* branch
  the unconditional default whenever the snapshot is `null`.
