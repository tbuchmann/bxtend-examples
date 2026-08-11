package de.tbuchmann.bxtend.ast2dag.rules;

import ast.AstFactory;
import ast.AstPackage;
import dag.DagFactory;
import dag.DagPackage;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagFactory;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.eclipse.xtext.xbase.lib.XbaseGenerated;

/**
 * Abstract base class for all BXtend transformation rules in the AST-to-DAG transformation.
 * 
 * <p>Each concrete subclass implements one rule that synchronises a specific pair of element
 * types between the source (ExpressionAST) and target (ExpressionDAG) models.  Both
 * synchronisation directions are represented as overridable methods:
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the AST to the DAG</li>
 *   <li>{@link #targetToSource()} – propagates changes from the DAG back to the AST</li>
 * </ul>
 * 
 * <h2>Correspondence model</h2>
 * The key challenge of the AST ↔ DAG transformation is the <em>structural mismatch</em>:
 * a single DAG node can be shared by multiple AST nodes (because identical sub-expressions
 * in the tree are deduplicated into one DAG node).  The correspondence model captures this
 * with two types:
 * <ul>
 *   <li>{@link BasicElem} – 1-to-1 correspondence, used only for Model root nodes</li>
 *   <li>{@link MultiElem} – many-to-1 correspondence, linking one target element to
 *       potentially many source elements (the duplicate AST sub-trees that share one
 *       DAG node)</li>
 * </ul>
 * 
 * <p>The static {@link #elementsToCorr} map provides an O(1) lookup from any model
 * element (source or target) to its correspondence entry.  It is shared across all
 * rule instances for a given transformation run so that rules can look up links created
 * by other rules.
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The EMF resource containing the source (AST) model.
   */
  protected Resource sourceModel;

  /**
   * The EMF resource containing the target (DAG) model.
   */
  protected Resource targetModel;

  /**
   * The EMF resource containing the correspondence model.
   */
  protected Resource corrModel;

  /**
   * Factory for instantiating AST model elements.
   */
  protected final AstFactory sourceFactory = AstFactory.eINSTANCE;

  /**
   * Factory for instantiating DAG model elements.
   */
  protected final DagFactory targetFactory = DagFactory.eINSTANCE;

  /**
   * Factory for instantiating correspondence elements.
   */
  protected final Ast2dagFactory corrFactory = Ast2dagFactory.eINSTANCE;

  /**
   * Reflective package for type-safe EClass references on the AST side.
   */
  protected final AstPackage sourcePackage = AstPackage.eINSTANCE;

  /**
   * Reflective package for type-safe EClass references on the DAG side.
   */
  protected final DagPackage targetPackage = DagPackage.eINSTANCE;

  /**
   * Identifies the rule within the correspondence model (stored in {@code Corr.desc}).
   * Concrete subclasses set this in their constructors, e.g. {@code "variable2variable"}.
   */
  protected String ruleID;

  /**
   * Shared O(1) index: maps every model element (AST or DAG) to its {@link Corr}
   * correspondence entry.  Declared {@code static} so that all rule instances created
   * for the same transformation session share the same map.
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Creates the rule and wires it to the three EMF resources.
   * Also populates {@link #elementsToCorr} from any correspondences already persisted
   * in the correspondence model, enabling incremental (alignment-based) operation.
   * 
   * @param src  the source (AST) model resource
   * @param trgt the target (DAG) model resource
   * @param corr the correspondence model resource (must already contain a
   *             {@link Transformation} root element)
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
   * Propagates changes from the source (AST) model to the target (DAG) model.
   * Subclasses override this method to implement their specific forward rule.
   * The default implementation is a no-op.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the target (DAG) model to the source (AST) model.
   * Subclasses override this method to implement their specific backward rule.
   * The default implementation is a no-op.
   */
  public void targetToSource() {
  }

  /**
   * Reconciliation hook, present for API consistency with the other BXtend examples'
   * {@code Elem2Elem} base classes. Not invoked directly by
   * {@link Ast2dagTransformation#synch()}: unlike a simple 1:1 domain, a per-rule method
   * that runs both {@link #sourceToTarget()} and {@link #targetToSource()} together
   * cannot be looped over a single rule list here, because forward propagation requires
   * leaves before operators (deduplication) while backward propagation requires
   * operators before leaves (tree reconstruction) — the opposite order. See
   * {@link Ast2dagTransformation#synch()} for how reconciliation is actually orchestrated
   * (all rules' {@link #sourceToTarget()} in {@code rulesFwd} order, then all rules'
   * {@link #targetToSource()} in {@code rulesBwd} order), which both existing directions
   * already support since they are idempotent, self-healing get-or-create
   * implementations. The default implementation here is a no-op.
   */
  public void synch() {
  }

  /**
   * Looks up the correspondence entry for the given model element.
   * 
   * @param obj any AST or DAG element
   * @return the {@link Corr} that contains {@code obj}, or {@code null} if none exists yet
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the correspondence entry for {@code obj}, creating a new one if it does
   * not already exist and registering it in the correspondence model.
   * 
   * <p>The type of the new correspondence depends on the element:
   * <ul>
   *   <li>{@link BasicElem} is created for {@code Model} root elements (1-to-1).</li>
   *   <li>{@link MultiElem} is created for all other elements (many-to-1), because
   *       AST leaves and operators can be shared in the DAG.</li>
   * </ul>
   * 
   * @param obj         the element to look up or register
   * @param description a human-readable label stored in {@link Corr#desc}, typically
   *                    the rule ID of the calling rule
   * @return the existing or newly created {@link Corr} entry
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      if ((Objects.equals(obj.eClass(), AstPackage.eINSTANCE.getModel()) || Objects.equals(obj.eClass(), DagPackage.eINSTANCE.getModel()))) {
        BasicElem _createBasicElem = this.corrFactory.createBasicElem();
        final Procedure1<BasicElem> _function = (BasicElem it) -> {
          EPackage _ePackage = obj.eClass().getEPackage();
          if ((_ePackage instanceof AstPackage)) {
            it.setSourceElement(obj);
          }
          EPackage _ePackage_1 = obj.eClass().getEPackage();
          if ((_ePackage_1 instanceof DagPackage)) {
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
          if ((_ePackage instanceof AstPackage)) {
            EList<EObject> _sourceElements = it.getSourceElements();
            _sourceElements.add(obj);
          }
          EPackage _ePackage_1 = obj.eClass().getEPackage();
          if ((_ePackage_1 instanceof DagPackage)) {
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
   * Indexes a {@link MultiElem} correspondence into {@link #elementsToCorr}.
   * Registers the target element and all source elements individually so that
   * lookups work in both directions.
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
   * Indexes a {@link BasicElem} correspondence into {@link #elementsToCorr}.
   * Registers both the single source element and the target element.
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
   * Creates a new AST (source) element of the given type using the AST factory.
   * 
   * @param clazz the {@link EClass} descriptor for the element to create
   * @return a freshly instantiated AST {@link EObject}
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new DAG (target) element of the given type using the DAG factory.
   * 
   * @param clazz the {@link EClass} descriptor for the element to create
   * @return a freshly instantiated DAG {@link EObject}
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element held in a {@link BasicElem} correspondence, creating
   * it (and registering it) if it does not yet exist.
   * Wrapped in a single-element list so callers can treat it uniformly with the
   * {@link MultiElem} overload.
   * 
   * @param corr  the 1-to-1 correspondence entry
   * @param clazz the EClass to instantiate if no source element exists yet
   * @return a list containing the (possibly new) source element
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
   * Returns all source elements held in a {@link MultiElem} correspondence, creating
   * the first one (and registering it) if the list is currently empty.
   * 
   * @param corr  the many-to-1 correspondence entry
   * @param clazz the EClass to instantiate when the source list is empty
   * @return the live {@code sourceElements} list of the correspondence
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
   * Finds the specific source element in a {@link MultiElem} correspondence that
   * satisfies the given predicate, creating it if it does not exist yet.
   * <p>This overload is used during backward propagation when multiple AST copies
   * of the same shared DAG node must be distinguished by their structural context
   * (e.g. which AST {@code Operator} is their parent).
   * 
   * @param corr      the many-to-1 correspondence entry
   * @param clazz     the EClass to instantiate if no matching element exists
   * @param predicate a filter that identifies the desired element among the existing ones
   * @return the matching (or newly created) source element
   */
  public EObject getOrCreateSourceElem(final MultiElem corr, final EClass clazz, final Predicate<EObject> predicate) {
    EObject source = IterableExtensions.<EObject>findFirst(corr.getSourceElements(), new Function1<EObject, Boolean>() {
        public Boolean apply(EObject p) {
          return predicate.test(p);
        }
    });
    if ((source == null)) {
      source = this.createSourceElement(clazz);
      EList<EObject> _sourceElements = corr.getSourceElements();
      _sourceElements.add(source);
      Elem2Elem.elementsToCorr.put(corr.getSourceElement(), corr);
    }
    return source;
  }

  /**
   * Returns the target (DAG) element held in the given correspondence, creating it
   * (and registering it) if it does not yet exist.
   * 
   * @param corr  any correspondence entry ({@link BasicElem} or {@link MultiElem})
   * @param clazz the EClass to instantiate if no target element exists yet
   * @return the existing or newly created target element
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
