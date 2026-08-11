package de.tbuchmann.bxtend.pn2pnw.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Pn2pnwFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import pnw.PTEdge;
import pnw.TPEdge;
import pnw.Transition;

/**
 * Orchestrates the complete bidirectional transformation between an unweighted
 * Petri net ({@code pn}) and a weighted Petri net ({@code pnw}).
 * 
 * <p>This class is the single public entry point for both transformation
 * directions.  It owns the three EMF resources required by every run:</p>
 * <ol>
 *   <li>{@code sourceModel} – the unweighted Petri net (PetriNet.ecore /
 *       {@code pn} package)</li>
 *   <li>{@code targetModel} – the weighted Petri net
 *       (PetriNetWeighted.ecore / {@code pnw} package)</li>
 *   <li>{@code corrModel}  – the correspondence model that tracks which
 *       source element is linked to which target element
 *       (corresp.ecore / {@code pn2pnw} package)</li>
 * </ol>
 * 
 * <p><b>Rule execution order:</b> Rules are registered in a fixed order that
 * guarantees that containers exist before their contents are processed:</p>
 * <ol>
 *   <li>{@link Net2Net} – synchronises the root {@code Net} objects.</li>
 *   <li>{@link Place2Place} – synchronises all {@code Place} elements.</li>
 *   <li>{@link Transition2Transition} – synchronises all {@code Transition}
 *       elements <em>and</em> the arcs between them and places.</li>
 * </ol>
 * 
 * <p><b>Deletion handling:</b> After all rules have run, the transformation
 * scans the correspondence model for entries whose source or target slot is
 * {@code null} (meaning the counterpart was deleted in the current edit) and
 * removes the dangling elements from the opposite model together with any
 * first-class edge objects that referenced the deleted transition.</p>
 * 
 * <p><b>Incremental support:</b> Because rules call
 * {@code getOrCreateCorrModelElement} / {@code getOrCreateSourceElem} /
 * {@code getOrCreateTargetElem}, an existing correspondence is reused on
 * subsequent runs, making the transformation naturally incremental:
 * only attributes and references that have actually changed need to be
 * updated.</p>
 */
@SuppressWarnings("all")
public class Pn2pnwTransformation {
  /**
   * The source-model resource (unweighted Petri net, {@code pn} package).
   */
  private Resource sourceModel;

  /**
   * The target-model resource (weighted Petri net, {@code pnw} package).
   */
  private Resource targetModel;

  /**
   * The correspondence-model resource (tracks source↔target element pairs,
   * {@code pn2pnw} package).
   */
  private Resource corrModel;

  /**
   * Ordered list of transformation rules executed in each pass.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * URI-based constructor: loads all three model resources from the given
   * URIs using a fresh {@link ResourceSetImpl} and registers the rules in
   * the required execution order.
   * 
   * <p>If the correspondence resource is empty (first run), an empty
   * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation
   * Transformation} root object is added before any rule is instantiated.</p>
   * 
   * @param source         URI of the source model (unweighted Petri net)
   * @param target         URI of the target model (weighted Petri net)
   * @param correspondence URI of the correspondence model
   */
  public Pn2pnwTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Pn2pnwFactory.eINSTANCE.createTransformation());
    }
    Net2Net _net2Net = new Net2Net(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_net2Net);
    Place2Place _place2Place = new Place2Place(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_place2Place);
    Transition2Transition _transition2Transition = new Transition2Transition(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_transition2Transition);
  }

  /**
   * Resource-based constructor: uses already-loaded EMF {@link Resource}
   * instances (e.g. when the caller manages the {@link ResourceSet} itself,
   * as in the BenchmarX test harness).
   * 
   * <p>If the correspondence resource is empty (first run), an empty
   * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation
   * Transformation} root object is added before any rule is instantiated.</p>
   * 
   * @param source         loaded source-model resource
   * @param target         loaded target-model resource
   * @param correspondence loaded correspondence-model resource
   */
  public Pn2pnwTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Pn2pnwFactory.eINSTANCE.createTransformation());
    }
    Net2Net _net2Net = new Net2Net(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_net2Net);
    Place2Place _place2Place = new Place2Place(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_place2Place);
    Transition2Transition _transition2Transition = new Transition2Transition(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_transition2Transition);
  }

  /**
   * Runs the forward transformation (source → target).
   * 
   * <p>Executes each rule's {@link Elem2Elem#sourceToTarget()} method in
   * registration order, then invokes {@link #deleteUnreferencedTargetElements()}
   * to clean up target elements whose source counterpart has been deleted.</p>
   * 
   * <p>If the source model is empty the method returns immediately without
   * modifying the target.</p>
   */
  public void sourceToTarget() {
    int _size = this.sourceModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rules) {
        e.sourceToTarget();
      }
    }
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Runs the backward transformation (target → source).
   * 
   * <p>Executes each rule's {@link Elem2Elem#targetToSource()} method in
   * registration order, then invokes {@link #deleteUnreferencedSourceElements()}
   * to clean up source elements whose target counterpart has been deleted.</p>
   * 
   * <p>If the target model is empty the method returns immediately without
   * modifying the source.</p>
   */
  public void targetToSource() {
    int _size = this.targetModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rules) {
        e.targetToSource();
      }
    }
    this.deleteUnreferencedSourceElements();
  }

  /**
   * Runs the synchronisation pass, reconciling concurrent edits made to both the
   * unweighted and weighted Petri net since the last synchronisation point.
   * 
   * <p>Executes each rule's {@link Elem2Elem#synch()} in the same registration order as
   * {@link #sourceToTarget()}/{@link #targetToSource()}, then cleans up dangling
   * correspondences on both sides.</p>
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Placeholder for a post-transformation consistency check.
   * 
   * @return {@code true} always (not yet implemented)
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Detects correspondences whose <em>source</em> element slot is {@code null},
   * i.e. target elements that have lost their source counterpart (indicating a
   * deletion on the source side during an incremental forward run).
   * 
   * @return a lazy iterator over {@link Corr} entries with a {@code null}
   *         {@code sourceElement}
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Detects correspondences whose <em>target</em> element slot is {@code null},
   * i.e. source elements that have lost their target counterpart (indicating a
   * deletion on the target side during an incremental backward run).
   * 
   * @return a lazy iterator over {@link Corr} entries with a {@code null}
   *         {@code targetElement}
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Deletes target elements that are no longer referenced by any source element
   * (forward deletion propagation).
   * 
   * <p>For each correspondence with a {@code null} source element, the
   * corresponding target element is scheduled for deletion.  If the target
   * element is a {@link Transition}, its incident {@link pnw.PTEdge} and
   * {@link pnw.TPEdge} objects are also scheduled for deletion first, so
   * that edge containment is properly cleaned up before the transition is
   * removed.  The correspondence entry itself is deleted as well.</p>
   * 
   * <p>All deletions are performed via {@link EcoreUtil#delete(EObject, boolean)}
   * with {@code recursive = true} to handle any remaining cross-references.</p>
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      if ((_targetElement instanceof Transition)) {
        EObject _targetElement_1 = c.getTargetElement();
        EList<PTEdge> _inPTEdges = ((Transition) _targetElement_1).getInPTEdges();
        for (final PTEdge edge : _inPTEdges) {
          deletionList.add(edge);
        }
        EObject _targetElement_2 = c.getTargetElement();
        EList<TPEdge> _outTPEdges = ((Transition) _targetElement_2).getOutTPEdges();
        for (final TPEdge edge_1 : _outTPEdges) {
          deletionList.add(edge_1);
        }
      }
      EObject _targetElement_3 = c.getTargetElement();
      deletionList.add(_targetElement_3);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Deletes source elements that are no longer referenced by any target element
   * (backward deletion propagation).
   * 
   * <p>For each correspondence with a {@code null} target element, the
   * corresponding source element and the correspondence entry itself are
   * scheduled for deletion.  Deletions are performed via
   * {@link EcoreUtil#delete(EObject, boolean)} with {@code recursive = true}.</p>
   */
  public void deleteUnreferencedSourceElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      deletionList.add(_sourceElement);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectTargetDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }
}
