package de.tbuchmann.bxtend.pdb12pdb2.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Pdb12pdb2Factory;
import de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.ConfigurableTargetToSourceDecision;
import de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision;
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
 * Top-level orchestrator of the PDB1 ↔ PDB2 BXtend transformation.
 * 
 * <p>This class ties together the three EMF resources (source, target, correspondence)
 * and the ordered list of {@link Elem2Elem} rules that together implement the full
 * bidirectional, incremental synchronisation between the two person-database models:</p>
 * 
 * <ul>
 *   <li>{@link Database2Database} – synchronises {@code pdb1.Database ↔ pdb2.Database}
 *       (must run before {@code Person2Person} so the parent containers exist when
 *       persons are processed).</li>
 *   <li>{@link Person2Person} – synchronises {@code pdb1.Person ↔ pdb2.Person},
 *       including the asymmetric name mapping.</li>
 * </ul>
 * 
 * <h3>Incrementality and deletion handling</h3>
 * <p>Incrementality is achieved through the correspondence model: every matched pair of
 * source/target elements is linked by a {@link Corr} entry.  After each propagation
 * pass, "dangling" correspondences (where the source or target element has been
 * deleted) are detected and used to drive deletion of the orphaned counterpart and
 * removal of the stale {@link Corr} entry.</p>
 * 
 * <h3>Non-determinism</h3>
 * <p>The backward direction (PDB2 → PDB1) requires splitting a full name string into
 * {@code firstName} / {@code lastName}.  The default strategy is
 * {@link ConfigurableTargetToSourceDecision} with {@code spacePosition = -1}, meaning
 * the <em>last</em> space in the name is used as the split point.  Callers may replace
 * the default by invoking {@link #configure(TargetToSourceDecision)} before the first
 * propagation.</p>
 */
@SuppressWarnings("all")
public class Pdb12pdb2Transformation {
  /**
   * The PDB1 (source) EMF resource.
   */
  private Resource sourceModel;

  /**
   * The PDB2 (target) EMF resource.
   */
  private Resource targetModel;

  /**
   * The correspondence / trace EMF resource.
   */
  private Resource corrModel;

  /**
   * Ordered list of bidirectional rules.  Rules are applied in list order during
   * both forward and backward propagation, so container rules must precede
   * contained-element rules.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * Convenience constructor that loads all three resources from the given URIs into
   * a shared {@link ResourceSet}.  If the correspondence resource is empty, an
   * initial {@link de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Transformation}
   * root element is created automatically.
   * 
   * @param source        URI of the PDB1 XMI file
   * @param target        URI of the PDB2 XMI file
   * @param correspondence URI of the correspondence XMI file
   */
  public Pdb12pdb2Transformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Pdb12pdb2Factory.eINSTANCE.createTransformation());
    }
    this.addRules();
    ConfigurableTargetToSourceDecision _configurableTargetToSourceDecision = new ConfigurableTargetToSourceDecision((-1));
    this.configure(_configurableTargetToSourceDecision);
  }

  /**
   * Constructor used when the EMF resources have already been loaded by the caller
   * (e.g. during test setup or when sharing a {@link ResourceSet} with other tools).
   * 
   * @param source        the PDB1 resource (must not be {@code null})
   * @param target        the PDB2 resource (must not be {@code null})
   * @param correspondence the correspondence resource (must not be {@code null})
   */
  public Pdb12pdb2Transformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Pdb12pdb2Factory.eINSTANCE.createTransformation());
    }
    this.addRules();
    ConfigurableTargetToSourceDecision _configurableTargetToSourceDecision = new ConfigurableTargetToSourceDecision((-1));
    this.configure(_configurableTargetToSourceDecision);
  }

  /**
   * Registers all transformation rules in the required execution order.
   * {@link Database2Database} must precede {@link Person2Person} so that target
   * database containers exist before persons are linked to them.
   */
  public boolean addRules() {
    boolean _xblockexpression = false;
    {
      Database2Database _database2Database = new Database2Database(this.sourceModel, this.targetModel, this.corrModel);
      this.rules.add(_database2Database);
      Person2Person _person2Person = new Person2Person(this.sourceModel, this.targetModel, this.corrModel);
      _xblockexpression = this.rules.add(_person2Person);
    }
    return _xblockexpression;
  }

  /**
   * Propagates the given {@link TargetToSourceDecision} to every registered rule.
   * 
   * @param dec the decision strategy to use for backward name splitting
   */
  public void configure(final TargetToSourceDecision dec) {
    final Consumer<Elem2Elem> _function = (Elem2Elem r) -> {
      r.configure(dec);
    };
    this.rules.forEach(_function);
  }

  /**
   * Runs all rules in the forward direction (PDB1 → PDB2) and then removes any
   * PDB2 elements whose correspondence source has been deleted.
   * 
   * <p>The propagation is skipped entirely if the source model is empty, which
   * prevents accidental erasure of the target during initialisation.</p>
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
   * Runs all rules in the backward direction (PDB2 → PDB1) and then removes any
   * PDB1 elements whose correspondence target has been deleted.
   * 
   * <p>The propagation is skipped entirely if the target model is empty.</p>
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
   * Runs all rules' synchronisation direction, reconciling concurrent edits made to both
   * the PDB1 and PDB2 models since the last synchronisation point.
   * 
   * <p>Executes each rule's {@link Elem2Elem#synch()} in the same registration order as
   * {@link #sourceToTarget()}/{@link #targetToSource()}, then cleans up dangling
   * correspondences on both sides.</p>
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Placeholder for checking that all correspondences are valid (both sides
   * present and consistent). Currently always returns {@code true}.
   * 
   * @return {@code true} if all correspondences are intact
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Finds all {@link Corr} entries where the source element has been deleted
   * (i.e. {@code sourceElement == null}).  These correspondences indicate that
   * a PDB1 element was removed and the corresponding PDB2 element must be cleaned up.
   * 
   * @return an iterator over dangling (source-less) correspondences
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Finds all {@link Corr} entries where the target element has been deleted
   * (i.e. {@code targetElement == null}).  These correspondences indicate that
   * a PDB2 element was removed and the corresponding PDB1 element must be cleaned up.
   * 
   * @return an iterator over dangling (target-less) correspondences
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Removes PDB2 elements and their {@link Corr} entries for every correspondence
   * whose source element is {@code null} (i.e. the PDB1 element was deleted).
   * Uses {@link EcoreUtil#delete} with {@code recursive = true} to cascade through
   * any contained sub-elements.
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
      if ((e != null)) {
        EcoreUtil.delete(e, true);
      }
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Removes PDB1 elements and their {@link Corr} entries for every correspondence
   * whose target element is {@code null} (i.e. the PDB2 element was deleted).
   * Uses {@link EcoreUtil#delete} with {@code recursive = true} to cascade through
   * any contained sub-elements.
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
      if ((e != null)) {
        EcoreUtil.delete(e, true);
      }
    };
    deletionList.forEach(_function_1);
  }
}
