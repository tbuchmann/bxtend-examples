package de.tbuchmann.bxtend.pdb12pdb2.rules;

import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.emf.ecore.EObject
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr
import java.util.ArrayList
import java.util.List

import de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision
import de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.ConfigurableTargetToSourceDecision

/**
 * Top-level orchestrator of the PDB1 ↔ PDB2 BXtend transformation.
 *
 * <p>This class ties together the three EMF resources (source, target, correspondence)
 * and the ordered list of {@link Elem2Elem} rules that together implement the full
 * bidirectional, incremental synchronisation between the two person-database models:</p>
 *
 * <ul>
 *   <li>{@link Database2Database} – synchronises {@code pdb1.Database ↔ pdb2.Database}
 *       (must run before {@code Person2Person} so the parent containers exist when
 *       persons are processed).</li>
 *   <li>{@link Person2Person} – synchronises {@code pdb1.Person ↔ pdb2.Person},
 *       including the asymmetric name mapping.</li>
 * </ul>
 *
 * <h3>Incrementality and deletion handling</h3>
 * <p>Incrementality is achieved through the correspondence model: every matched pair of
 * source/target elements is linked by a {@link Corr} entry.  After each propagation
 * pass, "dangling" correspondences (where the source or target element has been
 * deleted) are detected and used to drive deletion of the orphaned counterpart and
 * removal of the stale {@link Corr} entry.</p>
 *
 * <h3>Non-determinism</h3>
 * <p>The backward direction (PDB2 → PDB1) requires splitting a full name string into
 * {@code firstName} / {@code lastName}.  The default strategy is
 * {@link ConfigurableTargetToSourceDecision} with {@code spacePosition = -1}, meaning
 * the <em>last</em> space in the name is used as the split point.  Callers may replace
 * the default by invoking {@link #configure(TargetToSourceDecision)} before the first
 * propagation.</p>
 */
class Pdb12pdb2Transformation {

	/** The PDB1 (source) EMF resource. */
	Resource sourceModel
	/** The PDB2 (target) EMF resource. */
	Resource targetModel
	/** The correspondence / trace EMF resource. */
	Resource corrModel

	/**
	 * Ordered list of bidirectional rules.  Rules are applied in list order during
	 * both forward and backward propagation, so container rules must precede
	 * contained-element rules.
	 */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

	/**
	 * Convenience constructor that loads all three resources from the given URIs into
	 * a shared {@link ResourceSet}.  If the correspondence resource is empty, an
	 * initial {@link de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Transformation}
	 * root element is created automatically.
	 *
	 * @param source        URI of the PDB1 XMI file
	 * @param target        URI of the PDB2 XMI file
	 * @param correspondence URI of the correspondence XMI file
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)

		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Pdb12pdb2Factory.eINSTANCE.createTransformation)
		}

		// Register all rules in the correct processing order.
		addRules
		// Apply the default name-splitting strategy (last space).
		configure(new ConfigurableTargetToSourceDecision(-1))
	}

	/**
	 * Constructor used when the EMF resources have already been loaded by the caller
	 * (e.g. during test setup or when sharing a {@link ResourceSet} with other tools).
	 *
	 * @param source        the PDB1 resource (must not be {@code null})
	 * @param target        the PDB2 resource (must not be {@code null})
	 * @param correspondence the correspondence resource (must not be {@code null})
	 */
	new(Resource source, Resource target, Resource correspondence) {
		sourceModel = source
		targetModel = target
		corrModel = correspondence

		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Pdb12pdb2Factory.eINSTANCE.createTransformation)
		}

		// Register all rules in the correct processing order.
		addRules
		// Apply the default name-splitting strategy (last space).
		configure(new ConfigurableTargetToSourceDecision(-1))
	}

	/**
	 * Registers all transformation rules in the required execution order.
	 * {@link Database2Database} must precede {@link Person2Person} so that target
	 * database containers exist before persons are linked to them.
	 */
	def addRules() {
		rules += new Database2Database(sourceModel, targetModel, corrModel)
		rules += new Person2Person(sourceModel, targetModel, corrModel)
	}

	/**
	 * Propagates the given {@link TargetToSourceDecision} to every registered rule.
	 *
	 * @param dec the decision strategy to use for backward name splitting
	 */
	def void configure(TargetToSourceDecision dec) {
		rules.forEach[r | r.configure(dec)]
	}

	/**
	 * Runs all rules in the forward direction (PDB1 → PDB2) and then removes any
	 * PDB2 elements whose correspondence source has been deleted.
	 *
	 * <p>The propagation is skipped entirely if the source model is empty, which
	 * prevents accidental erasure of the target during initialisation.</p>
	 */
	def void sourceToTarget() {
		if (sourceModel.contents.size != 0)
			for (Elem2Elem e : rules) {
				e.sourceToTarget()
			}

		// Remove target elements that have lost their source counterpart.
		deleteUnreferencedTargetElements
	}

	/**
	 * Runs all rules in the backward direction (PDB2 → PDB1) and then removes any
	 * PDB1 elements whose correspondence target has been deleted.
	 *
	 * <p>The propagation is skipped entirely if the target model is empty.</p>
	 */
	def void targetToSource() {
		if (targetModel.contents.size != 0)
			for (Elem2Elem e : rules) {
				e.targetToSource()
			}

		// Remove source elements that have lost their target counterpart.
		deleteUnreferencedSourceElements
	}

	/**
	 * Runs all rules' synchronisation direction, reconciling concurrent edits made to both
	 * the PDB1 and PDB2 models since the last synchronisation point.
	 *
	 * <p>Executes each rule's {@link Elem2Elem#synch()} in the same registration order as
	 * {@link #sourceToTarget()}/{@link #targetToSource()}, then cleans up dangling
	 * correspondences on both sides.</p>
	 */
	def void synch() {
		for (Elem2Elem e : rules)
			e.synch()

		deleteUnreferencedSourceElements
		deleteUnreferencedTargetElements
	}

	/**
	 * Placeholder for checking that all correspondences are valid (both sides
	 * present and consistent). Currently always returns {@code true}.
	 *
	 * @return {@code true} if all correspondences are intact
	 */
	def boolean checkCorrespondences() {
		true
	}

	/**
	 * Finds all {@link Corr} entries where the source element has been deleted
	 * (i.e. {@code sourceElement == null}).  These correspondences indicate that
	 * a PDB1 element was removed and the corresponding PDB2 element must be cleaned up.
	 *
	 * @return an iterator over dangling (source-less) correspondences
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[c |
			c.sourceElement === null
		]
	}

	/**
	 * Finds all {@link Corr} entries where the target element has been deleted
	 * (i.e. {@code targetElement == null}).  These correspondences indicate that
	 * a PDB2 element was removed and the corresponding PDB1 element must be cleaned up.
	 *
	 * @return an iterator over dangling (target-less) correspondences
	 */
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[c |
			c.targetElement === null
		]
	}

	/**
	 * Removes PDB2 elements and their {@link Corr} entries for every correspondence
	 * whose source element is {@code null} (i.e. the PDB1 element was deleted).
	 * Uses {@link EcoreUtil#delete} with {@code recursive = true} to cascade through
	 * any contained sub-elements.
	 */
	def deleteUnreferencedTargetElements() {
		val List<EObject> deletionList = newArrayList;

		detectSourceDeletions().forEach[c |
			// TODO: add handling of contained and referenced Elements here if appropriate
			// end
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}

	/**
	 * Removes PDB1 elements and their {@link Corr} entries for every correspondence
	 * whose target element is {@code null} (i.e. the PDB2 element was deleted).
	 * Uses {@link EcoreUtil#delete} with {@code recursive = true} to cascade through
	 * any contained sub-elements.
	 */
	def deleteUnreferencedSourceElements() {
		val List<EObject> deletionList = newArrayList;

		detectTargetDeletions().forEach[c |
			// TODO: add handling of contained and referenced Elements here if appropriate

			// end
			deletionList += c.sourceElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
}