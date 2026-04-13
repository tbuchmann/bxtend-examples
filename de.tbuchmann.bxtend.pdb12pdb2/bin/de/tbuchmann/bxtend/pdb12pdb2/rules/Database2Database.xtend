package de.tbuchmann.bxtend.pdb12pdb2.rules

import org.eclipse.emf.ecore.resource.Resource

/**
 * BXtend transformation rule that synchronises {@code pdb1.Database} elements with
 * {@code pdb2.Database} elements in both directions.
 *
 * <p>This is the "root" rule of the PDB1 ↔ PDB2 transformation.  A Database element
 * exists in both metamodels with an identical structure (a single {@code name}
 * attribute and a containment reference to {@code Person} objects), so the rule is
 * symmetric: the only attribute propagated is the database {@code name}.</p>
 *
 * <h3>Forward (PDB1 → PDB2)</h3>
 * <ol>
 *   <li>Iterates over every {@code pdb1.Database} in the source model.</li>
 *   <li>Looks up or creates a {@link de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr}
 *       entry for the source database (keyed with {@code "Database2Database"}).</li>
 *   <li>Looks up or creates a matching {@code pdb2.Database} via the correspondence.</li>
 *   <li>Copies the {@code name} attribute from source to target.</li>
 *   <li>Adds the target database to the target model's root content list
 *       (idempotent because EMF ignores re-additions of already-contained objects).</li>
 * </ol>
 *
 * <h3>Backward (PDB2 → PDB1)</h3>
 * <p>Mirror image of the forward direction: iterates over {@code pdb2.Database} elements
 * and propagates their {@code name} to the corresponding {@code pdb1.Database}.</p>
 */
class Database2Database extends Elem2Elem {

	/**
	 * Creates a new {@code Database2Database} rule instance.
	 *
	 * @param src  the PDB1 (source) EMF resource
	 * @param trgt the PDB2 (target) EMF resource
	 * @param corr the correspondence EMF resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr);
		ruleID = "Database2Database";
	}

	/**
	 * Forward propagation: copies the {@code name} of every {@code pdb1.Database}
	 * to its corresponding {@code pdb2.Database}, creating target elements and
	 * correspondence entries as needed.
	 */
	override void sourceToTarget() {
		sourceModel.allContents.filter(typeof(pdb1.Database)).forEach[source |
			// Get or create the correspondence entry for this source Database.
			val corr = source.getOrCreateCorrModelElement(ruleID);
			// Get or create the matching target Database element.
			val target = corr.getOrCreateTargetElem(targetPackage.database) as pdb2.Database;
			// Propagate the database name (the only shared attribute).
			target.setName(source.getName());
			// Ensure the target Database is part of the target model's root content.
			targetModel.contents += target
		]
	}

	/**
	 * Backward propagation: copies the {@code name} of every {@code pdb2.Database}
	 * to its corresponding {@code pdb1.Database}, creating source elements and
	 * correspondence entries as needed.
	 */
	override void targetToSource() {
		targetModel.allContents.filter(typeof(pdb2.Database)).forEach[target |
			// Get or create the correspondence entry for this target Database.
			val corr = target.getOrCreateCorrModelElement(ruleID);
			// Get or create the matching source Database element.
			val source = corr.getOrCreateSourceElem(sourcePackage.database) as pdb1.Database;
			// Propagate the database name back to PDB1.
			source.setName(target.getName());
			// Ensure the source Database is part of the source model's root content.
			sourceModel.contents += source
		]
	}
}