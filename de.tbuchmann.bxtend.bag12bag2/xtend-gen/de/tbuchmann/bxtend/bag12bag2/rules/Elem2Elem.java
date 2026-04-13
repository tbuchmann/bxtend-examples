package de.tbuchmann.bxtend.bag12bag2.rules;

import bags1.Bags1Factory;
import bags1.Bags1Package;
import bags2.Bags2Factory;
import bags2.Bags2Package;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Factory;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Corr;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Transformation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.eclipse.xtext.xbase.lib.XbaseGenerated;

/**
 * Abstract base class for all BXtend transformation rules in the Bag1-to-Bag2
 * bidirectional, incremental model transformation.
 * 
 * <p>This class is part of the <em>BXtend</em> framework infrastructure and has been
 * extended beyond the standard BXtend generator output to introduce the
 * {@link MultiElem} correspondence type. The standard BXtend framework only generates
 * a single {@code Corr} type with one source element and one target element
 * (a 1-to-1 correspondence). The Bag1-to-Bag2 transformation, however, requires
 * <em>many-to-one</em> correspondences: multiple Bag1 {@code Element} objects sharing
 * the same {@code value} are all mapped to a single Bag2 {@code Element} with an
 * explicit {@code multiplicity} attribute. To represent this, {@code MultiElem} was
 * manually introduced in the correspondence metamodel ({@code corresp.ecore}) and the
 * corresponding EMF-generated Java interfaces/implementations were added to the
 * {@code correspondence} package.</p>
 * 
 * <h2>Correspondence Model Overview</h2>
 * <pre>
 *   Transformation
 *     └─ correspondences : Corr[*]
 *          ├─ BasicElem  (extends Corr)
 *          │    ├─ sourceElement : EObject   (one Bag1 MyBag)
 *          │    ├─ targetElement : EObject   (one Bag2 MyBag)
 *          │    └─ desc : String             (rule identifier, e.g. "Bag2Bag")
 *          └─ MultiElem  (extends Corr)       ← manually added extension
 *               ├─ sourceElements : EObject[*] (N Bag1 Elements with same value)
 *               ├─ targetElement  : EObject    (one Bag2 Element with multiplicity=N)
 *               └─ desc : String               (rule identifier, e.g. "Element2Element")
 * </pre>
 * 
 * <h2>In-Memory Cache</h2>
 * <p>The field {@link #elementsToCorr} is a <em>static</em>, shared {@link Map} that
 * acts as a reverse index from every model object (source or target) to its
 * {@link Corr} correspondence entry. This cache is populated once during construction
 * by iterating over the persisted correspondences and is kept up to date as new
 * correspondences are created at run time. The static scope means the cache is shared
 * across all rule instances within the same transformation execution.</p>
 * 
 * <h2>Rule Identification</h2>
 * <p>Each concrete subclass sets the {@link #ruleID} string (e.g. {@code "Bag2Bag"},
 * {@code "Element2Element"}). The rule ID is stored in the {@code desc} attribute of
 * every {@link Corr} entry created by that rule, which allows other rules to
 * distinguish their own correspondences when iterating over the correspondence model.</p>
 * 
 * @see Bag2Bag
 * @see Element2Element
 * @see Bag12bag2Transformation
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The EMF {@link Resource} that holds the Bag1 (source) model.
   */
  protected Resource sourceModel;

  /**
   * The EMF {@link Resource} that holds the Bag2 (target) model.
   */
  protected Resource targetModel;

  /**
   * The EMF {@link Resource} that holds the correspondence model.
   * Its root object is a {@link Transformation} instance whose
   * {@code correspondences} containment reference stores all {@link Corr} entries.
   */
  protected Resource corrModel;

  /**
   * Factory for creating new Bag1 model elements.
   */
  protected final Bags1Factory sourceFactory = Bags1Factory.eINSTANCE;

  /**
   * Factory for creating new Bag2 model elements.
   */
  protected final Bags2Factory targetFactory = Bags2Factory.eINSTANCE;

  /**
   * Factory for creating new correspondence model elements ({@link BasicElem} / {@link MultiElem}).
   */
  protected final Bag12bag2Factory corrFactory = Bag12bag2Factory.eINSTANCE;

  /**
   * Metamodel package descriptor for Bag1; used to test {@code instanceof Bags1Package} at runtime.
   */
  protected final Bags1Package sourcePackage = Bags1Package.eINSTANCE;

  /**
   * Metamodel package descriptor for Bag2; used to test {@code instanceof Bags2Package} at runtime.
   */
  protected final Bags2Package targetPackage = Bags2Package.eINSTANCE;

  /**
   * Human-readable identifier that distinguishes this rule from others.
   * Stored in every {@link Corr#getDesc()} so that rules can filter their
   * own correspondences when scanning the correspondence model.
   * Concrete subclasses must overwrite the default value {@code "base"}.
   */
  protected String ruleID;

  /**
   * Shared reverse-index cache mapping every tracked model element to its
   * {@link Corr} correspondence object.
   * 
   * <p>The map is populated in two ways:
   * <ol>
   *   <li>At construction time, by iterating over the already-persisted
   *       correspondences in the correspondence model (see {@link #put(Map, MultiElem)}
   *       / {@link #put(Map, BasicElem)} dispatch methods).</li>
   *   <li>Lazily at run time, whenever a new correspondence is created by
   *       {@link #getOrCreateCorrModelElement(EObject, String)} or the
   *       {@code getOrCreate*Elem} helpers.</li>
   * </ol>
   * Declared {@code static} so that the cache is shared across all rule
   * instances that participate in the same transformation session.</p>
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Constructs the rule, wires the three model resources, seeds {@link #ruleID}
   * with the sentinel value {@code "base"}, and populates the {@link #elementsToCorr}
   * cache from the already-persisted correspondences.
   * 
   * @param src  the Bag1 source model resource
   * @param trgt the Bag2 target model resource
   * @param corr the correspondence model resource (root must be a {@link Transformation})
   */
  public Elem2Elem(final Resource src, final Resource trgt, final Resource corr) {
    this.sourceModel = src;
    this.targetModel = trgt;
    this.corrModel = corr;
    this.ruleID = "base";
    EObject _get = this.corrModel.getContents().get(0);
    final Consumer<Corr> _function = (Corr c) -> {
      this.put(Elem2Elem.elementsToCorr, c);
    };
    ((Transformation) _get).getCorrespondences().forEach(_function);
  }

  /**
   * Propagates changes from the source (Bag1) model to the target (Bag2) model.
   * Concrete subclasses override this method with their specific rule logic.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the target (Bag2) model back to the source (Bag1) model.
   * Concrete subclasses override this method with their specific rule logic.
   */
  public void targetToSource() {
  }

  /**
   * Looks up the {@link Corr} entry for the given model object in the in-memory cache.
   * 
   * @param obj any Bag1 or Bag2 model element
   * @return the correspondence that covers {@code obj}, or {@code null} if none exists yet
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new one.
   * 
   * <p>The type of correspondence created depends on the metamodel package of {@code obj}:
   * <ul>
   *   <li>If {@code obj} is a {@code MyBag} instance (from either Bag1 or Bag2),
   *       a {@link BasicElem} is created, because bags have a strict 1-to-1
   *       correspondence (one Bag1 {@code MyBag} ↔ one Bag2 {@code MyBag}).</li>
   *   <li>For all other element types (i.e. {@code Element} instances), a
   *       {@link MultiElem} is created to accommodate the many-to-one grouping
   *       required by the bag-compression semantics.</li>
   * </ul>
   * The new correspondence is appended to the {@link Transformation#getCorrespondences()}
   * containment list and entered into the {@link #elementsToCorr} cache.</p>
   * 
   * @param obj         the model element for which a correspondence is needed
   * @param description the {@link Corr#getDesc()} label to assign (typically the {@link #ruleID})
   * @return the found or newly created {@link Corr}
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      if ((Objects.equals(obj.eClass(), Bags1Package.eINSTANCE.getMyBag()) || Objects.equals(obj.eClass(), Bags2Package.eINSTANCE.getMyBag()))) {
        BasicElem _createBasicElem = this.corrFactory.createBasicElem();
        final Procedure1<BasicElem> _function = (BasicElem it) -> {
          EPackage _ePackage = obj.eClass().getEPackage();
          if ((_ePackage instanceof Bags1Package)) {
            it.setSourceElement(obj);
          }
          EPackage _ePackage_1 = obj.eClass().getEPackage();
          if ((_ePackage_1 instanceof Bags2Package)) {
            it.setTargetElement(obj);
          }
          it.setDesc(description);
        };
        BasicElem _doubleArrow = ObjectExtensions.<BasicElem>operator_doubleArrow(_createBasicElem, _function);
        corr = _doubleArrow;
      } else {
        MultiElem _createMultiElem = this.corrFactory.createMultiElem();
        final Procedure1<MultiElem> _function_1 = (MultiElem it) -> {
          EPackage _ePackage = obj.eClass().getEPackage();
          if ((_ePackage instanceof Bags1Package)) {
            EList<EObject> _sourceElements = it.getSourceElements();
            _sourceElements.add(obj);
          }
          EPackage _ePackage_1 = obj.eClass().getEPackage();
          if ((_ePackage_1 instanceof Bags2Package)) {
            it.setTargetElement(obj);
          }
          it.setDesc(description);
        };
        MultiElem _doubleArrow_1 = ObjectExtensions.<MultiElem>operator_doubleArrow(_createMultiElem, _function_1);
        corr = _doubleArrow_1;
      }
      EObject _get = this.corrModel.getContents().get(0);
      EList<Corr> _correspondences = ((Transformation) _get).getCorrespondences();
      _correspondences.add(corr);
      this.put(Elem2Elem.elementsToCorr, corr);
    }
    return corr;
  }

  /**
   * Dispatch method that indexes a {@link MultiElem} correspondence into the cache.
   * 
   * <p>Registers both the single target element and every source element so that
   * any of them can be used as a look-up key.</p>
   * 
   * @param m    the cache map (unused; present only to satisfy the Xtend dispatch signature)
   * @param corr the {@link MultiElem} to index
   */
  protected Corr _put(final Map<EObject, Corr> m, final MultiElem corr) {
    Elem2Elem.elementsToCorr.put(corr.getTargetElement(), corr);
    final Consumer<EObject> _function = (EObject it) -> {
      Elem2Elem.elementsToCorr.put(it, corr);
    };
    corr.getSourceElements().forEach(_function);
    return null;
  }

  /**
   * Dispatch method that indexes a {@link BasicElem} correspondence into the cache.
   * 
   * <p>Registers both the source and target elements so that either can serve as
   * a look-up key.</p>
   * 
   * @param m    the cache map (unused; present only to satisfy the Xtend dispatch signature)
   * @param corr the {@link BasicElem} to index
   */
  protected Corr _put(final Map<EObject, Corr> m, final BasicElem corr) {
    Corr _xblockexpression = null;
    {
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
      _xblockexpression = Elem2Elem.elementsToCorr.put(corr.getTargetElement(), corr);
    }
    return _xblockexpression;
  }

  /**
   * Creates a new Bag1 (source) model element of the given metamodel class.
   * 
   * @param clazz the {@link EClass} descriptor (e.g. {@code Bags1Package.eINSTANCE.element})
   * @return the newly created {@link EObject}
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new Bag2 (target) model element of the given metamodel class.
   * 
   * @param clazz the {@link EClass} descriptor (e.g. {@code Bags2Package.eINSTANCE.element})
   * @return the newly created {@link EObject}
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Dispatch variant for {@link BasicElem}: ensures that the single source element
   * of a 1-to-1 correspondence exists, creating it if necessary.
   * 
   * <p>Returns a single-element list for interface uniformity with the
   * {@link MultiElem} dispatch variant.</p>
   * 
   * @param corr  the {@link BasicElem} correspondence
   * @param clazz the {@link EClass} to instantiate if the source element is missing
   * @return a list containing the (possibly newly created) source element
   */
  protected List<EObject> _getOrCreateSourceElem(final BasicElem corr, final EClass clazz) {
    ArrayList<EObject> source = CollectionLiterals.<EObject>newArrayList();
    EObject _sourceElement = corr.getSourceElement();
    boolean _tripleEquals = (_sourceElement == null);
    if (_tripleEquals) {
      corr.setSourceElement(this.createSourceElement(clazz));
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
    }
    EObject _sourceElement_1 = corr.getSourceElement();
    source.add(_sourceElement_1);
    return source;
  }

  /**
   * Dispatch variant for {@link MultiElem}: ensures that the source-elements list
   * of a many-to-one correspondence is non-empty, creating the first element if
   * the list is still empty.
   * 
   * <p>This is a bootstrap helper used during backward propagation when a Bag2
   * {@code Element} has been encountered for the first time and no Bag1 element
   * has been assigned to the correspondence yet.</p>
   * 
   * @param corr  the {@link MultiElem} correspondence
   * @param clazz the {@link EClass} to instantiate for the first source element
   * @return the (potentially modified) list of all source elements in this correspondence
   */
  protected List<EObject> _getOrCreateSourceElem(final MultiElem corr, final EClass clazz) {
    boolean _isEmpty = corr.getSourceElements().isEmpty();
    if (_isEmpty) {
      EList<EObject> _sourceElements = corr.getSourceElements();
      EObject _createSourceElement = this.createSourceElement(clazz);
      _sourceElements.add(_createSourceElement);
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
    }
    return corr.getSourceElements();
  }

  /**
   * Ensures that the target element of the given correspondence exists, creating a
   * new one of the specified class if it is still {@code null}.
   * 
   * <p>Works for both {@link BasicElem} and {@link MultiElem} correspondences because
   * both inherit {@code targetElement} from the base {@link Corr} type.</p>
   * 
   * @param corr  the correspondence whose target element is required
   * @param clazz the {@link EClass} to instantiate if the target element is missing
   * @return the (possibly newly created) target {@link EObject}
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

  @XbaseGenerated
  protected Corr put(final Map<EObject, Corr> m, final Corr corr) {
    if (corr instanceof BasicElem) {
      return _put(m, (BasicElem)corr);
    } else if (corr instanceof MultiElem) {
      return _put(m, (MultiElem)corr);
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(m, corr).toString());
    }
  }

  @XbaseGenerated
  public List<EObject> getOrCreateSourceElem(final Corr corr, final EClass clazz) {
    if (corr instanceof BasicElem) {
      return _getOrCreateSourceElem((BasicElem)corr, clazz);
    } else if (corr instanceof MultiElem) {
      return _getOrCreateSourceElem((MultiElem)corr, clazz);
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(corr, clazz).toString());
    }
  }
}
