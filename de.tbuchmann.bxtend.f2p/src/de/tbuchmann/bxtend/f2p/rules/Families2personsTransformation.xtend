/**
 * Top-level coordinator for the bidirectional, incremental, and synchronising
 * <em>Families-to-Persons</em> model transformation implemented with the BXtend framework.
 *
 * <p>This class owns the three model resources (source, target, correspondence) and
 * maintains an ordered list of {@link Elem2Elem} rules that are executed in sequence
 * for each transformation direction.  The rule execution order is:
 * <ol>
 *   <li>{@link Register2Register} – root-level register pairing</li>
 *   <li>{@link MotherDaughter2Female} – female family members</li>
 *   <li>{@link FatherSon2Male} – male family members</li>
 * </ol>
 *
 * <p><b>Transformation directions</b>
 * <ul>
 *   <li>{@link #sourceToTarget()} – forward: Families → Persons</li>
 *   <li>{@link #targetToSource()} – backward: Persons → Families</li>
 *   <li>{@link #synch()} – synchronisation: reconciles concurrent edits in both models</li>
 * </ul>
 *
 * <p><b>Families name index ({@link #familiesMap})</b><br>
 * A static {@code Map<String, List<Family>>} that indexes all known {@link Families.Family}
 * objects by their name, enabling O(1) candidate lookups during the backward transformation.
 * The map is populated by {@link #sourceToTarget()} and {@link #updateFamiliesMap()}.
 *
 * <p><b>Incremental behaviour / deletion handling</b><br>
 * After each rule pass, dangling correspondence entries (i.e. entries whose source or
 * target element has been deleted) are detected and cleaned up:
 * {@link #deleteUnreferencedTargetElements()} removes orphaned Persons elements, and
 * {@link #deleteUnreferencedSourceElements()} removes orphaned Families elements.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.Family
import Families.FamilyRegister
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr
import de.tbuchmann.bxtend.f2p.correspondence.f2p.F2pFactory
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import java.util.ArrayList
import java.util.List
import java.util.Map
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil

import static extension org.eclipse.emf.ecore.util.EcoreUtil.*

class Families2personsTransformation {
	
	/** The Families (source) model resource. */
	Resource sourceModel
	/** The Persons (target) model resource. */
	Resource targetModel
	/** The correspondence model resource. */
	Resource corrModel
	
	/** Ordered list of transformation rules applied during each direction pass. */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

	/**
	 * Index of all known {@link Family} objects keyed by family name.
	 * Used during backward transformation to find candidate families without a full
	 * linear scan of the source model.  Updated by {@link #sourceToTarget()} and
	 * {@link #updateFamiliesMap()}.
	 */
	public static Map<String, List<Family>> familiesMap = newHashMap
	
	/** The currently active backward-transformation decision strategy. */
	TargetToSourceDecision decision
	
	/**
	 * Constructs a new transformation by loading the three models from the given URIs.
	 * A fresh {@link de.tbuchmann.bxtend.f2p.correspondence.f2p.Transformation} root
	 * is created in the correspondence model if it is empty.
	 *
	 * @param source        URI of the Families model resource
	 * @param target        URI of the Persons model resource
	 * @param correspondence URI of the correspondence model resource
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(F2pFactory.eINSTANCE.createTransformation)	
		}
		addRules			
	}
	
	/**
	 * Constructs a new transformation from already-loaded EMF {@link Resource} objects.
	 * Useful in test scenarios where resources are set up programmatically.
	 *
	 * @param source        the Families model resource
	 * @param target        the Persons model resource
	 * @param correspondence the correspondence model resource
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(F2pFactory.eINSTANCE.createTransformation)	
		}
		addRules
	}
	
	/**
	 * Executes the forward transformation (Families → Persons).
	 *
	 * <p>Each rule in {@link #rules} is invoked in order.  Afterwards the
	 * {@link #familiesMap} index is updated and dangling Persons elements caused by
	 * source-side deletions are cleaned up.
	 */
	def void sourceToTarget() {
		if (sourceModel.contents.size != 0) {
			for (Elem2Elem e : rules) {
				e.sourceToTarget
			}
			
			// update families Hashmap
			(sourceModel.contents.get(0) as FamilyRegister).families.forEach[ f |
				var famList = familiesMap.get(f.name)
				if (famList === null) {
					famList = newArrayList
					famList += f
				} else {
					if (!famList.contains(f))
						famList += f
				}
				familiesMap.put(f.name, famList)
			]
		}
		
		// handle deletions
		deleteUnreferencedTargetElements		
	}
	
	/**
	 * Executes the backward transformation (Persons → Families).
	 *
	 * <p>Each rule in {@link #rules} is invoked in order.  Afterwards dangling Families
	 * elements caused by target-side deletions are cleaned up.
	 */
	def void targetToSource() {		
		if (targetModel.contents.size != 0)
		for (Elem2Elem e: rules) {
			e.targetToSource()
		}
		
		// handle deletions
		deleteUnreferencedSourceElements
	}
	
	/**
	 * Executes the synchronisation direction, which reconciles concurrent edits made to
	 * both models since the last synchronisation point.
	 *
	 * <p>Each rule's {@link Elem2Elem#synch()} is called in order.  Afterwards both
	 * dangling source and target elements are cleaned up.
	 */
	def void synch() {
		for (Elem2Elem e : rules) 
			e.synch
			
		// handle deletions
		deleteUnreferencedSourceElements
		deleteUnreferencedTargetElements
	}
	
	/**
	 * Replaces the decision strategy on this transformation and propagates the change to
	 * all registered rules.
	 *
	 * @param dec the new {@link TargetToSourceDecision} to use
	 */
	def void configure(TargetToSourceDecision dec) {
		decision = dec
		rules.forEach[r | r.configure(dec)]
	}
	
	/**
	 * Placeholder for future consistency checks on the correspondence model.
	 *
	 * @return {@code true} (currently always consistent)
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Returns a stream of {@link Corr} entries whose source element is {@code null},
	 * indicating that the corresponding Families element has been deleted.
	 *
	 * @return an iterator over correspondences with a missing source element
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.sourceElement === null
		]
	}
	
	/**
	 * Returns a stream of {@link Corr} entries whose target element is {@code null},
	 * indicating that the corresponding Persons element has been deleted.
	 *
	 * @return an iterator over correspondences with a missing target element
	 */	
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Deletes Persons-side ({@link Persons.Person}) elements whose corresponding Families
	 * element has been removed, and removes the now-empty correspondence entries.
	 *
	 * <p>Called at the end of {@link #sourceToTarget()} and {@link #synch()}.
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			if (c.targetElement !== null)
				deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | e.delete(true)]
	}
	
	/**
	 * Deletes Families-side ({@link Families.FamilyMember} / {@link Families.Family})
	 * elements whose corresponding Persons element has been removed, and removes the
	 * now-empty correspondence entries.
	 *
	 * <p>Called at the end of {@link #targetToSource()} and {@link #synch()}.
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			if (c.sourceElement !== null)
				deletionList += c.sourceElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
	
	/**
	 * Registers all transformation rules in the correct execution order.
	 * Ensures a root Transformation element exists in the correspondence model,
	 * then creates and adds one instance of each rule class.
	 *
	 * <p>Rule order:
	 * <ol>
	 *   <li>{@link Register2Register}</li>
	 *   <li>{@link MotherDaughter2Female}</li>
	 *   <li>{@link FatherSon2Male}</li>
	 * </ol>
	 */
	def private void addRules() {
		if (corrModel.contents.empty) {
			corrModel.contents.add(F2pFactory::eINSTANCE.createTransformation)
		}

		rules.add(new Register2Register(sourceModel, targetModel, corrModel, decision))
		rules.add(new MotherDaughter2Female(sourceModel, targetModel, corrModel, decision))
		rules.add(new FatherSon2Male(sourceModel, targetModel, corrModel, decision))
	}
	
	/**
	 * Refreshes the {@link #familiesMap} name-to-family index from the current state of
	 * the source model.  Should be called after external modifications to the Families
	 * model that are not going through a transformation direction method.
	 */
	def void updateFamiliesMap() {
		if (sourceModel.contents.size != 0) 
		(sourceModel.contents.get(0) as FamilyRegister).families.forEach[ f |
			var famList = familiesMap.get(f.name)
			if (famList === null) {
				famList = newArrayList
				famList += f
			} else {
				if (!famList.contains(f))
					famList += f
			}
			familiesMap.put(f.name, famList)
		]		
	}
}