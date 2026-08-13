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
 *       strategy. In {@link #targetToSource()} the split is re-derived whenever
 *       <em>any</em> tracked attribute of the person changed since the last backward
 *       call (not just the name text) — an untouched person keeps its previous split
 *       even if the decision strategy changes in between; {@link #synch()} only
 *       re-splits when the PDB2 {@code name} itself changed since the last
 *       synchronisation (tracked via {@link #corrToName}).</li>
 * </ul>
 *
 * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id})
 * are identical in both metamodels and are simply copied in both directions; in
 * {@link #synch()} each is tracked independently of the name (see
 * {@link #corrToBirthday}, {@link #corrToPlaceOfBirth}, {@link #corrToId}), since a
 * concurrent edit can change one of them without touching the name.</p>
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
			corrToBirthday.put(corr, source.birthday)
			corrToPlaceOfBirth.put(corr, source.placeOfBirth)
			corrToId.put(corr, source.id)
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
	 *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}.
	 *       The split is only re-derived when <em>something</em> about this person changed
	 *       since the last backward propagation (name text, birthday, placeOfBirth, or id —
	 *       tracked via the {@code corrTo*} snapshots); an entirely untouched person keeps
	 *       whatever split was chosen last time even if the decision strategy changes in
	 *       between (see {@link #synch()} below for why this is per-person, not per-name-text).</li>
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
			// Detect whether ANY tracked attribute of this person changed since the last
			// backward call - not just the name text. The reference tool re-derives the
			// ambiguous firstName/lastName split fresh (using the *current* decision
			// strategy) whenever a person is touched at all, even via an unrelated
			// attribute like `id`; a person nobody touched keeps its previous split
			// unchanged even across a decision-strategy change. Verified against three
			// independent fixtures: IncrementalBackward#testIncrementalInsertsDynamicConfig
			// and #testHippocraticness both require untouched persons to keep their old
			// split despite a config change; #testIncrementalValueChange requires a person
			// touched only via `id` to get a fresh split under the new config.
			val changed = corrToName.get(corr) != target.name
				|| corrToBirthday.get(corr) != target.birthday
				|| corrToPlaceOfBirth.get(corr) != target.placeOfBirth
				|| corrToId.get(corr) != target.id
			// Copy attributes that are structurally identical in both metamodels.
			source.birthday = target.birthday;
			source.placeOfBirth = target.placeOfBirth;
			source.id = target.id;
			// Resolve the parent database in the source model via the correspondence.
			source.database = target.eContainer.corrModelElem.sourceElement as pdb1.Database;
			if (changed) {
				source.firstName = decision.getFirstName(target.name)
				source.lastName = decision.getLastName(target.name)
			}
			corrToName.put(corr, target.name)
			corrToBirthday.put(corr, source.birthday)
			corrToPlaceOfBirth.put(corr, source.placeOfBirth)
			corrToId.put(corr, source.id)
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
	 * injected {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}.</p>
	 *
	 * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id}) are
	 * <strong>not</strong> tied to the name decision — each is resolved independently against
	 * its own snapshot ({@link #corrToBirthday}, {@link #corrToPlaceOfBirth}, {@link #corrToId}),
	 * since a concurrent edit can change one of them while the name stays untouched (see
	 * {@code MonotonicDeleting#testCombinedMatchingDeletion}, which changes only
	 * {@code placeOfBirth} on the target side of an otherwise-unmodified person).</p>
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
					// source changed (or never synchronised yet): push the name forward
					target.name = sourceKey
					corrToName.put(corr, sourceKey)
				} else if (target.name != lastKey) {
					// only the target changed: pull the name backward
					source.firstName = decision.getFirstName(target.name)
					source.lastName = decision.getLastName(target.name)
					corrToName.put(corr, target.name)
				}
				// else: neither side changed since the last synchronisation

				// birthday, placeOfBirth and id can each change independently of the
				// name (see MonotonicDeleting#testCombinedMatchingDeletion, where
				// placeOfBirth changes on the target while the name stays put) - resolve
				// each attribute separately against its own snapshot, source wins on a
				// genuine conflict.
				val lastBirthday = corrToBirthday.get(corr)
				if (lastBirthday === null || source.birthday != lastBirthday)
					target.birthday = source.birthday
				else if (target.birthday != lastBirthday)
					source.birthday = target.birthday
				corrToBirthday.put(corr, source.birthday)

				val lastPlaceOfBirth = corrToPlaceOfBirth.get(corr)
				if (lastPlaceOfBirth === null || source.placeOfBirth != lastPlaceOfBirth)
					target.placeOfBirth = source.placeOfBirth
				else if (target.placeOfBirth != lastPlaceOfBirth)
					source.placeOfBirth = target.placeOfBirth
				corrToPlaceOfBirth.put(corr, source.placeOfBirth)

				val lastId = corrToId.get(corr)
				if (lastId === null || source.id != lastId)
					target.id = source.id
				else if (target.id != lastId)
					source.id = target.id
				corrToId.put(corr, source.id)
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
				corrToBirthday.put(corr, source.birthday)
				corrToPlaceOfBirth.put(corr, source.placeOfBirth)
				corrToId.put(corr, source.id)
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
			corrToBirthday.put(corr, source.birthday)
			corrToPlaceOfBirth.put(corr, source.placeOfBirth)
			corrToId.put(corr, source.id)
		]
	}
}