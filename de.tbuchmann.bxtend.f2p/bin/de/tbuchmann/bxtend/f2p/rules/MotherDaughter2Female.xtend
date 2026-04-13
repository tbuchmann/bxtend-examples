/**
 * Concrete bidirectional transformation rule for the female-gender correspondence:
 * {@link Families.FamilyMember} in the roles <em>mother</em> or <em>daughter</em>
 * ↔ {@link Persons.Female}.
 *
 * <p>All three transformation directions are implemented:
 * <ul>
 *   <li><b>Forward ({@link #sourceToTarget()}):</b> iterates over all mothers and
 *       daughters in the Families model and creates or updates the corresponding
 *       {@link Persons.Female} elements in the Persons model.</li>
 *   <li><b>Backward ({@link #targetToSource()}):</b> iterates over all
 *       {@link Persons.Female} elements and transforms them back into
 *       {@link Families.FamilyMember} objects placed in the appropriate families.</li>
 *   <li><b>Synchronisation ({@link #synch()}):</b> reconciles concurrent changes in both
 *       models; members without a corr entry are matched to unmatched females by name,
 *       and any leftover females from the Persons side are removed.</li>
 * </ul>
 *
 * <p>This rule is the female-gender counterpart of {@link FatherSon2Male}.
 * Rule identifier: {@code "MotherDaughter2Female"}.
 */
package de.tbuchmann.bxtend.f2p.rules

import Families.Family
import Families.FamilyMember
import Persons.Female
import Persons.Person
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import java.util.ArrayList
import org.eclipse.emf.common.util.ECollections
import org.eclipse.emf.ecore.resource.Resource

class MotherDaughter2Female extends FamilyMember2Person {

	/**
	 * Constructs a new MotherDaughter2Female rule.
	 *
	 * @param src  the Families source model resource
	 * @param trgt the Persons target model resource
	 * @param corr the correspondence model resource
	 * @param dec  the strategy for resolving backward-transformation decisions
	 */
	new(Resource src, Resource trgt, Resource corr, TargetToSourceDecision dec) {
		super(src, trgt, corr, dec)
		ruleID = "MotherDaughter2Female"
	}

	/**
	 * Forward direction (Families → Persons): processes every mother and daughter
	 * {@link Families.FamilyMember} in the source model and produces a corresponding
	 * {@link Persons.Female} element in the target model.
	 *
	 * <p>The female person is added to the {@link Persons.PersonRegister} that corresponds
	 * to the containing {@link Families.FamilyRegister}.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Family)).forEach [ family |
			val females = ECollections.newBasicEList()
			females.addAll(family.daughters)
			if (family.mother !== null)
				females.add(family.mother)
			females.forEach [ member | member.addPerson("MotherDaughter2Female")

			]
		]
	}

	/**
	 * Backward direction (Persons → Families): processes every {@link Persons.Female}
	 * element in the target model and places a corresponding {@link Families.FamilyMember}
	 * into the appropriate {@link Families.Family} in the source model.
	 *
	 * <p>Delegation to {@link FamilyMember2Person#transformPerson} handles family
	 * selection, role assignment (mother vs. daughter), and optional empty-family cleanup.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Female)).forEach [ p |
			val corr = p.getOrCreateCorrModelElement("MotherDaughter2Female");
			corr.transformPerson(p)
		]
	}

	/**
	 * Synchronisation direction: reconciles concurrent changes made to mothers/daughters
	 * on the Families side and females on the Persons side.
	 *
	 * <p>Algorithm:
	 * <ol>
	 *   <li>Collects all unmatched (no-correspondence) {@link Persons.Female} objects.</li>
	 *   <li>For each mother and daughter in the source model, calls
	 *       {@link FamilyMember2Person#synchFamilyMember}, which will either update an
	 *       existing match, link an unmatched Female, or create a new Female.</li>
	 *   <li>Any Females that are still unmatched after step 2 (i.e. they exist in the
	 *       Persons model but have no corresponding FamilyMember) are processed via
	 *       {@link FamilyMember2Person#transformPerson}.</li>
	 * </ol>
	 */
	override synch() {
		val fmList = sourceModel.allContents.filter(typeof(FamilyMember)).filter[
			fm | (fm.motherInverse !== null || fm.daughtersInverse !== null) 
		].toList
		val pList = new ArrayList<Person>()
		pList += targetModel.allContents.filter(typeof(Female)).toList
				
		// establish correspondences, starting with familyRegisters
		// if the algorithms performs correctly, all FamilyRegisters
		// should have a corresponding partner in the Persons model
		fmList.forEach[			
			it.synchFamilyMember(pList, "MotherDaughter2Female")		
		]
		
		// now check if there are unmatched persons left
		pList.forEach[
			if (it.corrModelElem === null) it.getOrCreateCorrModelElement("MotherDaughter2Female").transformPerson(it)
		]

	}
}