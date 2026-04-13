/**
 * Abstract base class for all bidirectional element-to-element transformation rules in
 * the Families-to-Persons BXtend transformation.
 * 
 * <p>Every concrete rule (e.g. {@link Register2Register}, {@link FamilyMember2Person})
 * extends this class and provides implementations of at least one direction:
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the Families model to the
 *       Persons model (forward direction).</li>
 *   <li>{@link #targetToSource()} – propagates changes from the Persons model back to
 *       the Families model (backward direction).</li>
 *   <li>{@link #synch()} – reconciles concurrent edits made to both models
 *       (synchronisation direction).</li>
 * </ul>
 * 
 * <p><b>Correspondence model</b><br>
 * The transformation maintains a correspondence (corr) model that records which Families
 * element is paired with which Persons element.  The two static maps
 * {@link #elementsToCorr} and {@link #corrToName} serve as an in-memory index over the
 * corr model so that lookups by EMF object are O(1).
 * 
 * <p><b>Decision strategy</b><br>
 * Ambiguous decisions during the backward transformation are delegated to the injected
 * {@link TargetToSourceDecision} strategy object.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.FamiliesFactory;
import Families.FamiliesPackage;
import Persons.PersonsFactory;
import Persons.PersonsPackage;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.BasicElem;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.F2pFactory;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Transformation;
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision;
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

@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The Families (source) model resource.
   */
  protected Resource sourceModel;

  /**
   * The Persons (target) model resource.
   */
  protected Resource targetModel;

  /**
   * The correspondence model resource.
   */
  protected Resource corrModel;

  /**
   * Factory for creating Families model elements.
   */
  protected final FamiliesFactory sourceFactory = FamiliesFactory.eINSTANCE;

  /**
   * Factory for creating Persons model elements.
   */
  protected final PersonsFactory targetFactory = PersonsFactory.eINSTANCE;

  /**
   * Factory for creating correspondence model elements.
   */
  protected final F2pFactory corrFactory = F2pFactory.eINSTANCE;

  /**
   * Families metamodel package (used for type checks).
   */
  protected final FamiliesPackage sourcePackage = FamiliesPackage.eINSTANCE;

  /**
   * Persons metamodel package (used for type checks).
   */
  protected final PersonsPackage targetPackage = PersonsPackage.eINSTANCE;

  /**
   * Identifier string used to distinguish rule types in the correspondence model.
   */
  protected String ruleID;

  /**
   * Strategy object for resolving ambiguous backward-transformation decisions.
   */
  protected TargetToSourceDecision decision;

  /**
   * Index from a source or target EMF object to its {@link Corr} correspondence entry.
   * Shared across all rule instances (static) so all rules see the same correspondence
   * state.
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Index from a {@link Corr} correspondence to the descriptive name assigned when
   * the correspondence was created.
   */
  protected static Map<Corr, String> corrToName = CollectionLiterals.<Corr, String>newHashMap();

  /**
   * Constructs a new rule and builds the in-memory correspondence index from the
   * serialised correspondence model.
   * 
   * @param src  the Families source model resource
   * @param trgt the Persons target model resource
   * @param corr the correspondence model resource
   * @param dec  the strategy for resolving backward-transformation decisions
   */
  public Elem2Elem(final Resource src, final Resource trgt, final Resource corr, final TargetToSourceDecision dec) {
    this.sourceModel = src;
    this.targetModel = trgt;
    this.corrModel = corr;
    this.decision = dec;
    this.ruleID = "base";
    EObject _get = this.corrModel.getContents().get(0);
    final Consumer<Corr> _function = (Corr c) -> {
      Elem2Elem.elementsToCorr.put(c.getSourceElement(), c);
      Elem2Elem.elementsToCorr.put(c.getTargetElement(), c);
    };
    ((Transformation) _get).getCorrespondences().forEach(_function);
  }

  /**
   * Propagates changes from the source (Families) model to the target (Persons) model.
   * Subclasses override this method to implement the forward direction.
   * The default implementation is a no-op.
   */
  public void sourceToTarget() {
  }

  /**
   * Propagates changes from the target (Persons) model back to the source (Families)
   * model.  Subclasses override this method to implement the backward direction.
   * The default implementation is a no-op.
   */
  public void targetToSource() {
  }

  /**
   * Reconciles concurrent edits in both models.  Subclasses override this method to
   * implement the synchronisation direction.  The default implementation is a no-op.
   */
  public void synch() {
  }

  /**
   * Replaces the current decision strategy with a new one.
   * 
   * @param dec the new {@link TargetToSourceDecision} to use
   */
  public void configure(final TargetToSourceDecision dec) {
    this.decision = dec;
  }

  /**
   * Looks up the {@link Corr} correspondence entry for {@code obj}.
   * 
   * @param obj the EMF object to look up
   * @return the corresponding {@link Corr}, or {@code null} if none exists
   */
  public Corr getCorrModelElem(final EObject obj) {
    return Elem2Elem.elementsToCorr.get(obj);
  }

  /**
   * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new
   * one if none exists yet.
   * 
   * <p>The new correspondence is added to the root {@link Transformation} container in
   * the correspondence model, and both the source and target index entries are updated.
   * 
   * @param obj         the EMF object for which a correspondence is needed
   * @param description a human-readable description stored in the correspondence
   * @return the existing or newly created {@link Corr}
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      BasicElem _createBasicElem = this.corrFactory.createBasicElem();
      final Procedure1<BasicElem> _function = (BasicElem it) -> {
        EPackage _ePackage = obj.eClass().getEPackage();
        if ((_ePackage instanceof FamiliesPackage)) {
          it.setSourceElement(obj);
        }
        EPackage _ePackage_1 = obj.eClass().getEPackage();
        if ((_ePackage_1 instanceof PersonsPackage)) {
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
   * Creates a new source-side (Families) element of the given metaclass.
   * 
   * @param clazz the EClass to instantiate
   * @return the newly created {@link EObject}
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new target-side (Persons) element of the given metaclass.
   * 
   * @param clazz the EClass to instantiate
   * @return the newly created {@link EObject}
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element already stored in {@code corr}, or creates and stores
   * a new source element of type {@code clazz} when the slot is empty.
   * 
   * <p>The new element is also added to the {@link #elementsToCorr} index.
   * 
   * @param corr  the correspondence whose source slot is checked
   * @param clazz the EClass to instantiate when the slot is empty
   * @return the existing or newly created source {@link EObject}
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
   * Returns the target element already stored in {@code corr}, or creates and stores
   * a new target element of type {@code clazz} when the slot is empty.
   * 
   * <p>The new element is also added to the {@link #elementsToCorr} index.
   * 
   * @param corr  the correspondence whose target slot is checked
   * @param clazz the EClass to instantiate when the slot is empty
   * @return the existing or newly created target {@link EObject}
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
