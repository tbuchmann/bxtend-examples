/**
 * Default implementation of {@link TargetToSourceDecision} that uses deterministic,
 * rule-based behaviour for all backward-transformation decisions.
 *
 * <p>Policy summary:
 * <ul>
 *   <li><b>Family selection:</b> always picks the first candidate family (if one exists).</li>
 *   <li><b>Parent vs. child:</b> assigns the member as parent (father/mother) when the
 *       parent slot of the chosen family is still free; otherwise adds as a child.</li>
 *   <li><b>Empty-family cleanup:</b> always deletes a family that has become empty.</li>
 *   <li><b>Candidate list size:</b> returns {@code 1} so the framework only passes in at
 *       most one candidate family.</li>
 * </ul>
 *
 * <p>This implementation requires no user interaction and is therefore suitable for
 * automated test scenarios and batch transformations.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions

import java.util.List
import Families.Family
import Persons.Person
import Persons.Male
import Families.FamilyMember

class DefaultTargetToSourceDecision implements TargetToSourceDecision {
	
	/**
	 * Returns the first family from {@code families} if the list is non-empty,
	 * or {@code null} to request creation of a new family.
	 *
	 * @param families    candidate families (at most one element due to
	 *                    {@link #getFamilyListSize()} returning {@code 1})
	 * @param person      the Person being transformed back (unused here)
	 * @param actualFamily the Family currently containing the member (unused here)
	 * @return {@code families.get(0)} or {@code null}
	 */
	override getFamily(List<Family> families, Person person, Family actualFamily) {
		if(families.size > 0) {
			return families.get(0)
		}
		return null;
	}
	
	/**
	 * Returns {@code true} if the parent slot (father for {@link Male}, mother otherwise)
	 * in {@code family} is still vacant.
	 *
	 * @param person the Person being placed
	 * @param family the target Family
	 * @return {@code true} when the parent position is free
	 */
	override setAsParent(Person person, Family family) {
		if(person instanceof Male) {
			return family.father === null
		} else {
			return family.mother === null
		}
	}
	
	/**
	 * Always returns {@code true}: empty families are always removed.
	 *
	 * @param family     the empty Family (unused)
	 * @param lastMember the last removed FamilyMember (unused)
	 * @return {@code true}
	 */
	override deleteEmptyFamily(Family family, FamilyMember lastMember) {
		return true;
	}
	
	/**
	 * No-op initialisation; always signals that the transformation should proceed.
	 *
	 * @return {@code true}
	 */
	override init() {
		true
	}
	
	/**
	 * No-op hook; this implementation maintains no additional bookkeeping.
	 *
	 * @param person the newly placed Person (unused)
	 * @param family the newly created Family (unused)
	 */
	override linkPersonToFamily(Person person, Family family) {
	}
	
	/**
	 * Signals that the framework should pass at most one candidate family to
	 * {@link #getFamily}.
	 *
	 * @return {@code 1}
	 */
	override getFamilyListSize() {
		1
	}
	
}