/**
 * Bidirectional transformation rule that synchronises the top-level register elements:
 * {@link Families.FamilyRegister} (source) ↔ {@link Persons.PersonRegister} (target).
 *
 * <p>This rule is responsible for the root-level correspondence only.  It does <em>not</em>
 * recurse into child elements; that is handled by {@link FamilyMember2Person} and its
 * subclasses.
 *
 * <p>All three transformation directions are supported:
 * <ul>
 *   <li>{@link #sourceToTarget()} – ensures a {@link Persons.PersonRegister} exists and
 *       is linked to the {@link Families.FamilyRegister} via a correspondence.</li>
 *   <li>{@link #targetToSource()} – ensures a {@link Families.FamilyRegister} exists and
 *       is linked to the {@link Persons.PersonRegister} via a correspondence.</li>
 *   <li>{@link #synch()} – ensures that both registers exist and are cross-linked.</li>
 * </ul>
 */
package de.tbuchmann.bxtend.f2p.rules

import Families.FamiliesPackage
import Families.FamilyRegister
import Persons.PersonRegister
import Persons.PersonsPackage
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import org.eclipse.emf.ecore.resource.Resource

class Register2Register extends Elem2Elem {

	/**
	 * Constructs a new Register2Register rule.
	 *
	 * @param src  the Families source model resource
	 * @param trgt the Persons target model resource
	 * @param corr the correspondence model resource
	 * @param dec  the strategy for resolving backward-transformation decisions
	 */
	new(Resource src, Resource trgt, Resource corr, TargetToSourceDecision dec) {
		super(src, trgt, corr, dec)
		ruleID = "Register2Register"
	}
	
	/**
	 * Forward direction: Families → Persons.
	 *
	 * <p>Reads the {@link FamilyRegister} from the source model, creates (or reuses) a
	 * {@link PersonRegister} in the target model, and establishes a correspondence
	 * between the two root objects.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(FamilyRegister))
		.forEach[ c |
				val corrTarget = c.getOrCreateCorrModelElement("Register2Register")
				val targetElement = corrTarget.getOrCreateTargetElem(PersonsPackage.eINSTANCE.personRegister) as PersonRegister
				if(!targetModel.contents.contains(targetElement))
					targetModel.contents.add(targetElement)
		]
	}
	
	/**
	 * Backward direction: Persons → Families.
	 *
	 * <p>Reads the {@link PersonRegister} from the target model, creates (or reuses) a
	 * {@link FamilyRegister} in the source model, and establishes a correspondence
	 * between the two root objects.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(PersonRegister))
		.forEach[ c |
			val corrTarget = c.getOrCreateCorrModelElement("Register2Register")
			val sourceElement = corrTarget.getOrCreateSourceElem(FamiliesPackage.eINSTANCE.familyRegister) as FamilyRegister
			if(!sourceModel.contents.contains(sourceElement))
				sourceModel.contents.add(sourceElement)
		]
	}
	
	/**
	 * Synchronisation direction: concurrent edits in both models.
	 *
	 * <p>Ensures that both a {@link FamilyRegister} and a {@link PersonRegister} exist
	 * and that they are mutually linked via a correspondence entry.
	 */
	override synch() {
		val famRegList = sourceModel.allContents.filter(typeof(FamilyRegister)).toList
		val persRegList = targetModel.allContents.filter(typeof(PersonRegister)).toList
		
		// establish correspondences, starting with familyRegisters
		// if the algorithms performs correctly, all FamilyRegisters
		// should have a corresponding partner in the Persons model
		for (fr : famRegList) {
			val corr = fr.getOrCreateCorrModelElement("Register2Register")
			val target = corr.targetElement
			if (target !== null) {
				persRegList.remove(target)
				
			} else {
				// pick one from persRegList
				corr.targetElement = persRegList.head
				persRegList.remove(corr.targetElement)
			}
		}
		
		// now check if there are unmatched person registers left
		if (persRegList.size > 0) {
			for (pr : persRegList) {
				val corr = pr.getOrCreateCorrModelElement("Register2Register")
				val sourceElement = corr.getOrCreateSourceElem(FamiliesPackage.eINSTANCE.familyRegister) as FamilyRegister
				if(!sourceModel.contents.contains(sourceElement))
					sourceModel.contents.add(sourceElement)
			}
		}
	}
}