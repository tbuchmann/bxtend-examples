package de.tbuchmann.bxtend.pn2pnw.rules;

import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.BasicElem;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Pn2pnwFactory;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation;
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
import pn.PnFactory;
import pn.PnPackage;
import pnw.PnwFactory;
import pnw.PnwPackage;

/**
 * Abstract base class for all bidirectional transformation rules in the
 * Petrinet-to-PetrinetWeighted (Pn2Pnw) BXtend transformation.
 * 
 * <p>Each concrete subclass implements one correspondence rule (e.g. Net↔Net,
 * Place↔Place, Transition↔Transition) and overrides
 * {@link #sourceToTarget()} and/or {@link #targetToSource()} to propagate
 * changes in the respective direction.</p>
 * 
 * <p><b>Correspondence model:</b> Every matched pair of source and target
 * elements is recorded as a {@link Corr} object inside the shared
 * {@code corrModel} resource.  The static lookup map {@link #elementsToCorr}
 * allows O(1) retrieval of the correspondence for any model element during a
 * transformation pass.</p>
 * 
 * <p><b>Lifecycle:</b> All rule instances belonging to the same transformation
 * run share the same three resources ({@code sourceModel}, {@code targetModel},
 * {@code corrModel}) and the same static {@code elementsToCorr} map.  The map
 * is populated from the persisted correspondence model in the constructor so
 * that incremental runs can reuse existing correspondences.</p>
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The EMF resource that holds the source (unweighted Petri net) model.
   */
  protected Resource sourceModel;

  /**
   * The EMF resource that holds the target (weighted Petri net) model.
   */
  protected Resource targetModel;

  /**
   * The EMF resource that holds the correspondence model, i.e. the
   * {@link Transformation} root object containing all {@link Corr} links.
   */
  protected Resource corrModel;

  /**
   * Factory for creating source-side ({@code pn}) model elements.
   */
  protected final PnFactory sourceFactory = PnFactory.eINSTANCE;

  /**
   * Factory for creating target-side ({@code pnw}) model elements.
   */
  protected final PnwFactory targetFactory = PnwFactory.eINSTANCE;

  /**
   * Factory for creating correspondence model elements ({@code Corr}, {@code BasicElem}).
   */
  protected final Pn2pnwFactory corrFactory = Pn2pnwFactory.eINSTANCE;

  /**
   * Meta-model package for the source ({@code pn}) side.
   */
  protected final PnPackage sourcePackage = PnPackage.eINSTANCE;

  /**
   * Meta-model package for the target ({@code pnw}) side.
   */
  protected final PnwPackage targetPackage = PnwPackage.eINSTANCE;

  /**
   * Identifies the rule type in correspondence model entries (e.g. {@code "root"},
   * {@code "place"}, {@code "transition"}).  Set by each concrete subclass.
   */
  protected String ruleID;

  /**
   * Shared, static look-up table from any model element ({@code pn} or {@code pnw})
   * to the {@link Corr} object that links it to its counterpart.
   * 
   * <p>The map is populated once per transformation run from the persisted
   * correspondence model, and updated whenever a new correspondence is created
   * during source-to-target or target-to-source propagation.</p>
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Constructs the rule, wiring it to the shared model resources and
   * pre-loading the {@link #elementsToCorr} map from the persisted
   * correspondence model.
   * 
   * @param src   the source-model resource (unweighted Petri net)
   * @param trgt  the target-model resource (weighted Petri net)
   * @param corr  the correspondence-model resource
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
   * Propagates changes from the source model to the target model.
   * Concrete subclasses override this method to implement the forward
   * direction of their specific correspondence rule.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the target model to the source model.
   * Concrete subclasses override this method to implement the backward
   * direction of their specific correspondence rule.
   */
  public void targetToSource() {
  }

  /**
   * Returns the {@link Corr} object associated with {@code obj}, or
   * {@code null} if no correspondence has been established yet.
   * 
   * @param obj a source-side or target-side model element
   * @return the corresponding {@link Corr}, or {@code null}
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the existing {@link Corr} for {@code obj}, creating and
   * registering a new one if none exists yet.
   * 
   * <p>The newly created {@link Corr} is immediately added to the
   * {@link Transformation#getCorrespondences() correspondences} list of
   * the root object in the correspondence model and indexed in
   * {@link #elementsToCorr}.</p>
   * 
   * @param obj         a source-side or target-side model element
   * @param description a short human-readable label stored in {@link Corr#getDesc()}
   *                    (typically the {@link #ruleID} of the calling rule)
   * @return the found or newly created {@link Corr}
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      BasicElem _createBasicElem = this.corrFactory.createBasicElem();
      final Procedure1<BasicElem> _function = (BasicElem it) -> {
        EPackage _ePackage = obj.eClass().getEPackage();
        if ((_ePackage instanceof PnPackage)) {
          it.setSourceElement(obj);
        }
        EPackage _ePackage_1 = obj.eClass().getEPackage();
        if ((_ePackage_1 instanceof PnwPackage)) {
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
   * Creates a new source-side ({@code pn}) model element of the given meta-class.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return the new, unattached source element
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new target-side ({@code pnw}) model element of the given meta-class.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return the new, unattached target element
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element linked by {@code corr}, creating and linking a
   * new instance of {@code clazz} if the correspondence's source slot is empty.
   * 
   * <p>Used during target-to-source propagation to obtain (or lazily create)
   * the source counterpart of an existing target element.</p>
   * 
   * @param corr  the correspondence whose source slot should be filled
   * @param clazz the {@link EClass} to instantiate if no source element exists yet
   * @return the existing or newly created source element
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
   * Returns the target element linked by {@code corr}, creating and linking a
   * new instance of {@code clazz} if the correspondence's target slot is empty.
   * 
   * <p>Used during source-to-target propagation to obtain (or lazily create)
   * the target counterpart of an existing source element.</p>
   * 
   * @param corr  the correspondence whose target slot should be filled
   * @param clazz the {@link EClass} to instantiate if no target element exists yet
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
}
