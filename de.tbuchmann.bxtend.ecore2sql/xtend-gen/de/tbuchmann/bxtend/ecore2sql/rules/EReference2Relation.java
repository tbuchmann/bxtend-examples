package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Action;
import sql.Annotation;
import sql.Column;
import sql.Event;
import sql.ForeignKey;
import sql.Key;
import sql.Property;
import sql.Schema;
import sql.Table;

/**
 * Bidirectional transformation rule that maps Ecore {@link EReference}s to SQL relational
 * constructs (foreign-key columns or relation tables).
 * 
 * <p>This is the most complex rule in the transformation.  Ecore offers several kinds of
 * references and each is mapped to a different SQL structure.  Annotations on the generated
 * SQL elements record the original Ecore structure so that the backward transformation can
 * reconstruct it faithfully.</p>
 * 
 * <h3>Forward mapping – four cases</h3>
 * 
 * <dl>
 *   <dt>1. Containment references (single or multi, unidirectional or bidirectional)</dt>
 *   <dd>A {@link Column} is added to the <em>owned</em> class's table that is a foreign key
 *       pointing back to the <em>owner</em> class table.  The column name encodes the
 *       directionality:
 *       <ul>
 *         <li>Unidirectional: {@code <refName>_inverse}</li>
 *         <li>Bidirectional: {@code <oppositeName>_inverse_<refName>}</li>
 *       </ul>
 *       Annotations: {@code "containment"}, {@code "unidirectional"} or {@code "bidirectional"},
 *       {@code "single"} or {@code "multi"}.</dd>
 * 
 *   <dt>2. Single-valued, unidirectional cross-references ({@code upperBound == 1, EOpposite == null})</dt>
 *   <dd>A foreign-key {@link Column} named after the reference is added to the <em>source</em>
 *       class table pointing to the <em>target</em> class table.  The {@code ON DELETE} action
 *       is set to {@code SET NULL}.
 *       Annotations: {@code "single"}, {@code "unidirectional"}, {@code "cross"}.</dd>
 * 
 *   <dt>3. Multi-valued, unidirectional cross-references ({@code upperBound != 1, EOpposite == null})</dt>
 *   <dd>A separate relation {@link Table} named {@code <OwnerClass>_<refName>} is created with
 *       two foreign-key columns: {@code id NOT NULL} → owner table, {@code reference} → target
 *       table.
 *       Annotations: {@code "cross"}, {@code "multi"}, {@code "unidirectional"}.</dd>
 * 
 *   <dt>4. Bidirectional cross-references (both ends non-containment)</dt>
 *   <dd>A single relation {@link Table} named
 *       {@code <OwnerClass>_<refName>_inverse_<TargetClass>_<oppositeName>} is created with
 *       two foreign-key columns: {@code source NOT NULL} and {@code target NOT NULL}.  The
 *       lexicographically smaller name controls creation to avoid duplicates.
 *       Additional multiplicity annotations ({@code "forwardSingle"}/{@code "forwardMulti"},
 *       {@code "backwardSingle"}/{@code "backwardMulti"}) enable exact reconstruction of both
 *       ends.</dd>
 * </dl>
 * 
 * <h3>Backward direction ({@link #targetToSource})</h3>
 * <p>Two passes restore Ecore references from the SQL model:</p>
 * <ol>
 *   <li>Every {@link Column} annotated with {@code "cross"} or {@code "containment"} yields an
 *       {@link EReference}.  The name, containment flag, type, and optional opposite are all
 *       decoded from the column name and annotations.</li>
 *   <li>Every {@link Table} annotated with {@code "cross"} or {@code "containment"} yields
 *       an {@link EReference} (and optionally its opposite) by parsing the table name and
 *       the foreign-key columns {@code source}/{@code target}/{@code reference}.</li>
 * </ol>
 * 
 * <h3>Overridden {@link #getOrCreateTargetElem} hook</h3>
 * <p>The standard {@link Elem2Elem#getOrCreateTargetElem} is overridden because EReference
 * mapping dynamically decides between a {@link Column} and a {@link Table} as the target
 * element type.  If the existing correspondence target has the wrong type (e.g. previously
 * a Table, now needs to be a Column), the old element is deleted and a new one is created.</p>
 * 
 * <h3>Instance fields</h3>
 * <ul>
 *   <li>{@link #targetName} – the name of the column or table to create (set before calling
 *       {@code getOrCreateTargetElem}).</li>
 *   <li>{@link #refTable} – the foreign-key target table.</li>
 *   <li>{@link #owningTable} – the table that should own the new column (for column targets).</li>
 * </ul>
 */
@SuppressWarnings("all")
public class EReference2Relation extends Class2Table {
  /**
   * The SQL name of the column or table to be created for the current reference.
   * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
   */
  private String targetName;

  /**
   * The table that the new foreign-key column should point to.
   * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
   */
  private Table refTable;

  /**
   * The table that should own the new foreign-key column.
   * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
   */
  private Table owningTable;

  /**
   * Constructs the rule and registers it under the {@code "ereference2relation"} rule identifier.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public EReference2Relation(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "ereference2relation";
  }

  /**
   * Maps every {@link EReference} in the source model to the appropriate SQL construct.
   * See the class-level documentation for the four cases handled.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<EReference> _function = (EReference eref) -> {
      Corr corr = this.getOrCreateCorrModelElement(eref, this.ruleID);
      final List<String> annotations = CollectionLiterals.<String>newArrayList();
      boolean _isContainment = eref.isContainment();
      if (_isContainment) {
        annotations.add("containment");
        EReference _eOpposite = eref.getEOpposite();
        boolean _tripleEquals = (_eOpposite == null);
        if (_tripleEquals) {
          annotations.add("unidirectional");
          String _name = eref.getName();
          String _plus = (_name + "_inverse");
          this.targetName = _plus;
        } else {
          String _name_1 = eref.getEOpposite().getName();
          String _plus_1 = (_name_1 + "_inverse_");
          String _name_2 = eref.getName();
          String _plus_2 = (_plus_1 + _name_2);
          this.targetName = _plus_2;
          annotations.add("bidirectional");
        }
        int _upperBound = eref.getUpperBound();
        boolean _equals = (_upperBound == 1);
        if (_equals) {
          annotations.add("single");
        } else {
          annotations.add("multi");
        }
        EObject _targetElement = this.getCorrModelElem(eref.getEContainingClass()).getTargetElement();
        this.refTable = ((Table) _targetElement);
        EObject _targetElement_1 = this.getCorrModelElem(eref.getEReferenceType()).getTargetElement();
        this.owningTable = ((Table) _targetElement_1);
        EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getColumn());
        final Column col = ((Column) _orCreateTargetElem);
        this.addAnnotations(col, annotations);
        this.addAnnotations(col.getKeys().get(0), annotations);
      } else {
        if (((eref.getUpperBound() == 1) && (eref.getEOpposite() == null))) {
          EObject _targetElement_2 = this.getCorrModelElem(eref.getEContainingClass()).getTargetElement();
          this.owningTable = ((Table) _targetElement_2);
          EObject _targetElement_3 = this.getCorrModelElem(eref.getEReferenceType()).getTargetElement();
          this.refTable = ((Table) _targetElement_3);
          this.targetName = eref.getName();
          EObject _orCreateTargetElem_1 = this.getOrCreateTargetElem(corr, this.targetPackage.getColumn());
          final Column col_1 = ((Column) _orCreateTargetElem_1);
          Key _get = col_1.getKeys().get(0);
          Event _get_1 = ((ForeignKey) _get).getOwnedEvents().get(0);
          _get_1.setAction(Action.SET_NULL);
          List<String> _asList = Arrays.<String>asList("single", "unidirectional", "cross");
          Iterables.<String>addAll(annotations, _asList);
          this.addAnnotations(col_1, annotations);
          this.addAnnotations(col_1.getKeys().get(0), annotations);
        } else {
          EReference _eOpposite_1 = eref.getEOpposite();
          boolean _tripleEquals_1 = (_eOpposite_1 == null);
          if (_tripleEquals_1) {
            String _name_3 = eref.getEContainingClass().getName();
            String _plus_3 = (_name_3 + "_");
            String _name_4 = eref.getName();
            String _plus_4 = (_plus_3 + _name_4);
            this.targetName = _plus_4;
            EObject _orCreateTargetElem_2 = this.getOrCreateTargetElem(corr, this.targetPackage.getTable());
            final Table tbl = ((Table) _orCreateTargetElem_2);
            tbl.setName(this.targetName);
            EObject _targetElement_4 = this.getCorrModelElem(eref.getEContainingClass().getEPackage()).getTargetElement();
            final Schema schema = ((Schema) _targetElement_4);
            EList<Table> _ownedTables = schema.getOwnedTables();
            _ownedTables.add(tbl);
            EObject _targetElement_5 = this.getCorrModelElem(eref.getEContainingClass()).getTargetElement();
            EList<Property> _properties = this.createForeignKeyAttr(tbl, "id", ((Table) _targetElement_5)).getProperties();
            _properties.add(Property.NOT_NULL);
            EObject _targetElement_6 = this.getCorrModelElem(eref.getEReferenceType()).getTargetElement();
            this.createForeignKeyAttr(tbl, "reference", ((Table) _targetElement_6));
            this.addAnnotations(tbl, Arrays.<String>asList("cross", "multi", "unidirectional"));
          } else {
            boolean _isContainment_1 = eref.getEOpposite().isContainment();
            boolean _not = (!_isContainment_1);
            if (_not) {
              String _name_5 = eref.getEContainingClass().getName();
              String _plus_5 = (_name_5 + "_");
              String _name_6 = eref.getName();
              final String sourceName = (_plus_5 + _name_6);
              String _name_7 = eref.getEType().getName();
              String _plus_6 = (_name_7 + "_");
              String _name_8 = eref.getEOpposite().getName();
              final String oppositeName = (_plus_6 + _name_8);
              int _compareTo = sourceName.compareTo(oppositeName);
              boolean _lessThan = (_compareTo < 0);
              if (_lessThan) {
                Corr _corrModelElem = this.getCorrModelElem(eref.getEOpposite());
                boolean _tripleNotEquals = (_corrModelElem != null);
                if (_tripleNotEquals) {
                  corr.setTargetElement(this.getCorrModelElem(eref.getEOpposite()).getTargetElement());
                  EcoreUtil.delete(this.getCorrModelElem(eref.getEOpposite()), true);
                }
                final String refTargetName = ((sourceName + "_inverse_") + oppositeName);
                EObject _orCreateTargetElem_3 = this.getOrCreateTargetElem(corr, this.targetPackage.getTable());
                final Table tbl_1 = ((Table) _orCreateTargetElem_3);
                tbl_1.setName(refTargetName);
                EObject _targetElement_7 = this.getCorrModelElem(eref.getEContainingClass().getEPackage()).getTargetElement();
                final Schema schema_1 = ((Schema) _targetElement_7);
                EList<Table> _ownedTables_1 = schema_1.getOwnedTables();
                _ownedTables_1.add(tbl_1);
                final Function1<ForeignKey, Boolean> _function_1 = (ForeignKey it) -> {
                  String _name_9 = it.getColumn().getName();
                  return Boolean.valueOf(Objects.equals(_name_9, "source"));
                };
                boolean _exists = IterableExtensions.<ForeignKey>exists(tbl_1.getOwnedForeignKeys(), _function_1);
                boolean _not_1 = (!_exists);
                if (_not_1) {
                  EObject _targetElement_8 = this.getCorrModelElem(eref.getEContainingClass()).getTargetElement();
                  EList<Property> _properties_1 = this.createForeignKeyAttr(tbl_1, "source", ((Table) _targetElement_8)).getProperties();
                  _properties_1.add(Property.NOT_NULL);
                } else {
                  final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey it) -> {
                    String _name_9 = it.getColumn().getName();
                    return Boolean.valueOf(Objects.equals(_name_9, "source"));
                  };
                  final ForeignKey fk = IterableExtensions.<ForeignKey>findFirst(tbl_1.getOwnedForeignKeys(), _function_2);
                  EObject _targetElement_9 = this.getCorrModelElem(eref.getEContainingClass()).getTargetElement();
                  fk.setReferencedTable(((Table) _targetElement_9));
                  EList<Property> _properties_2 = fk.getColumn().getProperties();
                  _properties_2.add(Property.NOT_NULL);
                }
                final Function1<ForeignKey, Boolean> _function_3 = (ForeignKey it) -> {
                  String _name_9 = it.getColumn().getName();
                  return Boolean.valueOf(Objects.equals(_name_9, "target"));
                };
                boolean _exists_1 = IterableExtensions.<ForeignKey>exists(tbl_1.getOwnedForeignKeys(), _function_3);
                boolean _not_2 = (!_exists_1);
                if (_not_2) {
                  EObject _targetElement_10 = this.getCorrModelElem(eref.getEReferenceType()).getTargetElement();
                  EList<Property> _properties_3 = this.createForeignKeyAttr(tbl_1, "target", ((Table) _targetElement_10)).getProperties();
                  _properties_3.add(Property.NOT_NULL);
                } else {
                  final Function1<ForeignKey, Boolean> _function_4 = (ForeignKey it) -> {
                    String _name_9 = it.getColumn().getName();
                    return Boolean.valueOf(Objects.equals(_name_9, "target"));
                  };
                  final ForeignKey fk_1 = IterableExtensions.<ForeignKey>findFirst(tbl_1.getOwnedForeignKeys(), _function_4);
                  EObject _targetElement_11 = this.getCorrModelElem(eref.getEReferenceType()).getTargetElement();
                  fk_1.setReferencedTable(((Table) _targetElement_11));
                  EList<Property> _properties_4 = fk_1.getColumn().getProperties();
                  _properties_4.add(Property.NOT_NULL);
                }
                List<String> _asList_1 = Arrays.<String>asList("cross", "bidirectional");
                Iterables.<String>addAll(annotations, _asList_1);
                int _upperBound_1 = eref.getUpperBound();
                boolean _equals_1 = (_upperBound_1 == 1);
                if (_equals_1) {
                  annotations.add("forwardSingle");
                } else {
                  annotations.add("forwardMulti");
                }
                int _upperBound_2 = eref.getEOpposite().getUpperBound();
                boolean _equals_2 = (_upperBound_2 == 1);
                if (_equals_2) {
                  annotations.add("backwardSingle");
                } else {
                  annotations.add("backwardMulti");
                }
                this.addAnnotations(tbl_1, annotations);
              } else {
                EcoreUtil.delete(corr, true);
              }
            }
          }
        }
      }
      this.refTable = null;
      this.owningTable = null;
      this.targetName = "";
    };
    IteratorExtensions.<EReference>forEach(Iterators.<EReference>filter(this.sourceModel.getAllContents(), EReference.class), _function);
  }

  /**
   * Reconstructs Ecore {@link EReference}s from SQL columns and tables annotated with
   * {@code "cross"} or {@code "containment"}.
   * 
   * <p>Two passes are performed: one for {@link Column}s (single-valued and containment),
   * one for {@link Table}s (multi-valued and bidirectional cross-references).</p>
   */
  @Override
  public void targetToSource() {
    final Function1<Column, Boolean> _function = (Column it) -> {
      final Function1<Annotation, Boolean> _function_1 = (Annotation it_1) -> {
        return Boolean.valueOf((Objects.equals(it_1.getAnnotation(), "cross") || Objects.equals(it_1.getAnnotation(), "containment")));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_1));
    };
    final Procedure1<Column> _function_1 = (Column col) -> {
      final Corr corr = this.getOrCreateCorrModelElement(col, this.ruleID);
      if (((corr.getSourceElement() != null) && (!(corr.getSourceElement() instanceof EReference)))) {
        EcoreUtil.delete(corr.getSourceElement(), true);
      }
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEReference());
      final EReference ref = ((EReference) _orCreateSourceElem);
      EObject _eContainer = col.eContainer();
      EObject _sourceElement = this.getCorrModelElem(((Table) _eContainer)).getSourceElement();
      final EClass sourceClass = ((EClass) _sourceElement);
      EObject _eContainer_1 = col.eContainer();
      final Function1<ForeignKey, Boolean> _function_2 = (ForeignKey it) -> {
        Column _column = it.getColumn();
        return Boolean.valueOf(Objects.equals(_column, col));
      };
      EObject _sourceElement_1 = this.getCorrModelElem(IterableExtensions.<ForeignKey>findFirst(((Table) _eContainer_1).getOwnedForeignKeys(), _function_2).getReferencedTable()).getSourceElement();
      final EClass targetClass = ((EClass) _sourceElement_1);
      int _xifexpression = (int) 0;
      final Function1<Annotation, Boolean> _function_3 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "single"));
      };
      boolean _exists = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_3);
      if (_exists) {
        _xifexpression = 1;
      } else {
        _xifexpression = (-1);
      }
      ref.setUpperBound(_xifexpression);
      final Function1<Annotation, Boolean> _function_4 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "containment"));
      };
      boolean _exists_1 = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_4);
      if (_exists_1) {
        ref.setContainment(true);
        final Function1<Annotation, Boolean> _function_5 = (Annotation it) -> {
          String _annotation = it.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "bidirectional"));
        };
        boolean _exists_2 = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_5);
        if (_exists_2) {
          ref.setName(col.getName().split("_")[2]);
          final Function1<EReference, Boolean> _function_6 = (EReference r) -> {
            String _name = r.getName();
            Object _get = col.getName().split("_")[0];
            return Boolean.valueOf(Objects.equals(_name, _get));
          };
          EReference invRef = IterableExtensions.<EReference>findFirst(sourceClass.getEReferences(), _function_6);
          if ((invRef == null)) {
            EObject _createSourceElement = this.createSourceElement(this.sourcePackage.getEReference());
            invRef = ((EReference) _createSourceElement);
          }
          invRef.setName(col.getName().split("_")[0]);
          invRef.setEType(targetClass);
          ref.setEOpposite(invRef);
          invRef.setEOpposite(ref);
          EList<EStructuralFeature> _eStructuralFeatures = sourceClass.getEStructuralFeatures();
          _eStructuralFeatures.add(invRef);
        } else {
          EReference _eOpposite = ref.getEOpposite();
          boolean _tripleNotEquals = (_eOpposite != null);
          if (_tripleNotEquals) {
            EcoreUtil.delete(ref.getEOpposite(), true);
          }
          ref.setName(col.getName().split("_")[0]);
        }
        ref.setEType(sourceClass);
        EList<EStructuralFeature> _eStructuralFeatures_1 = targetClass.getEStructuralFeatures();
        _eStructuralFeatures_1.add(ref);
      } else {
        ref.setName(col.getName());
        ref.setEType(targetClass);
        EList<EStructuralFeature> _eStructuralFeatures_2 = sourceClass.getEStructuralFeatures();
        _eStructuralFeatures_2.add(ref);
      }
    };
    IteratorExtensions.<Column>forEach(IteratorExtensions.<Column>filter(Iterators.<Column>filter(this.targetModel.getAllContents(), Column.class), _function), _function_1);
    final Function1<Table, Boolean> _function_2 = (Table t) -> {
      final Function1<Annotation, Boolean> _function_3 = (Annotation a) -> {
        return Boolean.valueOf((Objects.equals(a.getAnnotation(), "cross") || Objects.equals(a.getAnnotation(), "containment")));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(t.getOwnedAnnotations(), _function_3));
    };
    final Procedure1<Table> _function_3 = (Table tab) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tab, this.ruleID);
      if (((corr.getSourceElement() != null) && (!(corr.getSourceElement() instanceof EReference)))) {
        EcoreUtil.delete(corr.getSourceElement(), true);
      }
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEReference());
      final EReference ref = ((EReference) _orCreateSourceElem);
      int _xifexpression = (int) 0;
      final Function1<Annotation, Boolean> _function_4 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "forwardSingle"));
      };
      boolean _exists = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_4);
      if (_exists) {
        _xifexpression = 1;
      } else {
        _xifexpression = (-1);
      }
      ref.setUpperBound(_xifexpression);
      final Function1<Annotation, Boolean> _function_5 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "unidirectional"));
      };
      boolean _exists_1 = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_5);
      if (_exists_1) {
        ref.setName(tab.getName().split("_")[1]);
        final Function1<ForeignKey, Boolean> _function_6 = (ForeignKey f) -> {
          String _name = f.getColumn().getName();
          return Boolean.valueOf(Objects.equals(_name, "reference"));
        };
        EObject _sourceElement = this.getCorrModelElem(IterableExtensions.<ForeignKey>findFirst(tab.getOwnedForeignKeys(), _function_6).getReferencedTable()).getSourceElement();
        ref.setEType(((EClass) _sourceElement));
        final EClass parentEClass = this.findClassByName(tab.getName().split("_")[0]);
        EList<EStructuralFeature> _eStructuralFeatures = parentEClass.getEStructuralFeatures();
        _eStructuralFeatures.add(ref);
        EReference _eOpposite = ref.getEOpposite();
        boolean _tripleNotEquals = (_eOpposite != null);
        if (_tripleNotEquals) {
          EcoreUtil.delete(ref.getEOpposite(), true);
          ref.setEOpposite(null);
        }
      } else {
        ref.setName(tab.getName().split("_")[1]);
        final EClass sourceEClass = this.findClassByName(tab.getName().split("_")[0]);
        final EClass targetEClass = this.findClassByName(tab.getName().split("_")[3]);
        ref.setEType(targetEClass);
        EReference invRef = ref.getEOpposite();
        if ((invRef == null)) {
          EObject _createSourceElement = this.createSourceElement(this.sourcePackage.getEReference());
          invRef = ((EReference) _createSourceElement);
        }
        invRef.setName(tab.getName().split("_")[4]);
        int _xifexpression_1 = (int) 0;
        final Function1<Annotation, Boolean> _function_7 = (Annotation it) -> {
          String _annotation = it.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "backwardSingle"));
        };
        boolean _exists_2 = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_7);
        if (_exists_2) {
          _xifexpression_1 = 1;
        } else {
          _xifexpression_1 = (-1);
        }
        invRef.setUpperBound(_xifexpression_1);
        invRef.setEType(sourceEClass);
        invRef.setEOpposite(ref);
        ref.setEOpposite(invRef);
        EList<EStructuralFeature> _eStructuralFeatures_1 = sourceEClass.getEStructuralFeatures();
        _eStructuralFeatures_1.add(ref);
        EList<EStructuralFeature> _eStructuralFeatures_2 = targetEClass.getEStructuralFeatures();
        _eStructuralFeatures_2.add(invRef);
      }
    };
    IteratorExtensions.<Table>forEach(IteratorExtensions.<Table>filter(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function_2), _function_3);
  }

  /**
   * Reconciles concurrent edits: re-runs {@link #sourceToTarget()} (idempotent — handles
   * updates and reshaping between Column/Table representations for existing references, and
   * creates SQL elements for new source references), then absorbs any
   * {@code "cross"}/{@code "containment"}-annotated {@link Column}/{@link Table} that still
   * has no correspondence at all — a genuine target-side insertion — using the same logic as
   * {@link #targetToSource()}.
   */
  @Override
  public void synch() {
    this.sourceToTarget();
    final Function1<Column, Boolean> _function = (Column it) -> {
      final Function1<Annotation, Boolean> _function_1 = (Annotation it_1) -> {
        return Boolean.valueOf((Objects.equals(it_1.getAnnotation(), "cross") || Objects.equals(it_1.getAnnotation(), "containment")));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_1));
    };
    final Function1<Column, Boolean> _function_1 = (Column it) -> {
      Corr _corrModelElem = this.getCorrModelElem(it);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final Procedure1<Column> _function_2 = (Column col) -> {
      final Corr corr = this.getOrCreateCorrModelElement(col, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEReference());
      final EReference ref = ((EReference) _orCreateSourceElem);
      EObject _eContainer = col.eContainer();
      EObject _sourceElement = this.getCorrModelElem(((Table) _eContainer)).getSourceElement();
      final EClass sourceClass = ((EClass) _sourceElement);
      EObject _eContainer_1 = col.eContainer();
      final Function1<ForeignKey, Boolean> _function_3 = (ForeignKey it) -> {
        Column _column = it.getColumn();
        return Boolean.valueOf(Objects.equals(_column, col));
      };
      EObject _sourceElement_1 = this.getCorrModelElem(IterableExtensions.<ForeignKey>findFirst(((Table) _eContainer_1).getOwnedForeignKeys(), _function_3).getReferencedTable()).getSourceElement();
      final EClass targetClass = ((EClass) _sourceElement_1);
      int _xifexpression = (int) 0;
      final Function1<Annotation, Boolean> _function_4 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "single"));
      };
      boolean _exists = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_4);
      if (_exists) {
        _xifexpression = 1;
      } else {
        _xifexpression = (-1);
      }
      ref.setUpperBound(_xifexpression);
      final Function1<Annotation, Boolean> _function_5 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "containment"));
      };
      boolean _exists_1 = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_5);
      if (_exists_1) {
        ref.setContainment(true);
        final Function1<Annotation, Boolean> _function_6 = (Annotation it) -> {
          String _annotation = it.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "bidirectional"));
        };
        boolean _exists_2 = IterableExtensions.<Annotation>exists(col.getOwnedAnnotations(), _function_6);
        if (_exists_2) {
          ref.setName(col.getName().split("_")[2]);
          final Function1<EReference, Boolean> _function_7 = (EReference r) -> {
            String _name = r.getName();
            Object _get = col.getName().split("_")[0];
            return Boolean.valueOf(Objects.equals(_name, _get));
          };
          EReference invRef = IterableExtensions.<EReference>findFirst(sourceClass.getEReferences(), _function_7);
          if ((invRef == null)) {
            EObject _createSourceElement = this.createSourceElement(this.sourcePackage.getEReference());
            invRef = ((EReference) _createSourceElement);
          }
          invRef.setName(col.getName().split("_")[0]);
          invRef.setEType(targetClass);
          ref.setEOpposite(invRef);
          invRef.setEOpposite(ref);
          EList<EStructuralFeature> _eStructuralFeatures = sourceClass.getEStructuralFeatures();
          _eStructuralFeatures.add(invRef);
        } else {
          EReference _eOpposite = ref.getEOpposite();
          boolean _tripleNotEquals = (_eOpposite != null);
          if (_tripleNotEquals) {
            EcoreUtil.delete(ref.getEOpposite(), true);
          }
          ref.setName(col.getName().split("_")[0]);
        }
        ref.setEType(sourceClass);
        EList<EStructuralFeature> _eStructuralFeatures_1 = targetClass.getEStructuralFeatures();
        _eStructuralFeatures_1.add(ref);
      } else {
        ref.setName(col.getName());
        ref.setEType(targetClass);
        EList<EStructuralFeature> _eStructuralFeatures_2 = sourceClass.getEStructuralFeatures();
        _eStructuralFeatures_2.add(ref);
      }
    };
    IteratorExtensions.<Column>forEach(IteratorExtensions.<Column>filter(IteratorExtensions.<Column>filter(Iterators.<Column>filter(this.targetModel.getAllContents(), Column.class), _function), _function_1), _function_2);
    final Function1<Table, Boolean> _function_3 = (Table t) -> {
      final Function1<Annotation, Boolean> _function_4 = (Annotation a) -> {
        return Boolean.valueOf((Objects.equals(a.getAnnotation(), "cross") || Objects.equals(a.getAnnotation(), "containment")));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(t.getOwnedAnnotations(), _function_4));
    };
    final Function1<Table, Boolean> _function_4 = (Table it) -> {
      Corr _corrModelElem = this.getCorrModelElem(it);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final Procedure1<Table> _function_5 = (Table tab) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tab, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEReference());
      final EReference ref = ((EReference) _orCreateSourceElem);
      int _xifexpression = (int) 0;
      final Function1<Annotation, Boolean> _function_6 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "forwardSingle"));
      };
      boolean _exists = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_6);
      if (_exists) {
        _xifexpression = 1;
      } else {
        _xifexpression = (-1);
      }
      ref.setUpperBound(_xifexpression);
      final Function1<Annotation, Boolean> _function_7 = (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "unidirectional"));
      };
      boolean _exists_1 = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_7);
      if (_exists_1) {
        ref.setName(tab.getName().split("_")[1]);
        final Function1<ForeignKey, Boolean> _function_8 = (ForeignKey f) -> {
          String _name = f.getColumn().getName();
          return Boolean.valueOf(Objects.equals(_name, "reference"));
        };
        EObject _sourceElement = this.getCorrModelElem(IterableExtensions.<ForeignKey>findFirst(tab.getOwnedForeignKeys(), _function_8).getReferencedTable()).getSourceElement();
        ref.setEType(((EClass) _sourceElement));
        final EClass parentEClass = this.findClassByName(tab.getName().split("_")[0]);
        EList<EStructuralFeature> _eStructuralFeatures = parentEClass.getEStructuralFeatures();
        _eStructuralFeatures.add(ref);
        EReference _eOpposite = ref.getEOpposite();
        boolean _tripleNotEquals = (_eOpposite != null);
        if (_tripleNotEquals) {
          EcoreUtil.delete(ref.getEOpposite(), true);
          ref.setEOpposite(null);
        }
      } else {
        ref.setName(tab.getName().split("_")[1]);
        final EClass sourceEClass = this.findClassByName(tab.getName().split("_")[0]);
        final EClass targetEClass = this.findClassByName(tab.getName().split("_")[3]);
        ref.setEType(targetEClass);
        EReference invRef = ref.getEOpposite();
        if ((invRef == null)) {
          EObject _createSourceElement = this.createSourceElement(this.sourcePackage.getEReference());
          invRef = ((EReference) _createSourceElement);
        }
        invRef.setName(tab.getName().split("_")[4]);
        int _xifexpression_1 = (int) 0;
        final Function1<Annotation, Boolean> _function_9 = (Annotation it) -> {
          String _annotation = it.getAnnotation();
          return Boolean.valueOf(Objects.equals(_annotation, "backwardSingle"));
        };
        boolean _exists_2 = IterableExtensions.<Annotation>exists(tab.getOwnedAnnotations(), _function_9);
        if (_exists_2) {
          _xifexpression_1 = 1;
        } else {
          _xifexpression_1 = (-1);
        }
        invRef.setUpperBound(_xifexpression_1);
        invRef.setEType(sourceEClass);
        invRef.setEOpposite(ref);
        ref.setEOpposite(invRef);
        EList<EStructuralFeature> _eStructuralFeatures_1 = sourceEClass.getEStructuralFeatures();
        _eStructuralFeatures_1.add(ref);
        EList<EStructuralFeature> _eStructuralFeatures_2 = targetEClass.getEStructuralFeatures();
        _eStructuralFeatures_2.add(invRef);
      }
    };
    IteratorExtensions.<Table>forEach(IteratorExtensions.<Table>filter(IteratorExtensions.<Table>filter(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function_3), _function_4), _function_5);
  }

  /**
   * Overrides the standard {@link Elem2Elem#getOrCreateTargetElem} to support the dynamic
   * choice between a {@link Column} target and a {@link Table} target.
   * 
   * <p>For {@link Column} targets the method delegates to
   * {@link Class2Table#createForeignKeyAttr} using the pre-set {@link #owningTable},
   * {@link #targetName}, and {@link #refTable} instance fields.  If the existing
   * correspondence target is of the wrong type it is deleted first.</p>
   * 
   * @param corr  the correspondence whose target element is needed
   * @param clazz the desired metaclass ({@code targetPackage.column} or {@code targetPackage.table})
   * @return the existing or newly created SQL element
   */
  @Override
  public EObject getOrCreateTargetElem(final Corr corr, final EClass clazz) {
    EObject target = corr.getTargetElement();
    EClass _column = this.targetPackage.getColumn();
    boolean _equals = Objects.equals(clazz, _column);
    if (_equals) {
      if (((target != null) && (!(target instanceof Column)))) {
        EcoreUtil.delete(target, true);
        target = this.createForeignKeyAttr(this.owningTable, this.targetName, this.refTable);
        corr.setTargetElement(target);
      } else {
        if ((target == null)) {
          target = this.createForeignKeyAttr(this.owningTable, this.targetName, this.refTable);
          corr.setTargetElement(target);
        } else {
          ((Column) target).setName(this.targetName);
          ((Column) target).setType("int");
          Key _get = ((Column) target).getKeys().get(0);
          ((ForeignKey) _get).setReferencedTable(this.refTable);
        }
      }
    } else {
      EClass _table = this.targetPackage.getTable();
      boolean _equals_1 = Objects.equals(clazz, _table);
      if (_equals_1) {
        if (((target != null) && (!(target instanceof Table)))) {
          EcoreUtil.delete(target, true);
          target = this.targetFactory.createTable();
          corr.setTargetElement(target);
        } else {
          if ((target == null)) {
            target = this.targetFactory.createTable();
            corr.setTargetElement(target);
          }
        }
      }
    }
    return target;
  }

  /**
   * Finds the first {@link EClass} in the source model with the given name.
   * 
   * @param clzName the class name to look up
   * @return the matching {@link EClass}, or {@code null} if not found
   */
  public EClass findClassByName(final String clzName) {
    final Function1<EClass, Boolean> _function = (EClass c) -> {
      String _name = c.getName();
      return Boolean.valueOf(Objects.equals(_name, clzName));
    };
    return IteratorExtensions.<EClass>findFirst(Iterators.<EClass>filter(this.sourceModel.getAllContents(), EClass.class), _function);
  }
}
