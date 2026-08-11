package de.tbuchmann.bxtend.gantt2cpm.rules;

import com.google.common.collect.Iterators;
import cpm.Activity;
import cpm.CPMNetwork;
import cpm.Element;
import cpm.Event;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr;
import gantt.Dependency;
import gantt.DependencyType;
import gantt.GanttDiagram;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Transformation rule that maps a {@code gantt.Dependency} to a {@code cpm.Activity}.
 * 
 * <p><b>The structural mismatch:</b> In a Gantt diagram, a {@link gantt.Dependency}
 * is an explicit edge object connecting two {@link gantt.Activity} nodes.  In the
 * CPM metamodel there are no explicit edge objects; instead, every scheduling
 * relationship between two milestone {@link cpm.Event}s is itself represented as
 * a {@code cpm.Activity} arc.  Therefore, one {@code gantt.Dependency} is mapped
 * to one {@code cpm.Activity} whose {@code sourceEvent} and {@code targetEvent}
 * are shared with the CPM activities that correspond to the Gantt predecessor and
 * successor activities.</p>
 * 
 * <p><b>Naming convention:</b> The CPM activity produced by this rule is named
 * {@code "<predecessorName>-><successorName>"}, e.g. {@code "Design->Build"}.
 * This "arrow" pattern distinguishes dependency activities from plain activities
 * and is relied upon by {@link Activity2Activity} to exclude them from that
 * rule's backward pass.</p>
 * 
 * <p><b>Dependency type mapping:</b> Gantt supports four dependency types that
 * describe which milestone events the dependency arc connects:</p>
 * <pre>
 *   StartStart → sourceEvent of predecessor  →  sourceEvent of successor
 *   StartEnd   → sourceEvent of predecessor  →  targetEvent of successor
 *   EndStart   → targetEvent of predecessor  →  sourceEvent of successor
 *   EndEnd     → targetEvent of predecessor  →  targetEvent of successor
 * </pre>
 * 
 * <p><b>Duration / offset:</b> The {@code offset} attribute of a Gantt
 * {@code Dependency} (a lag or lead time in days) is stored in the CPM activity's
 * {@code duration} attribute and round-tripped without loss.</p>
 */
@SuppressWarnings("all")
public class Dependency2Activity extends Elem2Elem {
  /**
   * Constructs the rule and registers it under the rule identifier
   * {@code "dependency"}.  This ID is persisted in every {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
   * created by this rule and is used during deletion detection to distinguish
   * dependency correspondences from activity correspondences.
   * 
   * @param src  EMF resource holding the source (Gantt) model
   * @param trgt EMF resource holding the target (CPM) model
   * @param corr EMF resource holding the correspondence model
   */
  public Dependency2Activity(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "dependency";
  }

  /**
   * Propagates all {@code gantt.Dependency} elements to the CPM target model
   * (forward direction: Gantt → CPM).
   * 
   * <p>For each {@code gantt.Dependency} {@code d}:</p>
   * <ol>
   *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr} for {@code d}.</li>
   *   <li>Look up or create the corresponding {@code cpm.Activity}.</li>
   *   <li>Resolve the CPM activities for the Gantt predecessor and successor
   *       via the correspondence map, then wire the {@code sourceEvent} and
   *       {@code targetEvent} of the new CPM dependency activity according to
   *       the {@link DependencyType}.</li>
   *   <li>Set the CPM activity name to {@code "<pred>-><succ>"} and its
   *       {@code duration} to {@code d.offset}.</li>
   *   <li>Place the CPM activity into the {@link CPMNetwork} corresponding to
   *       the Gantt diagram that owns {@code d}.</li>
   * </ol>
   * 
   * <p><b>Pre-condition:</b> {@link Activity2Activity#sourceToTarget()} must have
   * already run so that every {@code gantt.Activity} referenced by {@code d} has
   * a corresponding {@code cpm.Activity} (and therefore populated events) in the
   * correspondence model.</p>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Dependency> _function = (Dependency d) -> {
      Corr corr = this.getOrCreateCorrModelElement(d, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getActivity());
      Activity target = ((Activity) _orCreateTargetElem);
      this.updateDependencyTarget(target, d);
      target.setDuration(d.getOffset());
      EObject _targetElement = this.getCorrModelElem(d.getDiagram()).getTargetElement();
      final CPMNetwork net = ((CPMNetwork) _targetElement);
      boolean _contains = net.getElements().contains(target);
      boolean _not = (!_contains);
      if (_not) {
        EList<Element> _elements = net.getElements();
        _elements.add(target);
      }
      Elem2Elem.corrToName.put(corr, target.getName());
      Elem2Elem.corrToDuration.put(corr, Integer.valueOf(target.getDuration()));
    };
    IteratorExtensions.<Dependency>forEach(Iterators.<Dependency>filter(this.sourceModel.getAllContents(), Dependency.class), _function);
  }

  /**
   * Wires {@code target}'s {@code sourceEvent}/{@code targetEvent} (from the
   * predecessor/successor CPM activities, according to {@code d.dependencyType})
   * and its {@code name}.  Does <em>not</em> touch {@code duration}/{@code offset} —
   * that attribute is independent of the predecessor/successor identity and is
   * resolved separately by each caller via {@link #corrToDuration}.  Shared by
   * {@link #sourceToTarget()} and {@link #synch()} so the forward-direction
   * mapping logic exists in one place.
   * 
   * @param target the CPM dependency activity to update
   * @param d      the Gantt dependency providing the new attribute values
   */
  private void updateDependencyTarget(final Activity target, final Dependency d) {
    final gantt.Activity sourceActivity = d.getPredecessor();
    final gantt.Activity targetActivity = d.getSuccessor();
    EObject _targetElement = this.getCorrModelElem(sourceActivity).getTargetElement();
    final Activity cpmSourceActivity = ((Activity) _targetElement);
    EObject _targetElement_1 = this.getCorrModelElem(targetActivity).getTargetElement();
    final Activity cpmTargetActivity = ((Activity) _targetElement_1);
    DependencyType _dependencyType = d.getDependencyType();
    boolean _equals = Objects.equals(_dependencyType, DependencyType.START_START);
    if (_equals) {
      target.setSourceEvent(cpmSourceActivity.getSourceEvent());
      target.setTargetEvent(cpmTargetActivity.getSourceEvent());
    } else {
      DependencyType _dependencyType_1 = d.getDependencyType();
      boolean _equals_1 = Objects.equals(_dependencyType_1, DependencyType.START_END);
      if (_equals_1) {
        target.setSourceEvent(cpmSourceActivity.getSourceEvent());
        target.setTargetEvent(cpmTargetActivity.getTargetEvent());
      } else {
        DependencyType _dependencyType_2 = d.getDependencyType();
        boolean _equals_2 = Objects.equals(_dependencyType_2, DependencyType.END_START);
        if (_equals_2) {
          target.setSourceEvent(cpmSourceActivity.getTargetEvent());
          target.setTargetEvent(cpmTargetActivity.getSourceEvent());
        } else {
          target.setSourceEvent(cpmSourceActivity.getTargetEvent());
          target.setTargetEvent(cpmTargetActivity.getTargetEvent());
        }
      }
    }
    String _name = sourceActivity.getName();
    String _plus = (_name + "->");
    String _name_1 = targetActivity.getName();
    String _plus_1 = (_plus + _name_1);
    target.setName(_plus_1);
  }

  /**
   * Propagates {@code cpm.Activity} dependency arcs back to the Gantt source
   * model (backward direction: CPM → Gantt).
   * 
   * <p>Only CPM activities whose {@code name} contains {@code "->"} are
   * processed here (plain activities are handled by {@link Activity2Activity}).
   * The arrow-shaped name encodes both the predecessor and successor activity
   * names, which are used to locate the corresponding CPM activities via
   * {@link #findCPMActivity}.</p>
   * 
   * <p>For each qualifying {@code cpm.Activity} {@code a}:</p>
   * <ol>
   *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr} for {@code a}.</li>
   *   <li>Look up or create the corresponding {@code gantt.Dependency}.</li>
   *   <li>Determine the {@link DependencyType} by comparing the CPM source/target
   *       events of the dependency arc against the source/target events of the
   *       predecessor and successor CPM activities.</li>
   *   <li>Set the Gantt dependency's {@code predecessor}, {@code successor},
   *       {@code offset}, and {@code diagram} references.</li>
   * </ol>
   * 
   * <p><b>Pre-condition:</b> {@link Activity2Activity#targetToSource()} must have
   * already run so that every referenced {@code cpm.Activity} has a corresponding
   * {@code gantt.Activity} in the correspondence model.</p>
   */
  @Override
  public void targetToSource() {
    final Function1<Activity, Boolean> _function = (Activity a) -> {
      return Boolean.valueOf(a.getName().contains("->"));
    };
    final Procedure1<Activity> _function_1 = (Activity a) -> {
      Corr corr = this.getOrCreateCorrModelElement(a, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getDependency());
      Dependency sourceDependency = ((Dependency) _orCreateSourceElem);
      this.updateDependencySource(sourceDependency, a);
      sourceDependency.setOffset(a.getDuration());
      Elem2Elem.corrToName.put(corr, a.getName());
      Elem2Elem.corrToDuration.put(corr, Integer.valueOf(sourceDependency.getOffset()));
    };
    IteratorExtensions.<Activity>forEach(IteratorExtensions.<Activity>filter(Iterators.<Activity>filter(this.targetModel.getAllContents(), Activity.class), _function), _function_1);
  }

  /**
   * Reverse-engineers {@code d}'s {@code dependencyType}, {@code predecessor},
   * {@code successor} and {@code diagram} from the CPM dependency activity
   * {@code a} (whose arrow-shaped name encodes predecessor and successor).
   * Does <em>not</em> touch {@code offset} — see {@link #updateDependencyTarget}
   * for why that attribute is resolved independently by each caller. Shared by
   * {@link #targetToSource()} and {@link #synch()}.
   * 
   * @param d the Gantt dependency to update
   * @param a the CPM dependency activity providing the new attribute values
   */
  private void updateDependencySource(final Dependency d, final Activity a) {
    final Event cpmSourceEvent = a.getSourceEvent();
    final Event cpmTargetEvent = a.getTargetEvent();
    final Activity cpmSourceActivity = this.findCPMActivity(a.getName().split("->")[0]);
    final Activity cpmTargetActivity = this.findCPMActivity(a.getName().split("->")[1]);
    final EObject ganttSourceActivity = this.getCorrModelElem(cpmSourceActivity).getSourceElement();
    final EObject ganttTargetActivity = this.getCorrModelElem(cpmTargetActivity).getSourceElement();
    if ((Objects.equals(cpmSourceActivity.getSourceEvent(), cpmSourceEvent) && Objects.equals(cpmTargetActivity.getSourceEvent(), cpmTargetEvent))) {
      d.setDependencyType(DependencyType.START_START);
    } else {
      if ((Objects.equals(cpmSourceActivity.getSourceEvent(), cpmSourceEvent) && Objects.equals(cpmTargetActivity.getTargetEvent(), cpmTargetEvent))) {
        d.setDependencyType(DependencyType.START_END);
      } else {
        if ((Objects.equals(cpmSourceActivity.getTargetEvent(), cpmSourceEvent) && Objects.equals(cpmTargetActivity.getSourceEvent(), cpmTargetEvent))) {
          d.setDependencyType(DependencyType.END_START);
        } else {
          d.setDependencyType(DependencyType.END_END);
        }
      }
    }
    d.setPredecessor(((gantt.Activity) ganttSourceActivity));
    d.setSuccessor(((gantt.Activity) ganttTargetActivity));
    EObject _sourceElement = this.getCorrModelElem(a.getNetwork()).getSourceElement();
    d.setDiagram(((GanttDiagram) _sourceElement));
  }

  /**
   * Reconciles concurrent edits to {@code gantt.Dependency} ↔ {@code cpm.Activity}
   * (arrow-named) pairs.
   * 
   * <p>Identity key: {@code "<predecessorName>-><successorName>"}. Requires
   * {@link Activity2Activity#synch()} to have already run in the same pass so
   * that {@code d.predecessor}/{@code d.successor} already have CPM-side
   * correspondences.</p>
   * 
   * <p>Algorithm, mirroring {@link Activity2Activity#synch()}: link/re-link by
   * key, push the structural fields (events/name) forward on a key change or
   * pull them backward otherwise, resolve {@code offset}/{@code duration}
   * independently against {@link #corrToDuration} (same push/pull/source-wins
   * logic as {@link Activity2Activity#synch()}, since it can change on either
   * side without affecting the key), and turn any still-unmatched CPM
   * dependency activity into a new Gantt dependency.</p>
   */
  @Override
  public void synch() {
    final List<Dependency> depList = IteratorExtensions.<Dependency>toList(Iterators.<Dependency>filter(this.sourceModel.getAllContents(), Dependency.class));
    final Function1<Activity, Boolean> _function = (Activity a) -> {
      return Boolean.valueOf(a.getName().contains("->"));
    };
    final Function1<Activity, Boolean> _function_1 = (Activity a) -> {
      Corr _corrModelElem = this.getCorrModelElem(a);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<Activity> unmatched = IteratorExtensions.<Activity>toList(IteratorExtensions.<Activity>filter(IteratorExtensions.<Activity>filter(Iterators.<Activity>filter(this.targetModel.getAllContents(), Activity.class), _function), _function_1));
    final Consumer<Dependency> _function_2 = (Dependency d) -> {
      final Corr corr = this.getOrCreateCorrModelElement(d, this.ruleID);
      String _name = d.getPredecessor().getName();
      String _plus = (_name + "->");
      String _name_1 = d.getSuccessor().getName();
      final String key = (_plus + _name_1);
      EObject _targetElement = corr.getTargetElement();
      Activity target = ((Activity) _targetElement);
      if ((target != null)) {
        unmatched.remove(target);
        String _get = Elem2Elem.corrToName.get(corr);
        boolean _notEquals = (!Objects.equals(_get, key));
        if (_notEquals) {
          this.updateDependencyTarget(target, d);
        } else {
          this.updateDependencySource(d, target);
        }
        final Integer lastDuration = Elem2Elem.corrToDuration.get(corr);
        final boolean sourceChanged = ((lastDuration == null) || ((lastDuration).intValue() != d.getOffset()));
        final boolean targetChanged = ((lastDuration == null) || ((lastDuration).intValue() != target.getDuration()));
        if (sourceChanged) {
          target.setDuration(d.getOffset());
        } else {
          if (targetChanged) {
            d.setOffset(target.getDuration());
          }
        }
      } else {
        final Function1<Activity, Boolean> _function_3 = (Activity t) -> {
          String _name_2 = t.getName();
          return Boolean.valueOf(Objects.equals(_name_2, key));
        };
        target = IterableExtensions.<Activity>findFirst(unmatched, _function_3);
        if ((target != null)) {
          corr.setTargetElement(target);
          Elem2Elem.elementsToCorr.put(target, corr);
          unmatched.remove(target);
          this.updateDependencyTarget(target, d);
          target.setDuration(d.getOffset());
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getActivity());
          target = ((Activity) _orCreateTargetElem);
          this.updateDependencyTarget(target, d);
          target.setDuration(d.getOffset());
          EObject _targetElement_1 = this.getCorrModelElem(d.getDiagram()).getTargetElement();
          final CPMNetwork net = ((CPMNetwork) _targetElement_1);
          boolean _contains = net.getElements().contains(target);
          boolean _not = (!_contains);
          if (_not) {
            EList<Element> _elements = net.getElements();
            _elements.add(target);
          }
        }
      }
      Elem2Elem.corrToName.put(corr, key);
      Elem2Elem.corrToDuration.put(corr, Integer.valueOf(target.getDuration()));
    };
    depList.forEach(_function_2);
    final Consumer<Activity> _function_3 = (Activity a) -> {
      final Corr corr = this.getOrCreateCorrModelElement(a, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getDependency());
      final Dependency d = ((Dependency) _orCreateSourceElem);
      this.updateDependencySource(d, a);
      d.setOffset(a.getDuration());
      EObject _sourceElement = this.getCorrModelElem(a.getNetwork()).getSourceElement();
      final GanttDiagram diag = ((GanttDiagram) _sourceElement);
      boolean _contains = diag.getElements().contains(d);
      boolean _not = (!_contains);
      if (_not) {
        EList<gantt.Element> _elements = diag.getElements();
        _elements.add(d);
      }
      Elem2Elem.corrToName.put(corr, a.getName());
      Elem2Elem.corrToDuration.put(corr, Integer.valueOf(d.getOffset()));
    };
    unmatched.forEach(_function_3);
  }

  /**
   * Finds the unique {@code cpm.Activity} in the target model whose {@code name}
   * exactly equals the given {@code name}.
   * 
   * <p>This lookup is used during the backward pass to map the activity names
   * encoded in a dependency arc's {@code name} (e.g. {@code "Design->Build"})
   * back to the actual {@code cpm.Activity} objects.</p>
   * 
   * @param name the exact activity name to search for (no arrow, plain name)
   * @return the first matching {@code cpm.Activity}, or {@code null} if none found
   */
  public Activity findCPMActivity(final String name) {
    final Function1<Activity, Boolean> _function = (Activity a) -> {
      return Boolean.valueOf(a.getName().equals(name));
    };
    return IteratorExtensions.<Activity>findFirst(Iterators.<Activity>filter(this.targetModel.getAllContents(), Activity.class), _function);
  }
}
