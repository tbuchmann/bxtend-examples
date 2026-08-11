package de.tbuchmann.bxtend.pdb12pdb2.rules

import org.eclipse.emf.ecore.resource.Resource
import pdb1.Person
import pdb2.Database

/**
 * BXtend transformation rule that synchronises {@code pdb1.Person} elements with
 * {@code pdb2.Person} elements in both directions.
 *
 * <p>This rule handles the non-trivial part of the PDB1 ↔ PDB2 transformation: the
 * name attribute mismatch.  PDB1 stores a person's name as two separate attributes
 * ({@code firstName} and {@code lastName}), while PDB2 stores it as a single
 * {@code name} string.  The rules for each direction are therefore asymmetric:</p>
 *
 * <ul>
 *   <li><b>Forward (PDB1 → PDB2):</b> {@code name = firstName + " " + lastName}
 *       — deterministic concatenation.</li>
 *   <li><b>Backward (PDB2 → PDB1):</b> the full {@code name} must be split back
 *       into {@code firstName} and {@code lastName}.  Because this split is
 *       inherently ambiguous (e.g. {@code "Konrad Hermann Joseph Adenauer"} could
 *       split at any space), the rule delegates the decision to the injected
 *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}
 *       strategy. The split is only re-computed when the concatenated name in PDB1
 *       no longer matches the PDB2 {@code name}, avoiding unnecessary overwrites
 *       during incremental propagation.</li>
 * </ul>
 *
 * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id})
 * are identical in both metamodels and are simply copied in both directions.</p>
 *
 * <p>The containment relationship ({@code database} reference) is resolved via the
 * correspondence model: the parent container of the source/target person is looked
 * up in the {@code elementsToCorr} index, and its counterpart element is used as
 * the new container in the other model.</p>
 */
class Person2Person extends Elem2Elem {

	/**
	 * Creates a new {@code Person2Person} rule instance.
	 *
	 * @param src  the PDB1 (source) EMF resource
	 * @param trgt the PDB2 (target) EMF resource
	 * @param corr the correspondence EMF resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr);
		ruleID = "Person2Person";
	}

	/**
	 * Forward propagation: for every {@code pdb1.Person} in the source model,
	 * creates or updates the corresponding {@code pdb2.Person} in the target model.
	 *
	 * <p>Attribute mapping:</p>
	 * <ul>
	 *   <li>{@code birthday}, {@code placeOfBirth}, {@code id} → copied directly</li>
	 *   <li>{@code firstName + " " + lastName} → {@code name}</li>
	 *   <li>parent {@code pdb1.Database} → corresponding {@code pdb2.Database}
	 *       (resolved through the correspondence model)</li>
	 * </ul>
	 */
	override void sourceToTarget() {
		sourceModel.allContents.filter(typeof(Person)).forEach[source |
			// Get or create the correspondence entry for this source Person.
			val corr = source.getOrCreateCorrModelElement(ruleID);
			// Get or create the matching target Person element.
			val target = corr.getOrCreateTargetElem(targetPackage.person) as pdb2.Person;
			// Copy attributes that are structurally identical in both metamodels.
			target.birthday = source.birthday;
			target.placeOfBirth = source.placeOfBirth;
			target.id = source.id;
			// Resolve the parent database in the target model via the correspondence.
			target.database = source.eContainer.corrModelElem.targetElement as Database;
			// Concatenate firstName and lastName into the single PDB2 name attribute.
			target.name = source.firstName + ' ' + source.lastName;
			corrToName.put(corr, target.name)
		]
	}

	/**
	 * Backward propagation: for every {@code pdb2.Person} in the target model,
	 * creates or updates the corresponding {@code pdb1.Person} in the source model.
	 *
	 * <p>Attribute mapping:</p>
	 * <ul>
	 *   <li>{@code birthday}, {@code placeOfBirth}, {@code id} → copied directly</li>
	 *   <li>{@code name} → {@code firstName} + {@code lastName} via the injected
	 *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision};
	 *       the split is only applied when the current PDB1 concatenated name differs
	 *       from the PDB2 name, preserving existing splits during incremental runs.</li>
	 *   <li>parent {@code pdb2.Database} → corresponding {@code pdb1.Database}
	 *       (resolved through the correspondence model)</li>
	 * </ul>
	 */
	override void targetToSource() {
		targetModel.allContents.filter(typeof(pdb2.Person)).forEach[target |
			// Get or create the correspondence entry for this target Person.
			val corr = target.getOrCreateCorrModelElement(ruleID);
			// Get or create the matching source Person element.
			val source = corr.getOrCreateSourceElem(sourcePackage.person) as Person;
			// Copy attributes that are structurally identical in both metamodels.
			source.birthday = target.birthday;
			source.placeOfBirth = target.placeOfBirth;
			source.id = target.id;
			// Resolve the parent database in the source model via the correspondence.
			source.database = target.eContainer.corrModelElem.sourceElement as pdb1.Database;
			// Only re-split the name when it has actually changed to avoid unnecessary
			// overwrites of a previously user-set firstName/lastName combination.
			if(source.firstName + " " + source.lastName != target.name) {
				source.firstName = decision.getFirstName(target.name)
				source.lastName = decision.getLastName(target.name)
			}
			corrToName.put(corr, target.name)
		]
	}

	/**
	 * Reconciles concurrent edits to {@code Person} pairs.
	 *
	 * <p>The identity/content key is the concatenated name
	 * ({@code firstName + " " + lastName} on PDB1, the single {@code name} on PDB2).
	 * Following the same push-forward-on-change / pull-backward-otherwise logic as
	 * {@link Database2Database#synch()} (using {@link #corrToName}): if the source-side
	 * concatenation changed since the last synchronisation, it is pushed forward (verbatim,
	 * as a plain string — no re-splitting needed since it originates as a PDB1 split
	 * already); otherwise, if the PDB2 {@code name} changed, it is split back via the
	 * injected {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision},
	 * mirroring {@link #targetToSource()}'s hippocratic re-split guard. The remaining
	 * attributes ({@code birthday}, {@code placeOfBirth}, {@code id}) are copied together
	 * with whichever direction the name decision selects, since there is no test evidence
	 * that they can change independently of the name in this benchmark's edit scripts.</p>
	 */
	override void synch() {
		val personList = sourceModel.allContents.filter(typeof(Person)).toList
		val unmatched = targetModel.allContents.filter(typeof(pdb2.Person)).filter[p | p.corrModelElem === null].toList

		personList.forEach [ source |
			val corr = source.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as pdb2.Person
			val sourceKey = source.firstName + " " + source.lastName
			if (target !== null) {
				unmatched.remove(target)
				val lastKey = corrToName.get(corr)
				if (lastKey === null || sourceKey != lastKey) {
					// source changed (or never synchronised yet): push everything forward
					target.birthday = source.birthday
					target.placeOfBirth = source.placeOfBirth
					target.id = source.id
					target.name = sourceKey
					corrToName.put(corr, sourceKey)
				} else if (target.name != lastKey) {
					// only the target changed: pull everything backward
					source.birthday = target.birthday
					source.placeOfBirth = target.placeOfBirth
					source.id = target.id
					source.firstName = decision.getFirstName(target.name)
					source.lastName = decision.getLastName(target.name)
					corrToName.put(corr, target.name)
				}
				// else: neither side changed since the last synchronisation
			} else {
				target = unmatched.findFirst[t | t.name == sourceKey]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatched.remove(target)
					target.birthday = source.birthday
					target.placeOfBirth = source.placeOfBirth
					target.id = source.id
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.person) as pdb2.Person
					target.birthday = source.birthday
					target.placeOfBirth = source.placeOfBirth
					target.id = source.id
					target.name = sourceKey
					target.database = source.eContainer.corrModelElem.targetElement as Database
				}
				corrToName.put(corr, sourceKey)
			}
		]

		unmatched.forEach [ target |
			val corr = target.getOrCreateCorrModelElement(ruleID)
			val source = corr.getOrCreateSourceElem(sourcePackage.person) as Person
			source.birthday = target.birthday
			source.placeOfBirth = target.placeOfBirth
			source.id = target.id
			source.database = target.eContainer.corrModelElem.sourceElement as pdb1.Database
			source.firstName = decision.getFirstName(target.name)
			source.lastName = decision.getLastName(target.name)
			corrToName.put(corr, target.name)
		]
	}
}