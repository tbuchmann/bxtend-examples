package de.tbuchmann.bxtend.gantt2cpm.rules;

import com.google.common.collect.Iterators;
import cpm.CPMNetwork;
import cpm.Element;
import cpm.Event;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr;
import gantt.Activity;
import gantt.GanttDiagram;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Transformation rule that maps a {@code gantt.Activity} to a {@code cpm.Activity}.
 * 
 * <p><b>Metamodel correspondence:</b></p>
 * <pre>
 *   gantt.Activity  ←→  cpm.Activity
 *     name              name
 *     duration          duration
 *     (container: GanttDiagram)  ←→  (container: CPMNetwork)
 * </pre>
 * 
 * <p><b>Structural mismatch:</b> In the CPM metamodel every {@code Activity} is an
 * arc in a directed graph whose nodes are {@link Event} objects.  Consequently,
 * creating a new CPM {@code Activity} always requires two fresh {@code Event}
 * instances – a <em>source event</em> (predecessor node) and a <em>target event</em>
 * (successor node).  This rule overrides {@link #getOrCreateTargetElem} to
 * handle this 1-to-3 creation atomically.</p>
 * 
 * <p><b>Naming convention:</b> CPM activities whose {@code name} contains
 * {@code "->"} represent Gantt {@link gantt.Dependency} elements and are therefore
 * <em>excluded</em> from the backward pass in this rule (they are handled by
 * {@link Dependency2Activity} instead).  The forward pass applies the same
 * exclusion implicitly, because it only iterates over {@code gantt.Activity}
 * instances.</p>
 * 
 * <p><b>Event numbering:</b> A static counter {@code i} tracks the highest
 * event number already present in the target model so that new events always
 * receive a unique, monotonically increasing {@code number}.</p>
 */
@SuppressWarnings("all")
public class Activity2Activity extends Elem2Elem {
  /**
   * Monotonically increasing counter used to assign unique numbers to newly
   * created {@link Event} instances.  On the first call to {@link #createEvent}
   * within a JVM session the counter is initialised by scanning all existing
   * events in the target model so that new events never clash with persisted ones.
   */
  private static int i = 0;

  /**
   * Constructs the rule and registers it under the rule identifier
   * {@code "activity"}, which is stored in every {@link Corr} created by
   * this rule.
   * 
   * @param src  EMF resource holding the source (Gantt) model
   * @param trgt EMF resource holding the target (CPM) model
   * @param corr EMF resource holding the correspondence model
   */
  public Activity2Activity(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "activity";
  }

  /**
   * Propagates all {@code gantt.Activity} elements to the CPM target model
   * (forward direction: Gantt → CPM).
   * 
   * <p>For each {@code gantt.Activity} {@code a}:</p>
   * <ol>
   *   <li>Look up or create a {@link Corr} correspondence entry for {@code a}
   *       (keyed by rule ID {@code "activity"}).</li>
   *   <li>Look up or create the matching {@code cpm.Activity} via
   *       {@link #getOrCreateTargetElem}, which also creates the two bounding
   *       {@link Event} instances on first call.</li>
   *   <li>Synchronise attributes: {@code name} and {@code duration}.</li>
   *   <li>Ensure the activity and both its events are contained in the
   *       {@link CPMNetwork} that corresponds to the Gantt diagram containing
   *       {@code a}.  The network is resolved via the correspondence model.</li>
   * </ol>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Activity> _function = (Activity a) -> {
      final Corr corr = this.getOrCreateCorrModelElement(a, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getActivity());
      final cpm.Activity target = ((cpm.Activity) _orCreateTargetElem);
      target.setName(a.getName());
      target.setDuration(a.getDuration());
      EObject _targetElement = this.getCorrModelElem(a.getDiagram()).getTargetElement();
      final CPMNetwork net = ((CPMNetwork) _targetElement);
      EList<Element> _elements = net.getElements();
      _elements.add(target);
      EList<Element> _elements_1 = net.getElements();
      Event _sourceEvent = target.getSourceEvent();
      _elements_1.add(_sourceEvent);
      EList<Element> _elements_2 = net.getElements();
      Event _targetEvent = target.getTargetEvent();
      _elements_2.add(_targetEvent);
    };
    IteratorExtensions.<Activity>forEach(Iterators.<Activity>filter(this.sourceModel.getAllContents(), Activity.class), _function);
  }

  /**
   * Propagates {@code cpm.Activity} elements back to the Gantt source model
   * (backward direction: CPM → Gantt).
   * 
   * <p>Only CPM activities whose {@code name} does <em>not</em> contain
   * {@code "->"} are processed here; those are plain Gantt activities.
   * Activities with an arrow in their name represent Gantt dependencies and
   * are handled by {@link Dependency2Activity}.</p>
   * 
   * <p>For each qualifying {@code cpm.Activity} {@code a}:</p>
   * <ol>
   *   <li>Look up or create a {@link Corr} correspondence entry for {@code a}.</li>
   *   <li>Look up or create the matching {@code gantt.Activity}.</li>
   *   <li>Synchronise attributes: {@code name} and {@code duration}.</li>
   *   <li>Place the activity in the {@link GanttDiagram} that corresponds to
   *       the CPM network containing {@code a}.</li>
   * </ol>
   */
  @Override
  public void targetToSource() {
    final Function1<cpm.Activity, Boolean> _function = (cpm.Activity a) -> {
      boolean _contains = a.getName().contains("->");
      return Boolean.valueOf((!_contains));
    };
    final Procedure1<cpm.Activity> _function_1 = (cpm.Activity a) -> {
      final Corr corr = this.getOrCreateCorrModelElement(a, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getActivity());
      final Activity source = ((Activity) _orCreateSourceElem);
      source.setName(a.getName());
      source.setDuration(a.getDuration());
      EObject _sourceElement = this.getCorrModelElem(a.getNetwork()).getSourceElement();
      final GanttDiagram diag = ((GanttDiagram) _sourceElement);
      EList<gantt.Element> _elements = diag.getElements();
      _elements.add(source);
    };
    IteratorExtensions.<cpm.Activity>forEach(IteratorExtensions.<cpm.Activity>filter(Iterators.<cpm.Activity>filter(this.targetModel.getAllContents(), cpm.Activity.class), _function), _function_1);
  }

  /**
   * Overrides the default target-element creation to implement the
   * 1-to-3 mapping: one Gantt {@code Activity} maps to one CPM
   * {@code Activity} plus two bounding {@link Event} instances.
   * 
   * <p>If the {@link Corr} already holds a target element, that element is
   * returned unchanged (incremental update path).  On first creation the
   * new {@code Activity} is linked to two freshly created events via
   * {@code sourceEvent} and {@code targetEvent}.</p>
   * 
   * @param corr  the correspondence entry for the element being processed
   * @param clazz the target {@code EClass} (always {@code cpm.Activity} here)
   * @return the existing or newly created {@code cpm.Activity}
   */
  @Override
  public EObject getOrCreateTargetElem(final Corr corr, final EClass clazz) {
    EObject _targetElement = corr.getTargetElement();
    cpm.Activity target = ((cpm.Activity) _targetElement);
    if ((target == null)) {
      EObject _createTargetElement = this.createTargetElement(clazz);
      target = ((cpm.Activity) _createTargetElement);
      corr.setTargetElement(target);
      target.setSourceEvent(this.createEvent());
      target.setTargetEvent(this.createEvent());
    }
    return target;
  }

  /**
   * Creates a new {@link Event} with a unique, auto-incremented {@code number}.
   * 
   * <p>The static counter {@code i} is lazily initialised on the very first
   * call by scanning all existing {@link Event} instances in the target model
   * and recording the current maximum number.  Subsequent calls simply
   * increment and use the counter without re-scanning, ensuring O(1) cost
   * for all but the first invocation per JVM session.</p>
   * 
   * @return a new {@code cpm.Event} with a number greater than any
   *         previously assigned event number
   */
  private Event createEvent() {
    if ((Activity2Activity.i == 0)) {
      final Procedure1<Event> _function = (Event e) -> {
        int _number = e.getNumber();
        boolean _greaterThan = (_number > Activity2Activity.i);
        if (_greaterThan) {
          Activity2Activity.i = e.getNumber();
        }
      };
      IteratorExtensions.<Event>forEach(Iterators.<Event>filter(this.targetModel.getAllContents(), Event.class), _function);
    }
    Activity2Activity.i++;
    Event _createEvent = this.targetFactory.createEvent();
    final Procedure1<Event> _function_1 = (Event it) -> {
      it.setNumber(Activity2Activity.i);
    };
    Event e = ObjectExtensions.<Event>operator_doubleArrow(_createEvent, _function_1);
    return e;
  }
}
