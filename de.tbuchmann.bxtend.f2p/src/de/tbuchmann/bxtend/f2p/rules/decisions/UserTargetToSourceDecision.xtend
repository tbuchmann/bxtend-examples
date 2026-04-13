/**
 * Interactive Swing-dialog implementation of {@link TargetToSourceDecision} that asks
 * the user to make all backward-transformation (Persons → Families) decisions at
 * runtime.
 *
 * <p>Before each transformation pass, a dialog is shown that lets the user configure the
 * following settings once for the entire pass:
 * <ul>
 *   <li>Whether members should always be placed in a new Family.</li>
 *   <li>Whether the parent slot should be preferred over child slots.</li>
 *   <li>Whether empty Families should be deleted after member removals.</li>
 * </ul>
 * In addition, whenever a Person is created and matches multiple families by name, the
 * user is presented with a list of candidates and can choose the target Family
 * interactively.
 *
 * <p>This implementation is intended for demonstration and interactive tool scenarios.
 * For automated tests use {@link DefaultTargetToSourceDecision} or
 * {@link ConfigurableTargetToSourceDecision} instead.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions

import Families.Family
import Families.FamilyMember
import Persons.Male
import Persons.Person
import java.util.List
import javax.swing.JOptionPane

class UserTargetToSourceDecision implements TargetToSourceDecision {

	/** {@code true} when the user chose to always create new families this pass. */
	boolean alwaysNewFamily = false
	/** {@code true} when the user chose to prefer the parent slot this pass. */
	boolean preferParent = false
	/** {@code true} when the user chose to delete empty families this pass. */
	boolean deleteEmptyFamilies = false

	/**
	 * Shows a configuration dialog so the user can set the pass-level policy flags.
	 *
	 * @return {@code true} if the user confirmed the dialog, {@code false} if cancelled
	 */
	override init() {
		val options = #["Yes", "No"]

		alwaysNewFamily = JOptionPane.showOptionDialog(null, "Always create a new family?",
			"Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
			options, options.get(0)) == 0

		preferParent = JOptionPane.showOptionDialog(null, "Prefer parent role?",
			"Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
			options, options.get(0)) == 0

		deleteEmptyFamilies = JOptionPane.showOptionDialog(null, "Delete empty families?",
			"Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
			options, options.get(0)) == 0
		true
	}

	/**
	 * Selects a target Family according to the user-configured policy.
	 *
	 * <p>If {@code alwaysNewFamily} is set, or if {@code families} is empty, returns
	 * {@code null} to create a new Family.  If there is exactly one candidate it is
	 * returned immediately.  If there are multiple candidates and {@code preferParent} is
	 * set, the first family with a free parent slot is preferred; if none is free, or
	 * {@code preferParent} is not set, the user is asked to choose from a dialog.
	 *
	 * @param families     candidate families whose name matches the person's family name
	 * @param person       the Person being transformed back
	 * @param actualFamily the Family currently containing the FamilyMember (unused)
	 * @return the chosen Family or {@code null} to create a new one
	 */
	override getFamily(List<Family> families, Person person, Family actualFamily) {
		if (alwaysNewFamily || families.empty) return null
		if (families.size == 1) return families.get(0)
		if (preferParent) {
			val fam = if (person instanceof Male) families.findFirst[f|f.father === null]
					  else families.findFirst[f|f.mother === null]
			if (fam !== null) return fam
		}
		// Multiple matching families – let the user pick one
		val familyNames = families.map[f | f.name].toList
		familyNames.add("Create new family")
		val choice = JOptionPane.showInputDialog(null,
			"Choose the family for " + person.name + ":",
			"Select family", JOptionPane.QUESTION_MESSAGE, null,
			familyNames.toArray, familyNames.get(0)) as String
		if (choice === null || choice == "Create new family") return null
		return families.findFirst[f|f.name == choice]
	}

	/**
	 * Decides whether the member should occupy the parent slot.
	 *
	 * <p>Returns {@code false} when {@code preferParent} is not set.  Otherwise returns
	 * {@code true} only when the relevant parent slot in {@code family} is still vacant.
	 *
	 * @param person the Person being placed
	 * @param family the target Family
	 * @return {@code true} if the member should be placed as a parent
	 */
	override setAsParent(Person person, Family family) {
		if (!preferParent) return false
		if (person instanceof Male) family.father === null
		else family.mother === null
	}

	/**
	 * Returns the user-configured value of the delete-empty-families flag.
	 *
	 * @param family     the now-empty Family (unused)
	 * @param lastMember the last removed FamilyMember (unused)
	 * @return the configured flag value
	 */
	override deleteEmptyFamily(Family family, FamilyMember lastMember) {
		deleteEmptyFamilies
	}

	/**
	 * No-op hook; this implementation maintains no additional bookkeeping.
	 *
	 * @param person the newly placed Person (unused)
	 * @param family the newly created Family (unused)
	 */
	override linkPersonToFamily(Person person, Family family) {}

	/**
	 * Returns {@code -1} so the framework supplies all matching families for potential
	 * interactive selection.
	 *
	 * @return {@code -1}
	 */
	override getFamilyListSize() { -1 }
}