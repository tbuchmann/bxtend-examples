/**
 * Top-level coordinator for the bidirectional, incremental, and synchronising
 * <em>Families-to-Persons</em> model transformation implemented with the BXtend framework.
 * 
 * <p>This class owns the three model resources (source, target, correspondence) and
 * maintains an ordered list of {@link Elem2Elem} rules that are executed in sequence
 * for each transformation direction.  The rule execution order is:
 * <ol>
 *   <li>{@link Register2Register} – root-level register pairing</li>
 *   <li>{@link MotherDaughter2Female} – female family members</li>
 *   <li>{@link FatherSon2Male} – male family members</li>
 * </ol>
 * 
 * <p><b>Transformation directions</b>
 * <ul>
 *   <li>{@link #sourceToTarget()} – forward: Families → Persons</li>
 *   <li>{@link #targetToSource()} – backward: Persons → Families</li>
 *   <li>{@link #synch()} – synchronisation: reconciles concurrent edits in both models</li>
 * </ul>
 * 
 * <p><b>Families name index ({@link #familiesMap})</b><br>
 * A static {@code Map<String, List<Family>>} that indexes all known {@link Families.Family}
 * objects by their name, enabling O(1) candidate lookups during the backward transformation.
 * The map is populated by {@link #sourceToTarget()} and {@link #updateFamiliesMap()}.
 * 
 * <p><b>Incremental behaviour / deletion handling</b><br>
 * After each rule pass, dangling correspondence entries (i.e. entries whose source or
 * target element has been deleted) are detected and cleaned up:
 * {@link #deleteUnreferencedTargetElements()} removes orphaned Persons elements, and
 * {@link #deleteUnreferencedSourceElements()} removes orphaned Families elements.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.Family;
import Families.FamilyRegister;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.F2pFactory;
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

@SuppressWarnings("all")
public class Families2personsTransformation {
  /**
   * The Families (source) model resource.
   */
  private Resource sourceModel;

  /**
   * The Persons (target) model resource.
   */
  private Resource targetModel;

  /**
   * The correspondence model resource.
   */
  private Resource corrModel;

  /**
   * Ordered list of transformation rules applied during each direction pass.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * Index of all known {@link Family} objects keyed by family name.
   * Used during backward transformation to find candidate families without a full
   * linear scan of the source model.  Updated by {@link #sourceToTarget()} and
   * {@link #updateFamiliesMap()}.
   */
  public static Map<String, List<Family>> familiesMap = CollectionLiterals.<String, List<Family>>newHashMap();

  /**
   * The currently active backward-transformation decision strategy.
   */
  private TargetToSourceDecision decision;

  /**
   * Constructs a new transformation by loading the three models from the given URIs.
   * A fresh {@link de.tbuchmann.bxtend.f2p.correspondence.f2p.Transformation} root
   * is created in the correspondence model if it is empty.
   * 
   * @param source        URI of the Families model resource
   * @param target        URI of the Persons model resource
   * @param correspondence URI of the correspondence model resource
   */
  public Families2personsTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(F2pFactory.eINSTANCE.createTransformation());
    }
    this.addRules();
  }

  /**
   * Constructs a new transformation from already-loaded EMF {@link Resource} objects.
   * Useful in test scenarios where resources are set up programmatically.
   * 
   * @param source        the Families model resource
   * @param target        the Persons model resource
   * @param correspondence the correspondence model resource
   */
  public Families2personsTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(F2pFactory.eINSTANCE.createTransformation());
    }
    this.addRules();
  }

  /**
   * Executes the forward transformation (Families → Persons).
   * 
   * <p>Each rule in {@link #rules} is invoked in order.  Afterwards the
   * {@link #familiesMap} index is updated and dangling Persons elements caused by
   * source-side deletions are cleaned up.
   */
  public void sourceToTarget() {
    int _size = this.sourceModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rules) {
        e.sourceToTarget();
      }
      EObject _get = this.sourceModel.getContents().get(0);
      final Consumer<Family> _function = (Family f) -> {
        List<Family> famList = Families2personsTransformation.familiesMap.get(f.getName());
        if ((famList == null)) {
          famList = CollectionLiterals.<Family>newArrayList();
          famList.add(f);
        } else {
          boolean _contains = famList.contains(f);
          boolean _not = (!_contains);
          if (_not) {
            famList.add(f);
          }
        }
        Families2personsTransformation.familiesMap.put(f.getName(), famList);
      };
      ((FamilyRegister) _get).getFamilies().forEach(_function);
    }
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Executes the backward transformation (Persons → Families).
   * 
   * <p>Each rule in {@link #rules} is invoked in order.  Afterwards dangling Families
   * elements caused by target-side deletions are cleaned up.
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
   * Executes the synchronisation direction, which reconciles concurrent edits made to
   * both models since the last synchronisation point.
   * 
   * <p>Each rule's {@link Elem2Elem#synch()} is called in order.  Afterwards both
   * dangling source and target elements are cleaned up.
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Replaces the decision strategy on this transformation and propagates the change to
   * all registered rules.
   * 
   * @param dec the new {@link TargetToSourceDecision} to use
   */
  public void configure(final TargetToSourceDecision dec) {
    this.decision = dec;
    final Consumer<Elem2Elem> _function = (Elem2Elem r) -> {
      r.configure(dec);
    };
    this.rules.forEach(_function);
  }

  /**
   * Placeholder for future consistency checks on the correspondence model.
   * 
   * @return {@code true} (currently always consistent)
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Returns a stream of {@link Corr} entries whose source element is {@code null},
   * indicating that the corresponding Families element has been deleted.
   * 
   * @return an iterator over correspondences with a missing source element
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Returns a stream of {@link Corr} entries whose target element is {@code null},
   * indicating that the corresponding Persons element has been deleted.
   * 
   * @return an iterator over correspondences with a missing target element
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Deletes Persons-side ({@link Persons.Person}) elements whose corresponding Families
   * element has been removed, and removes the now-empty correspondence entries.
   * 
   * <p>Called at the end of {@link #sourceToTarget()} and {@link #synch()}.
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      boolean _tripleNotEquals = (_targetElement != null);
      if (_tripleNotEquals) {
        EObject _targetElement_1 = c.getTargetElement();
        deletionList.add(_targetElement_1);
      }
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Deletes Families-side ({@link Families.FamilyMember} / {@link Families.Family})
   * elements whose corresponding Persons element has been removed, and removes the
   * now-empty correspondence entries.
   * 
   * <p>Called at the end of {@link #targetToSource()} and {@link #synch()}.
   */
  public void deleteUnreferencedSourceElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      boolean _tripleNotEquals = (_sourceElement != null);
      if (_tripleNotEquals) {
        EObject _sourceElement_1 = c.getSourceElement();
        deletionList.add(_sourceElement_1);
      }
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectTargetDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Registers all transformation rules in the correct execution order.
   * Ensures a root Transformation element exists in the correspondence model,
   * then creates and adds one instance of each rule class.
   * 
   * <p>Rule order:
   * <ol>
   *   <li>{@link Register2Register}</li>
   *   <li>{@link MotherDaughter2Female}</li>
   *   <li>{@link FatherSon2Male}</li>
   * </ol>
   */
  private void addRules() {
    boolean _isEmpty = this.corrModel.getContents().isEmpty();
    if (_isEmpty) {
      this.corrModel.getContents().add(F2pFactory.eINSTANCE.createTransformation());
    }
    Register2Register _register2Register = new Register2Register(this.sourceModel, this.targetModel, this.corrModel, this.decision);
    this.rules.add(_register2Register);
    MotherDaughter2Female _motherDaughter2Female = new MotherDaughter2Female(this.sourceModel, this.targetModel, this.corrModel, this.decision);
    this.rules.add(_motherDaughter2Female);
    FatherSon2Male _fatherSon2Male = new FatherSon2Male(this.sourceModel, this.targetModel, this.corrModel, this.decision);
    this.rules.add(_fatherSon2Male);
  }

  /**
   * Refreshes the {@link #familiesMap} name-to-family index from the current state of
   * the source model.  Should be called after external modifications to the Families
   * model that are not going through a transformation direction method.
   */
  public void updateFamiliesMap() {
    int _size = this.sourceModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      EObject _get = this.sourceModel.getContents().get(0);
      final Consumer<Family> _function = (Family f) -> {
        List<Family> famList = Families2personsTransformation.familiesMap.get(f.getName());
        if ((famList == null)) {
          famList = CollectionLiterals.<Family>newArrayList();
          famList.add(f);
        } else {
          boolean _contains = famList.contains(f);
          boolean _not = (!_contains);
          if (_not) {
            famList.add(f);
          }
        }
        Families2personsTransformation.familiesMap.put(f.getName(), famList);
      };
      ((FamilyRegister) _get).getFamilies().forEach(_function);
    }
  }
}
