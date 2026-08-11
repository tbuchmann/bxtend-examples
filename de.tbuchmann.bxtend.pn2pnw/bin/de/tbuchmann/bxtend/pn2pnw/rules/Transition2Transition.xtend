package de.tbuchmann.bxtend.pn2pnw.rules

import java.util.ArrayList
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil
import pn.Transition
import pnw.Edge
import pnw.Net
import pnw.Place

/**
 * Bidirectional transformation rule that synchronises {@code Transition}
 * elements — and the arcs connected to them — between the unweighted Petri
 * net ({@code pn}) and the weighted Petri net ({@code pnw}).
 *
 * <p>This is the most complex rule in the transformation because the two
 * metamodels represent arcs differently:</p>
 * <ul>
 *   <li>In {@code pn}, arcs are plain cross-references:
 *       {@code Transition.srcP2T} (incoming places) and
 *       {@code Transition.trgT2P} (outgoing places).</li>
 *   <li>In {@code pnw}, arcs are first-class {@link pnw.PTEdge} /
 *       {@link pnw.TPEdge} objects owned by the {@code Net}, each carrying
 *       an integer {@code weight} attribute (default&nbsp;1).</li>
 * </ul>
 *
 * <p><b>Forward (source → target) arc mapping:</b></p>
 * <ul>
 *   <li>Each entry in {@code pn.Transition.srcP2T} (Place-to-Transition arc)
 *       becomes a {@link pnw.PTEdge} linking the corresponding
 *       {@code pnw.Place} to the {@code pnw.Transition}.</li>
 *   <li>Each entry in {@code pn.Transition.trgT2P} (Transition-to-Place arc)
 *       becomes a {@link pnw.TPEdge} linking the {@code pnw.Transition} to
 *       the corresponding {@code pnw.Place}.</li>
 *   <li>When an edge already exists in the target, its {@code weight} is
 *       preserved (<em>hippocraticness</em>).</li>
 *   <li>Edges that existed in the target but no longer have a corresponding
 *       source arc are collected as <em>unreferenced candidates</em> and
 *       deleted via {@link EcoreUtil#delete}.</li>
 * </ul>
 *
 * <p><b>Backward (target → source) arc mapping:</b></p>
 * <ul>
 *   <li>Each {@link pnw.PTEdge} is mapped back to an entry in
 *       {@code pn.Transition.srcP2T}.</li>
 *   <li>Each {@link pnw.TPEdge} is mapped back to an entry in
 *       {@code pn.Transition.trgT2P}.</li>
 *   <li>Source-side references that no longer have a corresponding weighted
 *       edge are removed from the cross-reference lists.</li>
 * </ul>
 *
 * <p>This rule must be executed <em>after</em> {@link Net2Net} and
 * {@link Place2Place}, because both the net container and the place
 * correspondences must already be established before arcs can be wired.</p>
 *
 * <p>The rule uses the rule identifier {@code "transition"} to tag every
 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr Corr} it
 * creates.</p>
 */
class Transition2Transition extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "transition"}.
	 *
	 * @param src   the source-model resource (unweighted Petri net)
	 * @param trgt  the target-model resource (weighted Petri net)
	 * @param corr  the correspondence-model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "transition"
	}
	
	/**
	 * Forward pass: for every {@code pn.Transition} in the source model,
	 * finds or creates the corresponding {@code pnw.Transition}, synchronises
	 * the {@code name} attribute, and reconciles all incoming ({@link pnw.PTEdge})
	 * and outgoing ({@link pnw.TPEdge}) weighted arcs with the source-side
	 * cross-references.
	 *
	 * <p>New edges are created with {@code weight = 1}.  Existing edges whose
	 * source arc has been deleted are removed from the model.</p>
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Transition))
			.forEach[t |
				var corr = t.getOrCreateCorrModelElement(ruleID)
				val targetTransition = corr.getOrCreateTargetElem(targetPackage.transition) as pnw.Transition
				targetTransition.name = t.name
				val targetNet = t.net.corrModelElem.targetElement as Net
				targetNet.elements += targetTransition
				reconcileEdges(t, targetTransition, targetNet)
			]
	}

	/**
	 * Reconciles {@code targetTransition}'s incoming ({@link pnw.PTEdge}) and outgoing
	 * ({@link pnw.TPEdge}) weighted arcs against {@code t}'s current {@code srcP2T}/{@code trgT2P}
	 * cross-references: creates any missing edge (with {@code weight = 1}), and removes any
	 * edge that no longer has a backing source-side reference.
	 *
	 * <p>Existing edges are otherwise left untouched — in particular {@code weight} is never
	 * reassigned once an edge exists, since it has no source-side equivalent to synchronise
	 * against. Shared by {@link #sourceToTarget()} and {@link #synch()}.</p>
	 *
	 * @param t                the source transition providing the current cross-references
	 * @param targetTransition the corresponding target transition whose edges are updated
	 * @param targetNet        the target net that should own any newly created edge
	 */
	private def void reconcileEdges(Transition t, pnw.Transition targetTransition, Net targetNet) {
		val pnSourcePlaces = t.srcP2T
		val pnTargetPlaces = t.trgT2P
		val unreferencedEdgeCandidates = new ArrayList<Edge>
		unreferencedEdgeCandidates += targetTransition.inPTEdges
		unreferencedEdgeCandidates += targetTransition.outTPEdges
		for (pnSP : pnSourcePlaces) {
			// check, if there is a connection from pnwSourcePlace to targetTransition
			val pnwSP = pnSP.corrModelElem.targetElement as Place
			if (pnwSP.outPTEdges.findFirst[ptEdge | ptEdge.toTransition == targetTransition] === null) {
				var ptEdge = targetFactory.createPTEdge
				pnwSP.outPTEdges += ptEdge
				ptEdge.toTransition = targetTransition
				ptEdge.weight = 1
				targetNet.elements += ptEdge
			}
			unreferencedEdgeCandidates -= pnwSP.outPTEdges.findFirst[ptEdge |
				ptEdge.toTransition == targetTransition
			]
		}
		for (pnTP : pnTargetPlaces) {
			// check, if there is a connection from targetTransition to pnwTargetPlace
			val pnwTP = pnTP.corrModelElem.targetElement as Place
			if (pnwTP.inTPEdges.findFirst[tpEdge | tpEdge.fromTransition == targetTransition] === null) {
				var tpEdge = targetFactory.createTPEdge
				pnwTP.inTPEdges += tpEdge
				tpEdge.fromTransition = targetTransition
				tpEdge.weight = 1
				targetNet.elements += tpEdge
			}
			unreferencedEdgeCandidates -= pnwTP.inTPEdges.findFirst[tpEdge |
				tpEdge.fromTransition == targetTransition
			]
		}
		for (unreferencedEdge : unreferencedEdgeCandidates) {
			EcoreUtil.delete(unreferencedEdge);
		}
	}
	
	/**
	 * Backward pass: for every {@code pnw.Transition} in the target model,
	 * finds or creates the corresponding {@code pn.Transition}, synchronises
	 * the {@code name} attribute, and reconciles all incoming and outgoing
	 * source cross-references with the target-side {@link pnw.PTEdge} /
	 * {@link pnw.TPEdge} objects.
	 *
	 * <p>Cross-references that no longer have a corresponding weighted arc in
	 * the target are removed from the source transition's reference lists.</p>
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(pnw.Transition))
			.forEach[tr |
				val pnwInEdges = tr.inPTEdges
				val pnwOutEdges = tr.outTPEdges
				var corr = tr.getOrCreateCorrModelElement(ruleID)
				val sourceTransition = corr.getOrCreateSourceElem(sourcePackage.transition) as Transition
				sourceTransition.name = tr.name
				val sourceNet = tr.net.corrModelElem.sourceElement as pn.Net
				sourceNet.elements += sourceTransition
				val unreferencedPTEdgeCandidates = new ArrayList(sourceTransition.srcP2T)
				val unreferencedTPEdgeCandidates = new ArrayList(sourceTransition.trgT2P)
				pnwInEdges.forEach[ptEdge |
					val pnInEdge = ptEdge.fromPlace.corrModelElem.sourceElement as pn.Place
					sourceTransition.srcP2T += pnInEdge
					unreferencedPTEdgeCandidates -= pnInEdge
				]
				pnwOutEdges.forEach[tpEdge |
					val pnOutEdge = tpEdge.toPlace.corrModelElem.sourceElement as pn.Place
					sourceTransition.trgT2P += pnOutEdge
					unreferencedTPEdgeCandidates -= pnOutEdge
				]
				for (val it = sourceTransition.srcP2T.iterator(); it.hasNext(); ) {
					if (unreferencedPTEdgeCandidates.contains(it.next())) {
						it.remove()
					}
				}
				for (val it = sourceTransition.trgT2P.iterator(); it.hasNext(); ) {
					if (unreferencedTPEdgeCandidates.contains(it.next())) {
						it.remove()
					}
				}
			]
	}

	/**
	 * Reconciles concurrent edits to {@code Transition} pairs and their arcs.
	 *
	 * <p>{@code name} follows the same push/pull logic as {@link Net2Net#synch()}/
	 * {@link Place2Place#synch()}. Arc existence is source-led: {@link #reconcileEdges}
	 * (the same logic {@link #sourceToTarget()} already uses) creates any {@code pnw} edge
	 * backed by a current {@code srcP2T}/{@code trgT2P} reference and removes any edge that
	 * no longer has one — this is safe for concurrent target-only <em>attribute</em> edits
	 * because {@code weight} is never touched on an edge that already exists (it has no
	 * source-side equivalent to reconcile against). A brand-new target-only transition
	 * (no correspondence yet) is instead pulled backward: its existing edges are used to
	 * recreate the missing {@code srcP2T}/{@code trgT2P} references, mirroring
	 * {@link #targetToSource()}.</p>
	 */
	override void synch() {
		val transList = sourceModel.allContents.filter(typeof(Transition)).toList
		val unmatched = targetModel.allContents.filter(typeof(pnw.Transition)).filter[t | t.corrModelElem === null].toList

		transList.forEach [ t |
			val corr = t.getOrCreateCorrModelElement(ruleID)
			var targetTransition = corr.targetElement as pnw.Transition
			if (targetTransition !== null) {
				unmatched.remove(targetTransition)
				if (corrToName.get(corr) != t.name)
					targetTransition.name = t.name
				else
					t.name = targetTransition.name
			} else {
				targetTransition = unmatched.findFirst[tt | tt.name == t.name]
				if (targetTransition !== null) {
					corr.targetElement = targetTransition
					elementsToCorr.put(targetTransition, corr)
					unmatched.remove(targetTransition)
				} else {
					targetTransition = corr.getOrCreateTargetElem(targetPackage.transition) as pnw.Transition => [name = t.name]
					val targetNet = t.net.corrModelElem.targetElement as Net
					targetNet.elements += targetTransition
				}
			}
			corrToName.put(corr, t.name)
			reconcileEdges(t, targetTransition, t.net.corrModelElem.targetElement as Net)
		]

		unmatched.forEach [ tr |
			val corr = tr.getOrCreateCorrModelElement(ruleID)
			val sourceTransition = corr.getOrCreateSourceElem(sourcePackage.transition) as Transition => [name = tr.name]
			val sourceNet = tr.net.corrModelElem.sourceElement as pn.Net
			sourceNet.elements += sourceTransition
			corrToName.put(corr, sourceTransition.name)
			// pull backward: recreate pn cross-references from the target's existing edges
			tr.inPTEdges.forEach[ptEdge |
				sourceTransition.srcP2T += ptEdge.fromPlace.corrModelElem.sourceElement as pn.Place
			]
			tr.outTPEdges.forEach[tpEdge |
				sourceTransition.trgT2P += tpEdge.toPlace.corrModelElem.sourceElement as pn.Place
			]
		]
	}
}