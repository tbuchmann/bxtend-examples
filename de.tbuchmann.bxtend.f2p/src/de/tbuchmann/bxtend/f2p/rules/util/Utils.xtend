/**
 * Utility functions shared across the Families-to-Persons BXtend transformation rules.
 *
 * <p>This class is declared as a BXtend extension class so that its static methods are
 * accessible as extension methods throughout the rule classes without requiring explicit
 * import of individual methods.
 *
 * <p>All methods are purely functional (no side effects) and operate on EMF model
 * objects only.
 */
package de.tbuchmann.bxtend.f2p.rules.util

import Families.Family
import Families.FamilyMember
import Families.FamilyRegister
import Persons.Female
import Persons.Male
import Persons.Person
import Persons.PersonRegister
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr

class Utils {

	/**
	 * Derives the full name of a {@link FamilyMember} in the form
	 * {@code "<firstName> <familyName>"}.
	 *
	 * <p>The family name is read from the containing {@link Family}.  If the member is
	 * not yet attached to a family the family-name portion will be {@code null}.
	 *
	 * @param member the FamilyMember whose full name is to be computed
	 * @return the full name string, e.g. {@code "Tom Simpson"}
	 */
	def static String getFullName(FamilyMember member) {
		val firstName = member.name
		val familyName = member.familyOfMember?.name
		return firstName + " " + familyName
	}

	/**
	 * Returns the {@link Family} that directly contains {@code member}, regardless of
	 * which role the member occupies (father, mother, son, or daughter).
	 *
	 * <p>The method inspects all four inverse references and returns the first non-null
	 * result, or {@code null} when the member is not contained in any family.
	 *
	 * @param member the FamilyMember to look up
	 * @return the containing Family, or {@code null}
	 */
	def static Family getFamilyOfMember(FamilyMember member) {
		if (member.fatherInverse !== null) return member.fatherInverse
		if (member.motherInverse !== null) return member.motherInverse
		if (member.sonsInverse !== null) return member.sonsInverse
		if (member.daughtersInverse !== null) return member.daughtersInverse
		return null
	}

	/**
	 * Checks whether the source and target elements stored in a {@link Corr} correspondence
	 * are still consistent with each other.
	 *
	 * <p>Consistency requires that:
	 * <ol>
	 *   <li>The person's full name equals {@code "<familyName>, <firstName>"}.</li>
	 *   <li>The gender of the {@link Person} ({@link Male}/{@link Female}) matches the role
	 *       of the {@link FamilyMember} (father/son → Male; mother/daughter → Female).</li>
	 * </ol>
	 * The special case of a {@link FamilyRegister}–{@link PersonRegister} correspondence is
	 * always considered consistent and returns {@code true} immediately.
	 *
	 * @param corr the correspondence object to validate
	 * @return {@code true} if source and target are mutually consistent
	 */
	def static boolean matchFamilyMember2Person(Corr corr) {
		if(corr.sourceElement instanceof FamilyRegister && corr.targetElement instanceof PersonRegister) {
			return true
		}
		val f = corr.sourceElement as FamilyMember
		val family = f.eContainer() as Family
		val p = corr.targetElement as Person
		if (!p.name.equals(family.name + ", " + f.name)) {
			return false
		}
		val males = newArrayList
		males.addAll(family.sons)
		if (family.father !== null) {
			males.add(family.father)
		}
		if (males.contains(f)) {
			return (p instanceof Male)
		}
		val females = newArrayList
		females.addAll(family.daughters)
		if (family.mother !== null) {
			females.add(family.mother)
		}
		if (females.contains(f)) {
			return (p instanceof Female)
		}
		return false
	}
}