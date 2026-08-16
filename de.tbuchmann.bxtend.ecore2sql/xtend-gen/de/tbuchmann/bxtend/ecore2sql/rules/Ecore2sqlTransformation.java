package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlFactory;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Column;
import sql.ForeignKey;
import sql.Key;
import sql.Schema;
import sql.Table;

/**
 * Top-level orchestrator for the bidirectional, incremental Ecore-to-SQL transformation
 * implemented with the <em>BXtend</em> framework.
 * 
 * <h3>Responsibility</h3>
 * <p>This class ties together all individual transformation rules (each a subclass of
 * {@link Elem2Elem}) into an ordered pipeline and exposes the two propagation directions
 * as the public API consumed by the benchmark harness ({@code BXtendEcore2SQL}):</p>
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the Ecore model to the SQL model.</li>
 *   <li>{@link #targetToSource()} – propagates changes from the SQL model back to the Ecore model.</li>
 * </ul>
 * 
 * <h3>Rule pipeline</h3>
 * <p>Rules are executed in strict order.  Each rule depends on the correspondences established
 * by the preceding rule(s), so the order is not arbitrary:</p>
 * <ol>
 *   <li>{@link Package2Schema} – Ecore package → SQL schema (must run first; all other rules
 *       rely on the schema element).</li>
 *   <li>{@link Class2Table} – Ecore class → SQL table (must run before inheritance and
 *       structural-feature rules).</li>
 *   <li>{@link Generalization2Relation} – Ecore generalisation → foreign-key hierarchy
 *       (requires class tables from step 2).</li>
 *   <li>{@link Attribute2Attribute} – Ecore attribute → SQL column or auxiliary table
 *       (requires class tables from step 2).</li>
 *   <li>{@link EReference2Relation} – Ecore reference → FK column or relation table
 *       (requires class tables from step 2).</li>
 * </ol>
 * 
 * <h3>Correspondence model initialisation</h3>
 * <p>Both constructors ensure that the correspondence resource contains a root
 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation Transformation}
 * object before any rule runs.  This object is the container for all
 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr Corr} links created
 * during transformation.</p>
 * 
 * <h3>Incremental deletion handling</h3>
 * <p>After the forward or backward rule pipeline finishes, a clean-up pass removes elements
 * that have become orphaned (i.e. their corresponding element on the other side was deleted
 * by the user and the correspondence now has a {@code null} slot):</p>
 * <ul>
 *   <li>{@link #deleteUnreferencedTargetElements()} – called at the end of
 *       {@link #sourceToTarget()}; removes SQL elements whose source Ecore element is gone,
 *       together with any dangling foreign keys.</li>
 *   <li>{@link #deleteUnreferencedSourceElements()} – called at the end of
 *       {@link #targetToSource()}; removes Ecore elements whose SQL element is gone, including
 *       any {@link EReference#getEOpposite() opposite} references.</li>
 * </ul>
 * 
 * <h3>Usage</h3>
 * <pre>
 * // Create the transformation wired to EMF Resources
 * val t = new Ecore2sqlTransformation(sourceResource, targetResource, corrResource);
 * // Initial batch forward propagation
 * t.sourceToTarget();
 * // Later, after editing the source model
 * t.sourceToTarget();
 * // Or after editing the target model
 * t.targetToSource();
 * </pre>
 */
@SuppressWarnings("all")
public class Ecore2sqlTransformation {
  /**
   * The Ecore source model resource.
   */
  private Resource sourceModel;

  /**
   * The SQL target model resource.
   */
  private Resource targetModel;

  /**
   * The correspondence model resource.
   */
  private Resource corrModel;

  /**
   * The ordered list of transformation rules.  Rules are applied left-to-right
   * in both propagation directions.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * Constructs the transformation by loading the three models from the given URIs.
   * If the correspondence resource is empty a fresh
   * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation Transformation}
   * root object is created automatically.
   * 
   * @param source         URI of the Ecore source model (e.g. {@code "ecore.ecore"})
   * @param target         URI of the SQL target model (e.g. {@code "sql.xmi"})
   * @param correspondence URI of the correspondence model (e.g. {@code "corr.xmi"})
   */
  public Ecore2sqlTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Ecore2sqlFactory.eINSTANCE.createTransformation());
    }
    Elem2Elem.rebuildCorrespondenceCache(((Transformation) this.corrModel.getContents().get(0)).getCorrespondences());
    Package2Schema _package2Schema = new Package2Schema(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_package2Schema);
    Class2Table _class2Table = new Class2Table(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_class2Table);
    Generalization2Relation _generalization2Relation = new Generalization2Relation(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_generalization2Relation);
    Attribute2Attribute _attribute2Attribute = new Attribute2Attribute(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_attribute2Attribute);
    EReference2Relation _eReference2Relation = new EReference2Relation(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_eReference2Relation);
  }

  /**
   * Constructs the transformation from already-loaded EMF {@link Resource}s.
   * This constructor is used by the benchmark harness ({@code BXtendEcore2SQL})
   * which manages resource loading itself.
   * 
   * @param source         the Ecore source model resource
   * @param target         the SQL target model resource
   * @param correspondence the correspondence model resource
   */
  public Ecore2sqlTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Ecore2sqlFactory.eINSTANCE.createTransformation());
    }
    Elem2Elem.rebuildCorrespondenceCache(((Transformation) this.corrModel.getContents().get(0)).getCorrespondences());
    Package2Schema _package2Schema = new Package2Schema(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_package2Schema);
    Class2Table _class2Table = new Class2Table(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_class2Table);
    Generalization2Relation _generalization2Relation = new Generalization2Relation(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_generalization2Relation);
    Attribute2Attribute _attribute2Attribute = new Attribute2Attribute(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_attribute2Attribute);
    EReference2Relation _eReference2Relation = new EReference2Relation(this.sourceModel, this.targetModel, this.corrModel);
    this.rules.add(_eReference2Relation);
  }

  /**
   * Propagates changes from the Ecore source model to the SQL target model.
   * 
   * <p>Runs each rule's {@link Elem2Elem#sourceToTarget()} method in pipeline order,
   * then calls {@link #deleteUnreferencedTargetElements()} to purge SQL elements whose
   * Ecore counterpart has been deleted.</p>
   * 
   * <p>If the source model is empty (e.g. during initialisation) the pipeline is skipped
   * entirely to avoid null-pointer situations in the rules.</p>
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
   * Propagates changes from the SQL target model back to the Ecore source model.
   * 
   * <p>Runs each rule's {@link Elem2Elem#targetToSource()} method in pipeline order,
   * then calls {@link #deleteUnreferencedSourceElements()} to purge Ecore elements whose
   * SQL counterpart has been deleted.</p>
   * 
   * <p>If the target model is empty the pipeline is skipped.</p>
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
   * Propagates and reconciles concurrent edits made to both the Ecore source model and the
   * SQL target model since the last synchronisation point.
   * 
   * <p>Runs each rule's {@link Elem2Elem#synch()} in the same pipeline order as
   * {@link #sourceToTarget()}/{@link #targetToSource()} (later rules depend on correspondences
   * established by earlier ones), then cleans up dangling correspondences on both sides.</p>
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Placeholder consistency check; currently always returns {@code true}.
   * May be extended in the future to verify that all correspondences are valid.
   * 
   * @return {@code true}
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Returns all {@link Corr} objects in the correspondence model whose
   * {@link Corr#getSourceElement() sourceElement} is {@code null}.
   * 
   * <p>A {@code null} source element indicates that the user deleted the corresponding
   * Ecore element, so the linked SQL element should also be removed from the target model.</p>
   * 
   * @return an iterator over orphaned {@link Corr} objects (source-side deletions)
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Returns all {@link Corr} objects in the correspondence model whose
   * {@link Corr#getTargetElement() targetElement} is {@code null}.
   * 
   * <p>A {@code null} target element indicates that the user deleted the corresponding
   * SQL element, so the linked Ecore element should also be removed from the source model.</p>
   * 
   * @return an iterator over orphaned {@link Corr} objects (target-side deletions)
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Removes all SQL elements (and their dependent foreign keys) that have become orphaned
   * because their corresponding Ecore source element was deleted.
   * 
   * <p>The deletion algorithm:</p>
   * <ol>
   *   <li>For each {@link Corr} with a {@code null} source element:
   *     <ul>
   *       <li>If the target is a {@link Column}: collect all keys (foreign keys) on that
   *           column for deletion.</li>
   *       <li>If the target is a {@link Table}: collect all incoming foreign keys
   *           ({@code referencingForeignKeys}) and the corresponding column in the
   *           {@code EObject} sentinel table.</li>
   *       <li>Add the target element and the {@link Corr} itself to the deletion list.</li>
   *     </ul>
   *   </li>
   *   <li>Collect any remaining {@link ForeignKey}s that have a {@code null} column or
   *       referenced table (dangling keys from prior partial deletions).</li>
   *   <li>Delete all collected elements via {@link EcoreUtil#delete}.</li>
   * </ol>
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    EObject _get = this.targetModel.getContents().get(0);
    final Schema s = ((Schema) _get);
    final Function1<Table, Boolean> _function = (Table t) -> {
      return Boolean.valueOf(t.getName().equals("EObject"));
    };
    final Table eot = IterableExtensions.<Table>findFirst(s.getOwnedTables(), _function);
    final Procedure1<Corr> _function_1 = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      if ((_targetElement instanceof Column)) {
        EObject _targetElement_1 = c.getTargetElement();
        final Column col = ((Column) _targetElement_1);
        EList<Key> _keys = col.getKeys();
        Iterables.<EObject>addAll(deletionList, _keys);
      }
      EObject _targetElement_2 = c.getTargetElement();
      if ((_targetElement_2 instanceof Table)) {
        EObject _targetElement_3 = c.getTargetElement();
        final Table tab = ((Table) _targetElement_3);
        EList<ForeignKey> _referencingForeignKeys = tab.getReferencingForeignKeys();
        Iterables.<EObject>addAll(deletionList, _referencingForeignKeys);
        final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey it) -> {
          Table _owningTable = it.getOwningTable();
          return Boolean.valueOf(Objects.equals(_owningTable, eot));
        };
        ForeignKey _findFirst = IterableExtensions.<ForeignKey>findFirst(tab.getReferencingForeignKeys(), _function_2);
        Column _column = null;
        if (_findFirst!=null) {
          _column=_findFirst.getColumn();
        }
        deletionList.add(_column);
      }
      EObject _targetElement_4 = c.getTargetElement();
      Elem2Elem.elementsToCorr.remove(_targetElement_4);
      deletionList.add(_targetElement_4);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function_1);
    final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey k) -> {
      return Boolean.valueOf(((k.getColumn() == null) || (k.getReferencedTable() == null)));
    };
    List<EObject> _list = IteratorExtensions.<EObject>toList(IteratorExtensions.<ForeignKey>filter(Iterators.<ForeignKey>filter(this.targetModel.getAllContents(), ForeignKey.class), _function_2));
    Iterables.<EObject>addAll(deletionList, _list);
    final Consumer<EObject> _function_3 = (EObject e) -> {
      if ((e != null)) {
        EcoreUtil.delete(e, true);
      }
    };
    deletionList.forEach(_function_3);
  }

  /**
   * Removes all Ecore elements that have become orphaned because their corresponding
   * SQL target element was deleted.
   * 
   * <p>Special care is taken for {@link EReference}s that have an
   * {@link EReference#getEOpposite() EOpposite}: the opposite reference is also
   * collected for deletion so that the Ecore model remains consistent.</p>
   * 
   * <p>After collection all elements are deleted via {@link EcoreUtil#delete}.</p>
   */
  public void deleteUnreferencedSourceElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      final EObject source = c.getSourceElement();
      if ((source instanceof EReference)) {
        EReference _eOpposite = ((EReference)source).getEOpposite();
        boolean _tripleNotEquals = (_eOpposite != null);
        if (_tripleNotEquals) {
          EReference _eOpposite_1 = ((EReference)source).getEOpposite();
          deletionList.add(_eOpposite_1);
        }
      }
      EObject _sourceElement = c.getSourceElement();
      Elem2Elem.elementsToCorr.remove(_sourceElement);
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
