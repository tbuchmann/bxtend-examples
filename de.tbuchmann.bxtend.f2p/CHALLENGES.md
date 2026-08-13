# Challenges: Families ↔ Persons

## The challenge

The backward direction (Persons → Families) is **not injective**: a `Male` named
`"Simpson, Homer"` could rejoin the `Simpson` family as either the father or a son,
several `Family` objects might share the same surname, and it's unclear whether an
emptied-out `Family` should be deleted. None of these three decisions are derivable
from the data alone — they depend on intent that the model itself doesn't encode.

Name and gender encoding add a smaller, but still real, wrinkle: `FamilyMember` only
stores a first name, while `Person` stores `"<surname>, <firstname>"` plus a concrete
subtype (`Male`/`Female`) standing in for a `gender` attribute that doesn't exist as
such.

## How BXtend solved it

- **Structural mapping** is handled by ordinary correspondence-based rules
  (`Register2Register`, `FatherSon2Male`, `MotherDaughter2Female`) — deterministic in
  the forward direction, since Families → Persons has no ambiguity.
- **The ambiguity is pushed out of the rules entirely.** Rather than hard-coding a
  policy for family assignment, parent-vs-child role, or empty-family deletion, BXtend
  externalises these choices into a pluggable `TargetToSourceDecision` strategy,
  injected into every rule via `configure(...)`. The default
  (`DefaultTargetToSourceDecision`) picks a reasonable default; a
  `ConfigurableTargetToSourceDecision`/user-supplied strategy can override it per call.
- This keeps the *correspondence and propagation* logic (which is mechanical and
  reusable) cleanly separated from the *decision* logic (which is inherently
  benchmark-specific and non-deterministic) — the pattern every other example in this
  repository with a genuine backward ambiguity (`pdb12pdb2`) later reused.

This project was the original reference implementation: it already had the full
`synch()` (incremental/concurrent) direction before the rest of the repository's
examples were ported to match it.
