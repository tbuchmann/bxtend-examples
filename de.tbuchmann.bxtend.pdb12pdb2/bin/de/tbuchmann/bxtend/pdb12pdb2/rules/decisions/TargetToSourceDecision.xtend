package de.tbuchmann.bxtend.pdb12pdb2.rules.decisions

/**
 * Strategy interface that resolves the non-determinism of the backward (PDB2 → PDB1)
 * name-splitting step.
 *
 * <p>In PDB2, a person's full name is stored in a single {@code name} attribute
 * (e.g. {@code "Konrad Hermann Joseph Adenauer"}).  In PDB1, the name is split
 * across two attributes: {@code firstName} and {@code lastName}.  Because the same
 * full name can be split at different space positions, the transformation requires
 * an explicit decision about <em>where</em> to split.  Implementors of this interface
 * encapsulate that decision and are injected into the rule engine via
 * {@link de.tbuchmann.bxtend.pdb12pdb2.rules.Elem2Elem#configure(TargetToSourceDecision)}.</p>
 *
 * <p>The only built-in implementation is
 * {@link ConfigurableTargetToSourceDecision}, which supports splitting at any
 * n-th space (or the last space when {@code spacePosition < 0}).</p>
 *
 * @see ConfigurableTargetToSourceDecision
 */
interface TargetToSourceDecision {
	/**
	 * Returns the firstName that should be set to the person, with fullName name.
	 * @param name The name from which the lastName should be constructed.
	 * @return the firstName that should be set to the person, with fullName name.
	 */
	def String getFirstName(String name);

	/**
	 * Returns the lastName that should be set to the person, with fullName name.
	 * @param name The name from which the lastName should be constructed.
	 * @return the lastName that should be set to the person, with fullName name.
	 */
	def String getLastName(String name);
}