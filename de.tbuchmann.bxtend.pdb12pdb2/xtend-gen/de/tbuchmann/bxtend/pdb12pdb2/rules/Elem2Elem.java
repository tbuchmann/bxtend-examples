package de.tbuchmann.bxtend.pdb12pdb2.rules;

import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.BasicElem;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Pdb12pdb2Factory;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Transformation;
import de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision;
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
import pdb1.Pdb1Factory;
import pdb1.Pdb1Package;
import pdb2.Pdb2Factory;
import pdb2.Pdb2Package;

/**
 * Abstract base class for all BXtend transformation rules in the PDB1 ↔ PDB2 transformation.
 * 
 * <p>Each concrete subclass handles the bidirectional synchronisation of one pair of
 * metamodel elements (e.g. {@code Database ↔ Database} or {@code Person ↔ Person}).
 * The class manages access to the three EMF resources involved in every BXtend
 * transformation:</p>
 * <ul>
 *   <li><b>sourceModel</b> – the PDB1 resource (split-name person database)</li>
 *   <li><b>targetModel</b> – the PDB2 resource (full-name person database)</li>
 *   <li><b>corrModel</b>   – the correspondence (trace) resource that links
 *       matched source and target elements across incremental executions</li>
 * </ul>
 * 
 * <p>A shared, static {@code elementsToCorr} map provides O(1) lookup of the
 * {@link Corr} object for any source or target element, enabling efficient
 * incremental propagation without scanning the entire correspondence model on
 * every rule execution.</p>
 * 
 * <p>The {@link TargetToSourceDecision} strategy is injected via
 * {@link #configure(TargetToSourceDecision)} and resolves the inherent
 * non-determinism of the backward direction (splitting a PDB2 full name into
 * PDB1 {@code firstName} / {@code lastName}).</p>
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The PDB1 (source) EMF resource.
   */
  protected Resource sourceModel;

  /**
   * The PDB2 (target) EMF resource.
   */
  protected Resource targetModel;

  /**
   * The correspondence / trace EMF resource.
   */
  protected Resource corrModel;

  /**
   * Factory for creating new PDB1 model elements.
   */
  protected final Pdb1Factory sourceFactory = Pdb1Factory.eINSTANCE;

  /**
   * Factory for creating new PDB2 model elements.
   */
  protected final Pdb2Factory targetFactory = Pdb2Factory.eINSTANCE;

  /**
   * Factory for creating new correspondence model elements.
   */
  protected final Pdb12pdb2Factory corrFactory = Pdb12pdb2Factory.eINSTANCE;

  /**
   * Meta-model package descriptor for PDB1, used to resolve {@link EClass} instances.
   */
  protected final Pdb1Package sourcePackage = Pdb1Package.eINSTANCE;

  /**
   * Meta-model package descriptor for PDB2, used to resolve {@link EClass} instances.
   */
  protected final Pdb2Package targetPackage = Pdb2Package.eINSTANCE;

  /**
   * The runtime decision strategy that resolves non-determinism in the backward
   * direction (PDB2 → PDB1 name splitting). Injected via {@link #configure}.
   */
  protected TargetToSourceDecision decision;

  /**
   * A human-readable identifier for this rule, e.g. {@code "Database2Database"}.
   * It is stored as the {@code desc} attribute of each {@link Corr} element that
   * this rule creates, making the correspondence model self-documenting.
   */
  protected String ruleID;

  /**
   * Shared, static bidirectional index from any model element (source <em>or</em>
   * target) to its corresponding {@link Corr} entry. Using a static field means
   * all rule instances within one transformation session share the same index,
   * which is consistent with the single {@link Transformation} root object that
   * owns all correspondences.
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Initialises the rule with the three participating EMF resources and pre-populates
   * the {@code elementsToCorr} index from the existing correspondence model contents.
   * 
   * @param src  the PDB1 (source) resource
   * @param trgt the PDB2 (target) resource
   * @param corr the correspondence resource (must already contain a root
   *             {@link Transformation} object)
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
   * Injects the {@link TargetToSourceDecision} strategy used by the backward
   * propagation direction to resolve name-splitting ambiguity.
   * 
   * @param dec the decision strategy to use; must not be {@code null}
   */
  public void configure(final TargetToSourceDecision dec) {
    this.decision = dec;
  }

  /**
   * Propagates changes from the PDB1 source model to the PDB2 target model.
   * Subclasses override this method to implement the forward synchronisation
   * logic for their specific element pair.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the PDB2 target model to the PDB1 source model.
   * Subclasses override this method to implement the backward synchronisation
   * logic for their specific element pair.
   */
  public void targetToSource() {
  }

  /**
   * Looks up the {@link Corr} entry for the given model element.
   * 
   * @param obj any PDB1 or PDB2 element
   * @return the correspondence entry, or {@code null} if none exists yet
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the existing {@link Corr} entry for {@code obj}, or creates and
   * registers a new one if none exists yet.
   * 
   * <p>The new entry is added to the root {@link Transformation} container of the
   * correspondence model and indexed in {@code elementsToCorr} for both the source
   * and target slots, so future look-ups are O(1).</p>
   * 
   * @param obj         the model element (PDB1 or PDB2) for which a correspondence
   *                    entry is required
   * @param description a human-readable label stored in {@link Corr#desc} —
   *                    typically the {@link #ruleID} of the creating rule
   * @return the (possibly newly created) {@link Corr} entry
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      BasicElem _createBasicElem = this.corrFactory.createBasicElem();
      final Procedure1<BasicElem> _function = (BasicElem it) -> {
        EPackage _ePackage = obj.eClass().getEPackage();
        if ((_ePackage instanceof Pdb1Package)) {
          it.setSourceElement(obj);
        }
        EPackage _ePackage_1 = obj.eClass().getEPackage();
        if ((_ePackage_1 instanceof Pdb2Package)) {
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
   * Creates a new PDB1 element of the given meta-class using the source factory.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return the new, unattached PDB1 element
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new PDB2 element of the given meta-class using the target factory.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return the new, unattached PDB2 element
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element already linked to {@code corr}, or creates a new
   * PDB1 element of the given class, attaches it to {@code corr}, and registers
   * it in the index.
   * 
   * @param corr  the correspondence entry whose source slot should be filled
   * @param clazz the PDB1 meta-class to instantiate if no source element exists yet
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
   * Returns the target element already linked to {@code corr}, or creates a new
   * PDB2 element of the given class, attaches it to {@code corr}, and registers
   * it in the index.
   * 
   * @param corr  the correspondence entry whose target slot should be filled
   * @param clazz the PDB2 meta-class to instantiate if no target element exists yet
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
