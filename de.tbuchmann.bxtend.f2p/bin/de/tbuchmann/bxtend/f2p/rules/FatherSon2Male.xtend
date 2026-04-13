/**
 * Concrete bidirectional transformation rule for the male-gender correspondence:
 * {@link Families.FamilyMember} in the roles <em>father</em> or <em>son</em>
 * ↔ {@link Persons.Male}.
 *
 * <p>All three transformation directions are implemented:
 * <ul>
 *   <li><b>Forward ({@link #sourceToTarget()}):</b> iterates over all fathers and sons
 *       in the Families model and creates or updates the corresponding {@link Persons.Male}
 *       elements in the Persons model.</li>
 *   <li><b>Backward ({@link #targetToSource()}):</b> iterates over all {@link Persons.Male}
 *       elements and transforms them back into {@link Families.FamilyMember} objects placed
 *       in the appropriate families.</li>
 *   <li><b>Synchronisation ({@link #synch()}):</b> reconciles concurrent changes in both
 *       models; members without a corr entry are matched to unmatched males by name,
 *       and any leftover males from the Persons side are removed.</li>
 * </ul>
 *
 * <p>Rule identifier: {@code "FatherSon2Male"}.
 */
package de.tbuchmann.bxtend.f2p.rules

import Families.Family
import Families.FamilyMember
import Persons.Male
import Persons.Person
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import java.util.ArrayList
import org.eclipse.emf.common.util.ECollections
import org.eclipse.emf.ecore.resource.Resource

class FatherSon2Male extends FamilyMember2Person {

	/**
	 * Constructs a new FatherSon2Male rule.
	 *
	 * @param src  the Families source model resource
	 * @param trgt the Persons target model resource
	 * @param corr the correspondence model resource
	 * @param dec  the strategy for resolving backward-transformation decisions
	 */
	new(Resource src, Resource trgt, Resource corr, TargetToSourceDecision dec) {
		super(src, trgt, corr, dec)
		ruleID = "FatherSon2Male"
	}
	
	/**
	 * Forward direction (Families → Persons): processes every father and son
	 * {@link Families.FamilyMember} in the source model and produces a corresponding
	 * {@link Persons.Male} element in the target model.
	 *
	 * <p>The male person is added to the {@link Persons.PersonRegister} that corresponds
	 * to the containing {@link Families.FamilyRegister}.
	 */
	override sourceToTarget() {		
		sourceModel.allContents.filter(typeof(Family)).forEach [ family |
			val males = ECollections.newBasicEList()
			males.addAll(family.sons)
			if (family.getFather() !== null)
				males.add(family.father)
			males.forEach [ member | member.addPerson("FatherSon2Male")
			]
		]
	}

	/**
	 * Backward direction (Persons → Families): processes every {@link Persons.Male}
	 * element in the target model and places a corresponding {@link Families.FamilyMember}
	 * into the appropriate {@link Families.Family} in the source model.
	 *
	 * <p>Delegation to {@link FamilyMember2Person#transformPerson} handles family
	 * selection, role assignment (father vs. son), and optional empty-family cleanup.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Male)).forEach [ p |
			val corr = p.getOrCreateCorrModelElement("FatherSon2Male");
			corr.transformPerson(p)
		]
	}
	
	/**
	 * Synchronisation direction: reconciles concurrent changes made to fathers/sons on
	 * the Families side and males on the Persons side.
	 *
	 * <p>Algorithm:
	 * <ol>
	 *   <li>Collects all unmatched (no-correspondence) {@link Persons.Male} objects.</li>
	 *   <li>For each father and son in the source model, calls
	 *       {@link FamilyMember2Person#synchFamilyMember}, which will either update an
	 *       existing match, link an unmatched Male, or create a new Male.</li>
	 *   <li>Any Males that are still unmatched after step 2 (i.e. they exist in the
	 *       Persons model but have no corresponding FamilyMember) are deleted.</li>
	 * </ol>
	 */
	override synch() {
		val fmList = sourceModel.allContents.filter(typeof(FamilyMember)).filter[
			fm | (fm.fatherInverse !== null || fm.sonsInverse !== null) 
		].toList
		val pList = new ArrayList<Person>()
		pList += targetModel.allContents.filter(typeof(Male)).toList		
					
		// establish correspondences, starting with familyRegisters
		// if the algorithms performs correctly, all FamilyRegisters
		// should have a corresponding partner in the Persons model
		fmList.forEach[
			it.synchFamilyMember(pList, "FatherSon2Male")		
		]
		
		// now check if there are unmatched persons left
		pList.forEach[
			if (it.corrModelElem === null) it.getOrCreateCorrModelElement("FatherSon2Male").transformPerson(it)
		]

	}
}