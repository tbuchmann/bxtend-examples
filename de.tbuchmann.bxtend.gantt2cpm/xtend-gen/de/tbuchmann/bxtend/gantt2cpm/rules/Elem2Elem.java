package de.tbuchmann.bxtend.gantt2cpm.rules;

import cpm.CpmFactory;
import cpm.CpmPackage;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.BasicElem;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Gantt2cpmFactory;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation;
import gantt.GanttFactory;
import gantt.GanttPackage;
import java.util.Map;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Abstract base class for all Gantt ↔ CPM transformation rules.
 * 
 * <p>This class provides the shared infrastructure that every concrete rule builds upon:</p>
 * <ul>
 *   <li><b>EMF resource handles</b> – references to the three model resources
 *       ({@code sourceModel}, {@code targetModel}, {@code corrModel}) injected at
 *       construction time.</li>
 *   <li><b>EMF factory / package singletons</b> – pre-fetched instances of
 *       {@link GanttFactory}, {@link CpmFactory}, the correspondence factory, and the
 *       corresponding package objects for creating and inspecting model elements.</li>
 *   <li><b>O(1) correspondence lookup</b> – a static, shared
 *       {@code Map<EObject, Corr>} ({@link #elementsToCorr}) that is eagerly
 *       populated from the persisted correspondence model during construction and
 *       is kept up to date whenever new correspondences are added at run time.</li>
 *   <li><b>Element look-up/creation helpers</b> – {@link #getOrCreateCorrModelElement},
 *       {@link #getOrCreateSourceElem}, and {@link #getOrCreateTargetElem} implement
 *       the "look up existing, create if absent" pattern that all rules rely on for
 *       incremental re-synchronisation.</li>
 *   <li><b>Abstract propagation hooks</b> – {@link #sourceToTarget()} and
 *       {@link #targetToSource()} are overridden by concrete subclasses to implement
 *       the rule-specific mapping logic.</li>
 * </ul>
 * 
 * <p><b>Shared static map:</b> {@code elementsToCorr} is {@code static} so that the
 * combined lookup table is shared across all rule instances created for the same
 * transformation run.  Every constructor call pre-populates the map with the entries
 * already stored in the correspondence XMI file, ensuring that previously matched
 * elements are recognised as such on subsequent (incremental) runs.</p>
 * 
 * <p><b>Rule identifier:</b> Each subclass must set the {@link #ruleID} field in its
 * constructor.  The ID is stored in the {@code desc} attribute of every {@link Corr}
 * created by that subclass and is used by
 * {@link Gantt2cpmTransformation#detectSourceDeletions()} /
 * {@link Gantt2cpmTransformation#detectTargetDeletions()} to distinguish
 * correspondence entries by origin rule.</p>
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * EMF resource that holds the source (Gantt) model.
   */
  protected Resource sourceModel;

  /**
   * EMF resource that holds the target (CPM) model.
   */
  protected Resource targetModel;

  /**
   * EMF resource that holds the correspondence (Corr) model.
   */
  protected Resource corrModel;

  /**
   * EMF factory for creating new {@code gantt.*} elements.
   */
  protected final GanttFactory sourceFactory = GanttFactory.eINSTANCE;

  /**
   * EMF factory for creating new {@code cpm.*} elements.
   */
  protected final CpmFactory targetFactory = CpmFactory.eINSTANCE;

  /**
   * EMF factory for creating new correspondence model elements.
   */
  protected final Gantt2cpmFactory corrFactory = Gantt2cpmFactory.eINSTANCE;

  /**
   * Package singleton for the Gantt metamodel (used to look up {@code EClass} descriptors).
   */
  protected final GanttPackage sourcePackage = GanttPackage.eINSTANCE;

  /**
   * Package singleton for the CPM metamodel (used to look up {@code EClass} descriptors).
   */
  protected final CpmPackage targetPackage = CpmPackage.eINSTANCE;

  /**
   * Rule identifier stored in the {@code desc} attribute of every {@link Corr}
   * created by this rule.  Subclasses must override this in their constructor,
   * e.g. {@code "root"}, {@code "activity"}, {@code "dependency"}.
   */
  protected String ruleID;

  /**
   * Shared, static map from any model element ({@code EObject}) to its
   * {@link Corr} correspondence entry.
   * 
   * <p>Both the source element and the target element of a {@code Corr} are
   * inserted as keys so that a single {@link Map#get(Object)} suffices
   * regardless of which side the caller holds.</p>
   * 
   * <p>The map is populated in the constructor from the persisted correspondence
   * model and is kept current by {@link #getOrCreateCorrModelElement} and
   * {@link #getOrCreateTargetElem} / {@link #getOrCreateSourceElem} as new
   * correspondences are lazily created during each transformation run.</p>
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Constructs the base rule, wires the three model resources, and populates
   * {@link #elementsToCorr} from the persisted correspondence model.
   * 
   * @param src  EMF resource holding the source (Gantt) model
   * @param trgt EMF resource holding the target (CPM) model
   * @param corr EMF resource holding the correspondence model
   */
  public Elem2Elem(final Resource src, final Resource trgt, final Resource corr) {
    this.sourceModel = src;
    this.targetModel = trgt;
    this.corrModel = corr;
    this.ruleID = "base";
    EObject _get = this.corrModel.getContents().get(0);
    final Consumer<Corr> _function = (Corr c) -> {
      Elem2Elem.elementsToCorr.put(c.getSourceElement(), c);
      Elem2Elem.elementsToCorr.put(c.getTargetElement(), c);
    };
    ((Transformation) _get).getCorrespondences().forEach(_function);
  }

  /**
   * Propagates changes from the source (Gantt) model to the target (CPM) model.
   * Override in concrete rule subclasses; the default implementation is a no-op.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the target (CPM) model back to the source (Gantt) model.
   * Override in concrete rule subclasses; the default implementation is a no-op.
   */
  public void targetToSource() {
  }

  /**
   * Looks up the {@link Corr} entry associated with the given model element.
   * 
   * @param obj the source or target element whose correspondence is needed
   * @return the {@link Corr} for {@code obj}, or {@code null} if none exists yet
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the existing {@link Corr} for {@code obj}, or creates a new one if
   * none is found.
   * 
   * <p>When creating a new {@code Corr}:</p>
   * <ul>
   *   <li>If {@code obj} belongs to the Gantt package it is stored as
   *       {@code Corr.sourceElement}.</li>
   *   <li>If {@code obj} belongs to the CPM package it is stored as
   *       {@code Corr.targetElement}.</li>
   *   <li>{@code desc} is set to the supplied {@code description} string
   *       (typically the rule ID of the calling rule).</li>
   *   <li>The new {@code Corr} is appended to the root {@link Transformation}
   *       object in the correspondence model and both sides are registered in
   *       {@link #elementsToCorr}.</li>
   * </ul>
   * 
   * @param obj         the model element to look up or register
   * @param description a human-readable label (rule ID) stored in the new {@code Corr}
   * @return the existing or newly created {@link Corr}
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      BasicElem _createBasicElem = this.corrFactory.createBasicElem();
      final Procedure1<BasicElem> _function = (BasicElem it) -> {
        EPackage _ePackage = obj.eClass().getEPackage();
        if ((_ePackage instanceof GanttPackage)) {
          it.setSourceElement(obj);
        }
        EPackage _ePackage_1 = obj.eClass().getEPackage();
        if ((_ePackage_1 instanceof CpmPackage)) {
          it.setTargetElement(obj);
        }
        it.setDesc(description);
      };
      BasicElem _doubleArrow = ObjectExtensions.<BasicElem>operator_doubleArrow(_createBasicElem, _function);
      corr = _doubleArrow;
      EObject _get = this.corrModel.getContents().get(0);
      EList<Corr> _correspondences = ((Transformation) _get).getCorrespondences();
      _correspondences.add(corr);
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
      Elem2Elem.elementsToCorr.put(corr.getTargetElement(), corr);
    }
    return corr;
  }

  /**
   * Creates a new source (Gantt) model element of the given {@link EClass}.
   * 
   * @param clazz the {@code EClass} descriptor of the element to create
   * @return the newly instantiated {@code EObject}
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new target (CPM) model element of the given {@link EClass}.
   * 
   * @param clazz the {@code EClass} descriptor of the element to create
   * @return the newly instantiated {@code EObject}
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element linked by {@code corr}, or creates and links
   * a new one if {@code corr.sourceElement} is {@code null}.
   * 
   * <p>The newly created element is registered in {@link #elementsToCorr}.</p>
   * 
   * @param corr  the correspondence entry to inspect / update
   * @param clazz the {@code EClass} of the source element to create if absent
   * @return the existing or newly created source {@code EObject}
   */
  public EObject getOrCreateSourceElem(final Corr corr, final EClass clazz) {
    EObject source = corr.getSourceElement();
    EObject _sourceElement = corr.getSourceElement();
    boolean _tripleEquals = (_sourceElement == null);
    if (_tripleEquals) {
      source = this.createSourceElement(clazz);
      corr.setSourceElement(source);
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
    }
    return source;
  }

  /**
   * Returns the target element linked by {@code corr}, or creates and links
   * a new one if {@code corr.targetElement} is {@code null}.
   * 
   * <p>The newly created element is registered in {@link #elementsToCorr}.
   * Subclasses that need to create additional related elements (e.g.
   * {@link Activity2Activity} which must also create two {@link cpm.Event}
   * instances) should override this method.</p>
   * 
   * @param corr  the correspondence entry to inspect / update
   * @param clazz the {@code EClass} of the target element to create if absent
   * @return the existing or newly created target {@code EObject}
   */
  public EObject getOrCreateTargetElem(final Corr corr, final EClass clazz) {
    EObject target = corr.getTargetElement();
    if ((target == null)) {
      target = this.createTargetElement(clazz);
      corr.setTargetElement(target);
      Elem2Elem.elementsToCorr.put(corr.getTargetElement(), corr);
    }
    return target;
  }
}
