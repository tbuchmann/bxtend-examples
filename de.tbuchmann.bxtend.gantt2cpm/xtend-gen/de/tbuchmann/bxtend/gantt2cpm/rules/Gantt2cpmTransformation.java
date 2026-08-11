package de.tbuchmann.bxtend.gantt2cpm.rules;

import com.google.common.collect.Iterators;
import cpm.Event;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Gantt2cpmFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
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

/**
 * Top-level orchestrator for the Gantt ↔ CPM bidirectional, incremental model
 * transformation implemented with BXtend.
 * 
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Loads (or accepts) the three EMF resources: source (Gantt), target (CPM),
 *       and correspondence model.</li>
 *   <li>Bootstraps an empty {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
 *       root object in the correspondence resource if none is present yet.</li>
 *   <li>Instantiates and chains the transformation rules in the correct
 *       execution order:
 *       <ol>
 *         <li>{@link Diagram2Network} – must run first to establish the root
 *             container correspondence before child rules access it.</li>
 *         <li>{@link Activity2Activity} – must run before {@link Dependency2Activity}
 *             because dependency wiring requires the CPM activities (and their
 *             events) to already exist in the correspondence model.</li>
 *         <li>{@link Dependency2Activity} – runs last; wires dependency arcs
 *             between already-resolved CPM events.</li>
 *       </ol>
 *   </li>
 *   <li>Exposes {@link #sourceToTarget()} and {@link #targetToSource()} as the
 *       sole public API – each method drives the complete rule chain and then
 *       triggers deletion propagation.</li>
 * </ul>
 * 
 * <p><b>Incrementality &amp; deletion propagation:</b> After each rule-chain pass,
 * the orchestrator scans the correspondence model for "dangling" {@link Corr}
 * entries – i.e. entries whose source or target reference has become {@code null}
 * because the corresponding model element was deleted from one side.  The orphaned
 * element on the other side (and any isolated {@link Event} nodes) is then removed
 * via {@link EcoreUtil#delete(EObject, boolean)}.</p>
 * 
 * <p><b>Two constructors:</b> One constructor accepts file {@link URI}s and loads
 * the resources itself (standalone use); the second accepts pre-loaded
 * {@link Resource} objects (used by the Benchmarx adapter
 * {@code BXtendGantt2CPM}, which manages the resource set externally).</p>
 */
@SuppressWarnings("all")
public class Gantt2cpmTransformation {
  /**
   * EMF resource holding the source (Gantt) model.
   */
  private Resource sourceModel;

  /**
   * EMF resource holding the target (CPM) model.
   */
  private Resource targetModel;

  /**
   * EMF resource holding the correspondence model.
   */
  private Resource corrModel;

  /**
   * Ordered list of transformation rules.  Rules are executed in insertion
   * order during both {@link #sourceToTarget()} and {@link #targetToSource()}.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * Constructs the transformation from file URIs, loading all three resources
   * into a fresh {@link ResourceSetImpl}.
   * 
   * <p>If the correspondence resource is empty (first run), a root
   * {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
   * object is created automatically.</p>
   * 
   * @param source       URI of the Gantt model XMI file
   * @param target       URI of the CPM model XMI file
   * @param correspondence URI of the correspondence model XMI file
   */
  public Gantt2cpmTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Gantt2cpmFactory.eINSTANCE.createTransformation());
    }
    Diagram2Network _diagram2Network = new Diagram2Network(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_diagram2Network);
    Activity2Activity _activity2Activity = new Activity2Activity(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_activity2Activity);
    Dependency2Activity _dependency2Activity = new Dependency2Activity(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_dependency2Activity);
  }

  /**
   * Constructs the transformation from already-loaded EMF {@link Resource} objects.
   * 
   * <p>This constructor is used by the Benchmarx adapter {@code BXtendGantt2CPM},
   * which manages its own {@code ResourceSet} and passes in the three resources
   * directly.  If the correspondence resource is empty (first run), a root
   * {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
   * object is created automatically.</p>
   * 
   * @param source        pre-loaded Gantt model resource
   * @param target        pre-loaded CPM model resource
   * @param correspondence pre-loaded correspondence model resource
   */
  public Gantt2cpmTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Gantt2cpmFactory.eINSTANCE.createTransformation());
    }
    Diagram2Network _diagram2Network = new Diagram2Network(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_diagram2Network);
    Activity2Activity _activity2Activity = new Activity2Activity(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_activity2Activity);
    Dependency2Activity _dependency2Activity = new Dependency2Activity(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_dependency2Activity);
  }

  /**
   * Drives the forward propagation pass (Gantt → CPM).
   * 
   * <p>Executes each rule's {@link Elem2Elem#sourceToTarget()} in order, then
   * calls {@link #deleteUnreferencedTargetElements()} to remove CPM elements
   * whose Gantt counterpart was deleted.</p>
   * 
   * <p>The pass is skipped entirely when the source model is empty
   * (i.e. {@code sourceModel.contents} is empty) to avoid
   * {@code IndexOutOfBoundsException} on the root access in
   * {@link Diagram2Network}.</p>
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
   * Drives the backward propagation pass (CPM → Gantt).
   * 
   * <p>Executes each rule's {@link Elem2Elem#targetToSource()} in order, then
   * calls {@link #deleteUnreferencedSourceElements()} to remove Gantt elements
   * whose CPM counterpart was deleted.</p>
   * 
   * <p>The pass is skipped entirely when the target model is empty.</p>
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
   * Drives the synchronisation pass, reconciling concurrent edits made to both
   * the Gantt and the CPM model since the last synchronisation point.
   * 
   * <p>Executes each rule's {@link Elem2Elem#synch()} in order (the same order
   * used for {@link #sourceToTarget()}/{@link #targetToSource()}, since
   * {@link Dependency2Activity} depends on correspondences already established
   * by {@link Activity2Activity}), then cleans up dangling correspondences on
   * both sides.
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Verifies that the correspondence model is in a consistent state.
   * 
   * <p>Currently always returns {@code true}; reserved for future consistency
   * checks (e.g. verifying that every {@link Corr} has both a source and a
   * target element after a synchronisation pass).</p>
   * 
   * @return {@code true} if the correspondence model is consistent
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Collects all {@link Corr} entries in the correspondence model whose
   * {@code sourceElement} reference is {@code null}.
   * 
   * <p>A {@code null} source element indicates that the corresponding Gantt
   * element was deleted, either by the user or by a previous propagation pass.
   * EMF automatically sets cross-resource references to {@code null} when the
   * referenced object is removed from its resource via
   * {@link EcoreUtil#delete(EObject, boolean)}.</p>
   * 
   * @return a lazy iterator over dangling {@link Corr} entries (source side deleted)
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Collects all {@link Corr} entries in the correspondence model whose
   * {@code targetElement} reference is {@code null}.
   * 
   * <p>A {@code null} target element indicates that the corresponding CPM
   * element was deleted.</p>
   * 
   * @return a lazy iterator over dangling {@link Corr} entries (target side deleted)
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Removes CPM elements that have become orphaned because their corresponding
   * Gantt source element was deleted.
   * 
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Iterate over all {@link Corr} entries detected by
   *       {@link #detectSourceDeletions()}.</li>
   *   <li>Collect the orphaned CPM target elements and the dangling {@code Corr}
   *       objects into a deletion list.</li>
   *   <li>Delete everything in the list via {@link EcoreUtil#delete(EObject, boolean)}
   *       (cascades into contained children).</li>
   *   <li>After activity deletion, perform a second pass to remove any
   *       {@link Event} nodes that have no remaining incoming or outgoing
   *       activity arcs (isolated events that would otherwise leave a
   *       structurally invalid CPM network).</li>
   * </ol>
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      deletionList.add(_targetElement);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
    final Function1<Event, Boolean> _function_2 = (Event e) -> {
      return Boolean.valueOf(((e.getOutgoingActivities().size() == 0) && (e.getIncomingActivities().size() == 0)));
    };
    final List<Event> elemsToDelete = IteratorExtensions.<Event>toList(IteratorExtensions.<Event>filter(Iterators.<Event>filter(this.targetModel.getAllContents(), Event.class), _function_2));
    final Consumer<Event> _function_3 = (Event ev) -> {
      EcoreUtil.delete(ev, true);
    };
    elemsToDelete.forEach(_function_3);
  }

  /**
   * Removes Gantt elements that have become orphaned because their corresponding
   * CPM target element was deleted.
   * 
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Iterate over all {@link Corr} entries detected by
   *       {@link #detectTargetDeletions()}.</li>
   *   <li>Collect the orphaned Gantt source elements and the dangling {@code Corr}
   *       objects into a deletion list.</li>
   *   <li>Delete everything in the list via {@link EcoreUtil#delete(EObject, boolean)}.</li>
   * </ol>
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
