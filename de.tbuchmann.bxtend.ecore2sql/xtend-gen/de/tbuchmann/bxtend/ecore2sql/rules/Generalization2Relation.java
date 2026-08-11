package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Annotation;
import sql.ForeignKey;
import sql.Table;

/**
 * Bidirectional transformation rule that maps Ecore generalisation (inheritance) relationships
 * to SQL foreign-key constraints between class tables.
 * 
 * <p>This rule extends {@link Class2Table} so that it can reuse the {@link Class2Table#createForeignKey}
 * helper to add foreign-key columns.</p>
 * 
 * <h3>Mapping semantics</h3>
 * <p>Ecore generalisation ({@link EClass#getESuperTypes()}) is represented at the SQL level
 * by a foreign key from a sub-class table's primary-key column to the super-class table.
 * Two mutually exclusive annotation tags on the foreign key distinguish the two cases:</p>
 * <ul>
 *   <li>{@code "superType"} – the referenced table is the direct Ecore super-class table.</li>
 *   <li>{@code "root"} – the class has no explicit super-class, so the foreign key points to
 *       the special {@code "EObject"} sentinel table (every class hierarchy must have a root
 *       that links into the global object identity table).</li>
 * </ul>
 * 
 * <h3>Forward direction ({@link #sourceToTarget})</h3>
 * <p>The method performs two passes over all {@link EClass} elements in the source model:</p>
 * <ol>
 *   <li><b>Classes with super-types:</b> any existing {@code "root"} foreign key is deleted and
 *       replaced by a {@code "superType"} foreign key pointing to the first super-class table.
 *       Only one super-type is supported (single inheritance).</li>
 *   <li><b>Classes without super-types (root classes):</b> any existing {@code "superType"} foreign
 *       key is deleted and replaced by a {@code "root"} foreign key pointing to the
 *       {@code "EObject"} sentinel table.</li>
 * </ol>
 * 
 * <h3>Backward direction ({@link #targetToSource})</h3>
 * <p>For every {@link Table} in the target model the rule inspects its foreign keys:</p>
 * <ul>
 *   <li>If a {@code "superType"} foreign key exists the corresponding source {@link EClass} is
 *       given the class of the referenced table as its direct super-type.</li>
 *   <li>If only a {@code "root"} foreign key exists the {@code ESuperTypes} list is cleared,
 *       marking the class as a root class.</li>
 * </ul>
 */
@SuppressWarnings("all")
public class Generalization2Relation extends Class2Table {
  /**
   * Constructs the rule and registers it under the {@code "generalization2relation"} rule identifier.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public Generalization2Relation(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "generalization2relation";
  }

  /**
   * Propagates Ecore generalisation edges to SQL foreign-key constraints.
   * See class-level documentation for the detailed two-pass algorithm.
   */
  @Override
  public void sourceToTarget() {
    final Function1<EClass, Boolean> _function = (EClass cl) -> {
      int _size = cl.getESuperTypes().size();
      return Boolean.valueOf((_size != 0));
    };
    final Procedure1<EClass> _function_1 = (EClass ec) -> {
      EObject _targetElement = this.getCorrModelElem(ec).getTargetElement();
      final Table targetTable = ((Table) _targetElement);
      EObject _targetElement_1 = this.getCorrModelElem(ec.getESuperTypes().get(0)).getTargetElement();
      final Table superTypeTable = ((Table) _targetElement_1);
      final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey it) -> {
        final Function1<Annotation, Boolean> _function_3 = (Annotation it_1) -> {
          String _annotation = it_1.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "root"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_3));
      };
      final ForeignKey root = IterableExtensions.<ForeignKey>findFirst(targetTable.getOwnedForeignKeys(), _function_2);
      if ((root != null)) {
        EcoreUtil.delete(root, true);
      }
      final Function1<ForeignKey, Boolean> _function_3 = (ForeignKey it) -> {
        final Function1<Annotation, Boolean> _function_4 = (Annotation it_1) -> {
          String _annotation = it_1.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "superType"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_4));
      };
      final Function1<ForeignKey, Boolean> _function_4 = (ForeignKey it) -> {
        Table _referencedTable = it.getReferencedTable();
        return Boolean.valueOf(Objects.equals(_referencedTable, superTypeTable));
      };
      boolean _exists = IterableExtensions.<ForeignKey>exists(IterableExtensions.<ForeignKey>filter(targetTable.getOwnedForeignKeys(), _function_3), _function_4);
      boolean _not = (!_exists);
      if (_not) {
        final ForeignKey key = this.createForeignKey(targetTable.getOwnedPrimaryKey().getColumn(), superTypeTable);
        this.addAnnotations(key, Arrays.<String>asList("superType"));
      }
    };
    IteratorExtensions.<EClass>forEach(IteratorExtensions.<EClass>filter(Iterators.<EClass>filter(this.sourceModel.getAllContents(), EClass.class), _function), _function_1);
    final Function1<EClass, Boolean> _function_2 = (EClass cl) -> {
      int _size = cl.getESuperTypes().size();
      return Boolean.valueOf((_size == 0));
    };
    final Procedure1<EClass> _function_3 = (EClass ec) -> {
      EObject _targetElement = this.getCorrModelElem(ec).getTargetElement();
      final Table targetTable = ((Table) _targetElement);
      Table _eObjectTable = this.eObjectTable(targetTable.getOwningSchema());
      final Table superTypeTable = ((Table) _eObjectTable);
      final Function1<ForeignKey, Boolean> _function_4 = (ForeignKey it) -> {
        final Function1<Annotation, Boolean> _function_5 = (Annotation it_1) -> {
          String _annotation = it_1.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "superType"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_5));
      };
      final ForeignKey superTable = IterableExtensions.<ForeignKey>findFirst(targetTable.getOwnedForeignKeys(), _function_4);
      if ((superTable != null)) {
        EcoreUtil.delete(superTable, true);
      }
      final Function1<ForeignKey, Boolean> _function_5 = (ForeignKey it) -> {
        final Function1<Annotation, Boolean> _function_6 = (Annotation it_1) -> {
          String _annotation = it_1.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "root"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_6));
      };
      final Function1<ForeignKey, Boolean> _function_6 = (ForeignKey it) -> {
        Table _referencedTable = it.getReferencedTable();
        return Boolean.valueOf(Objects.equals(_referencedTable, superTypeTable));
      };
      boolean _exists = IterableExtensions.<ForeignKey>exists(IterableExtensions.<ForeignKey>filter(targetTable.getOwnedForeignKeys(), _function_5), _function_6);
      boolean _not = (!_exists);
      if (_not) {
        final ForeignKey key = this.createForeignKey(targetTable.getOwnedPrimaryKey().getColumn(), superTypeTable);
        this.addAnnotations(key, Arrays.<String>asList("root"));
      }
    };
    IteratorExtensions.<EClass>forEach(IteratorExtensions.<EClass>filter(Iterators.<EClass>filter(this.sourceModel.getAllContents(), EClass.class), _function_2), _function_3);
  }

  /**
   * Reconstructs Ecore generalisation edges from SQL {@code "superType"} foreign keys.
   * See class-level documentation for the detailed algorithm.
   */
  @Override
  public void targetToSource() {
    final Procedure1<Table> _function = (Table tbl) -> {
      final Function1<ForeignKey, Boolean> _function_1 = (ForeignKey fk) -> {
        final Function1<Annotation, Boolean> _function_2 = (Annotation a) -> {
          String _annotation = a.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "superType"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(fk.getOwnedAnnotations(), _function_2));
      };
      final ForeignKey foreignKey = IterableExtensions.<ForeignKey>findFirst(tbl.getOwnedForeignKeys(), _function_1);
      if ((foreignKey != null)) {
        EObject _sourceElement = this.getCorrModelElem(tbl).getSourceElement();
        final EClass sourceClass = ((EClass) _sourceElement);
        EObject _sourceElement_1 = this.getCorrModelElem(foreignKey.getReferencedTable()).getSourceElement();
        final EClass sourceSuperClass = ((EClass) _sourceElement_1);
        EList<EClass> _eSuperTypes = sourceClass.getESuperTypes();
        _eSuperTypes.add(sourceSuperClass);
      }
      final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey fk) -> {
        final Function1<Annotation, Boolean> _function_3 = (Annotation a) -> {
          String _annotation = a.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "root"));
        };
        return Boolean.valueOf(IterableExtensions.<Annotation>exists(fk.getOwnedAnnotations(), _function_3));
      };
      final ForeignKey rootKey = IterableExtensions.<ForeignKey>findFirst(tbl.getOwnedForeignKeys(), _function_2);
      if ((rootKey != null)) {
        EObject _sourceElement_2 = this.getCorrModelElem(tbl).getSourceElement();
        final EClass sourceClass_1 = ((EClass) _sourceElement_2);
        sourceClass_1.getESuperTypes().clear();
      }
    };
    IteratorExtensions.<Table>forEach(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function);
  }

  /**
   * Reconciles concurrent edits to the inheritance hierarchy.
   * 
   * <p>Unlike the other rules, this one owns no correspondences of its own — it only
   * annotates foreign keys on tables already linked by {@link Class2Table}. Generalisation
   * is therefore treated as state fully derived from the source model's current
   * {@code ESuperTypes}, so synchronisation simply re-runs the (idempotent) forward direction
   * rather than absorbing anything from the target side.</p>
   */
  @Override
  public void synch() {
    this.sourceToTarget();
  }
}
