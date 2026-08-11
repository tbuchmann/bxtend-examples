package de.tbuchmann.bxtend.pn2pnw.rules;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import pn.Place;
import pn.Transition;
import pnw.Edge;
import pnw.Net;
import pnw.NetElement;
import pnw.PTEdge;
import pnw.TPEdge;

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
@SuppressWarnings("all")
public class Transition2Transition extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "transition"}.
   * 
   * @param src   the source-model resource (unweighted Petri net)
   * @param trgt  the target-model resource (weighted Petri net)
   * @param corr  the correspondence-model resource
   */
  public Transition2Transition(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "transition";
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
  @Override
  public void sourceToTarget() {
    final Procedure1<Transition> _function = (Transition t) -> {
      Corr corr = this.getOrCreateCorrModelElement(t, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getTransition());
      final pnw.Transition targetTransition = ((pnw.Transition) _orCreateTargetElem);
      targetTransition.setName(t.getName());
      EObject _targetElement = this.getCorrModelElem(t.getNet()).getTargetElement();
      final Net targetNet = ((Net) _targetElement);
      EList<NetElement> _elements = targetNet.getElements();
      _elements.add(targetTransition);
      this.reconcileEdges(t, targetTransition, targetNet);
    };
    IteratorExtensions.<Transition>forEach(Iterators.<Transition>filter(this.sourceModel.getAllContents(), Transition.class), _function);
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
  private void reconcileEdges(final Transition t, final pnw.Transition targetTransition, final Net targetNet) {
    final EList<Place> pnSourcePlaces = t.getSrcP2T();
    final EList<Place> pnTargetPlaces = t.getTrgT2P();
    final ArrayList<Edge> unreferencedEdgeCandidates = new ArrayList<Edge>();
    EList<PTEdge> _inPTEdges = targetTransition.getInPTEdges();
    Iterables.<Edge>addAll(unreferencedEdgeCandidates, _inPTEdges);
    EList<TPEdge> _outTPEdges = targetTransition.getOutTPEdges();
    Iterables.<Edge>addAll(unreferencedEdgeCandidates, _outTPEdges);
    for (final Place pnSP : pnSourcePlaces) {
      {
        EObject _targetElement = this.getCorrModelElem(pnSP).getTargetElement();
        final pnw.Place pnwSP = ((pnw.Place) _targetElement);
        final Function1<PTEdge, Boolean> _function = (PTEdge ptEdge) -> {
          pnw.Transition _toTransition = ptEdge.getToTransition();
          return Boolean.valueOf(Objects.equals(_toTransition, targetTransition));
        };
        PTEdge _findFirst = IterableExtensions.<PTEdge>findFirst(pnwSP.getOutPTEdges(), _function);
        boolean _tripleEquals = (_findFirst == null);
        if (_tripleEquals) {
          PTEdge ptEdge = this.targetFactory.createPTEdge();
          EList<PTEdge> _outPTEdges = pnwSP.getOutPTEdges();
          _outPTEdges.add(ptEdge);
          ptEdge.setToTransition(targetTransition);
          ptEdge.setWeight(1);
          EList<NetElement> _elements = targetNet.getElements();
          _elements.add(ptEdge);
        }
        final Function1<PTEdge, Boolean> _function_1 = (PTEdge ptEdge_1) -> {
          pnw.Transition _toTransition = ptEdge_1.getToTransition();
          return Boolean.valueOf(Objects.equals(_toTransition, targetTransition));
        };
        PTEdge _findFirst_1 = IterableExtensions.<PTEdge>findFirst(pnwSP.getOutPTEdges(), _function_1);
        unreferencedEdgeCandidates.remove(_findFirst_1);
      }
    }
    for (final Place pnTP : pnTargetPlaces) {
      {
        EObject _targetElement = this.getCorrModelElem(pnTP).getTargetElement();
        final pnw.Place pnwTP = ((pnw.Place) _targetElement);
        final Function1<TPEdge, Boolean> _function = (TPEdge tpEdge) -> {
          pnw.Transition _fromTransition = tpEdge.getFromTransition();
          return Boolean.valueOf(Objects.equals(_fromTransition, targetTransition));
        };
        TPEdge _findFirst = IterableExtensions.<TPEdge>findFirst(pnwTP.getInTPEdges(), _function);
        boolean _tripleEquals = (_findFirst == null);
        if (_tripleEquals) {
          TPEdge tpEdge = this.targetFactory.createTPEdge();
          EList<TPEdge> _inTPEdges = pnwTP.getInTPEdges();
          _inTPEdges.add(tpEdge);
          tpEdge.setFromTransition(targetTransition);
          tpEdge.setWeight(1);
          EList<NetElement> _elements = targetNet.getElements();
          _elements.add(tpEdge);
        }
        final Function1<TPEdge, Boolean> _function_1 = (TPEdge tpEdge_1) -> {
          pnw.Transition _fromTransition = tpEdge_1.getFromTransition();
          return Boolean.valueOf(Objects.equals(_fromTransition, targetTransition));
        };
        TPEdge _findFirst_1 = IterableExtensions.<TPEdge>findFirst(pnwTP.getInTPEdges(), _function_1);
        unreferencedEdgeCandidates.remove(_findFirst_1);
      }
    }
    for (final Edge unreferencedEdge : unreferencedEdgeCandidates) {
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
  @Override
  public void targetToSource() {
    final Procedure1<pnw.Transition> _function = (pnw.Transition tr) -> {
      final EList<PTEdge> pnwInEdges = tr.getInPTEdges();
      final EList<TPEdge> pnwOutEdges = tr.getOutTPEdges();
      Corr corr = this.getOrCreateCorrModelElement(tr, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getTransition());
      final Transition sourceTransition = ((Transition) _orCreateSourceElem);
      sourceTransition.setName(tr.getName());
      EObject _sourceElement = this.getCorrModelElem(tr.getNet()).getSourceElement();
      final pn.Net sourceNet = ((pn.Net) _sourceElement);
      EList<pn.NetElement> _elements = sourceNet.getElements();
      _elements.add(sourceTransition);
      EList<Place> _srcP2T = sourceTransition.getSrcP2T();
      final ArrayList<Place> unreferencedPTEdgeCandidates = new ArrayList<Place>(_srcP2T);
      EList<Place> _trgT2P = sourceTransition.getTrgT2P();
      final ArrayList<Place> unreferencedTPEdgeCandidates = new ArrayList<Place>(_trgT2P);
      final Consumer<PTEdge> _function_1 = (PTEdge ptEdge) -> {
        EObject _sourceElement_1 = this.getCorrModelElem(ptEdge.getFromPlace()).getSourceElement();
        final Place pnInEdge = ((Place) _sourceElement_1);
        EList<Place> _srcP2T_1 = sourceTransition.getSrcP2T();
        _srcP2T_1.add(pnInEdge);
        unreferencedPTEdgeCandidates.remove(pnInEdge);
      };
      pnwInEdges.forEach(_function_1);
      final Consumer<TPEdge> _function_2 = (TPEdge tpEdge) -> {
        EObject _sourceElement_1 = this.getCorrModelElem(tpEdge.getToPlace()).getSourceElement();
        final Place pnOutEdge = ((Place) _sourceElement_1);
        EList<Place> _trgT2P_1 = sourceTransition.getTrgT2P();
        _trgT2P_1.add(pnOutEdge);
        unreferencedTPEdgeCandidates.remove(pnOutEdge);
      };
      pnwOutEdges.forEach(_function_2);
      for (final Iterator<Place> it = sourceTransition.getSrcP2T().iterator(); it.hasNext();) {
        boolean _contains = unreferencedPTEdgeCandidates.contains(it.next());
        if (_contains) {
          it.remove();
        }
      }
      for (final Iterator<Place> it = sourceTransition.getTrgT2P().iterator(); it.hasNext();) {
        boolean _contains = unreferencedTPEdgeCandidates.contains(it.next());
        if (_contains) {
          it.remove();
        }
      }
    };
    IteratorExtensions.<pnw.Transition>forEach(Iterators.<pnw.Transition>filter(this.targetModel.getAllContents(), pnw.Transition.class), _function);
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
  @Override
  public void synch() {
    final List<Transition> transList = IteratorExtensions.<Transition>toList(Iterators.<Transition>filter(this.sourceModel.getAllContents(), Transition.class));
    final Function1<pnw.Transition, Boolean> _function = (pnw.Transition t) -> {
      Corr _corrModelElem = this.getCorrModelElem(t);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<pnw.Transition> unmatched = IteratorExtensions.<pnw.Transition>toList(IteratorExtensions.<pnw.Transition>filter(Iterators.<pnw.Transition>filter(this.targetModel.getAllContents(), pnw.Transition.class), _function));
    final Consumer<Transition> _function_1 = (Transition t) -> {
      final Corr corr = this.getOrCreateCorrModelElement(t, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      pnw.Transition targetTransition = ((pnw.Transition) _targetElement);
      if ((targetTransition != null)) {
        unmatched.remove(targetTransition);
        String _get = Elem2Elem.corrToName.get(corr);
        String _name = t.getName();
        boolean _notEquals = (!Objects.equals(_get, _name));
        if (_notEquals) {
          targetTransition.setName(t.getName());
        } else {
          t.setName(targetTransition.getName());
        }
      } else {
        final Function1<pnw.Transition, Boolean> _function_2 = (pnw.Transition tt) -> {
          String _name_1 = tt.getName();
          String _name_2 = t.getName();
          return Boolean.valueOf(Objects.equals(_name_1, _name_2));
        };
        targetTransition = IterableExtensions.<pnw.Transition>findFirst(unmatched, _function_2);
        if ((targetTransition != null)) {
          corr.setTargetElement(targetTransition);
          Elem2Elem.elementsToCorr.put(targetTransition, corr);
          unmatched.remove(targetTransition);
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getTransition());
          final Procedure1<pnw.Transition> _function_3 = (pnw.Transition it) -> {
            it.setName(t.getName());
          };
          pnw.Transition _doubleArrow = ObjectExtensions.<pnw.Transition>operator_doubleArrow(((pnw.Transition) _orCreateTargetElem), _function_3);
          targetTransition = _doubleArrow;
          EObject _targetElement_1 = this.getCorrModelElem(t.getNet()).getTargetElement();
          final Net targetNet = ((Net) _targetElement_1);
          EList<NetElement> _elements = targetNet.getElements();
          _elements.add(targetTransition);
        }
      }
      Elem2Elem.corrToName.put(corr, t.getName());
      EObject _targetElement_2 = this.getCorrModelElem(t.getNet()).getTargetElement();
      this.reconcileEdges(t, targetTransition, ((Net) _targetElement_2));
    };
    transList.forEach(_function_1);
    final Consumer<pnw.Transition> _function_2 = (pnw.Transition tr) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tr, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getTransition());
      final Procedure1<Transition> _function_3 = (Transition it) -> {
        it.setName(tr.getName());
      };
      final Transition sourceTransition = ObjectExtensions.<Transition>operator_doubleArrow(((Transition) _orCreateSourceElem), _function_3);
      EObject _sourceElement = this.getCorrModelElem(tr.getNet()).getSourceElement();
      final pn.Net sourceNet = ((pn.Net) _sourceElement);
      EList<pn.NetElement> _elements = sourceNet.getElements();
      _elements.add(sourceTransition);
      Elem2Elem.corrToName.put(corr, sourceTransition.getName());
      final Consumer<PTEdge> _function_4 = (PTEdge ptEdge) -> {
        EList<Place> _srcP2T = sourceTransition.getSrcP2T();
        EObject _sourceElement_1 = this.getCorrModelElem(ptEdge.getFromPlace()).getSourceElement();
        _srcP2T.add(((Place) _sourceElement_1));
      };
      tr.getInPTEdges().forEach(_function_4);
      final Consumer<TPEdge> _function_5 = (TPEdge tpEdge) -> {
        EList<Place> _trgT2P = sourceTransition.getTrgT2P();
        EObject _sourceElement_1 = this.getCorrModelElem(tpEdge.getToPlace()).getSourceElement();
        _trgT2P.add(((Place) _sourceElement_1));
      };
      tr.getOutTPEdges().forEach(_function_5);
    };
    unmatched.forEach(_function_2);
  }
}
