package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Action;
import sql.Annotation;
import sql.Column;
import sql.Condition;
import sql.Event;
import sql.ForeignKey;
import sql.PrimaryKey;
import sql.Property;
import sql.Schema;
import sql.Table;

/**
 * Bidirectional transformation rule that maps an Ecore {@link EClass} to a SQL {@link Table}.
 * 
 * <h3>Mapping semantics</h3>
 * <p>Every Ecore class – whether abstract or concrete – is mapped to its own table (the
 * <em>class-per-table</em> inheritance strategy).  Two annotations are stored on the
 * generated table:</p>
 * <ul>
 *   <li>{@code "class"} – marks the table as originating from an EClass.</li>
 *   <li>{@code "abstract"} or {@code "concrete"} – records the abstract-flag of the EClass
 *       so that the backward transformation can reconstruct it faithfully.</li>
 * </ul>
 * 
 * <h3>Forward direction ({@link #sourceToTarget})</h3>
 * <ol>
 *   <li>For every {@link EClass} in the source model a correspondence tagged
 *       {@code "class2table"} is obtained or created.</li>
 *   <li>The corresponding {@link Table} is obtained or created and given the class name as
 *       its SQL name.</li>
 *   <li>A two-column primary-key structure ({@code id INT NOT NULL}) is added if not already
 *       present (see {@link #createPrimaryKeyAttr}).</li>
 *   <li>An entry for the class is added to the special {@code EObject} sentinel table as a
 *       {@link sql.ForeignKey foreign-key} column (see {@link #createForeignKeyAttr}), which
 *       implements the cross-table identity link.  The column is {@code UNIQUE} so that each
 *       object appears at most once per class.</li>
 *   <li>Annotations {@code "class"} and either {@code "abstract"} or {@code "concrete"} are
 *       attached via {@link Elem2Elem#addAnnotations}.</li>
 * </ol>
 * 
 * <h3>Backward direction ({@link #targetToSource})</h3>
 * <ol>
 *   <li>Every {@link Table} in the target model that carries a {@code "class"} annotation
 *       (and is not the sentinel {@code "EObject"} table) is mapped to an {@link EClass}.</li>
 *   <li>The {@code abstract} flag is set based on whether the {@code "abstract"} annotation
 *       is present.</li>
 *   <li>The class is added to the {@link EPackage} that corresponds to the owning
 *       {@link Schema}.</li>
 * </ol>
 * 
 * <h3>Helper methods</h3>
 * <ul>
 *   <li>{@link #createPrimaryKeyAttr} – creates an {@code id INT NOT NULL} column and a
 *       {@link sql.PrimaryKey} object in the given table.</li>
 *   <li>{@link #createForeignKeyAttr} – creates a foreign-key column in {@code owner} that
 *       references {@code refTable}, together with an {@code ON DELETE CASCADE} event.</li>
 *   <li>{@link #kind} – returns {@code "abstract"} or {@code "concrete"} for an EClass.</li>
 *   <li>{@link #eObjectTable} – retrieves the sentinel {@code EObject} table from a schema.</li>
 *   <li>{@link #createColumn} – creates or reuses a column in a table.</li>
 * </ul>
 */
@SuppressWarnings("all")
public class Class2Table extends Elem2Elem {
  /**
   * Constructs the rule and registers it under the {@code "class2table"} rule identifier.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public Class2Table(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "class2table";
  }

  /**
   * Creates (or updates) a SQL {@link Table} for every {@link EClass} in the source model.
   * See class-level documentation for the detailed algorithm.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<EClass> _function = (EClass ec) -> {
      final Corr corr = this.getOrCreateCorrModelElement(ec, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getTable());
      final Table tbl = ((Table) _orCreateTargetElem);
      tbl.setName(ec.getName());
      EObject _targetElement = this.getCorrModelElem(ec.getEPackage()).getTargetElement();
      final Schema schema = ((Schema) _targetElement);
      EList<Table> _ownedTables = schema.getOwnedTables();
      _ownedTables.add(tbl);
      PrimaryKey _ownedPrimaryKey = tbl.getOwnedPrimaryKey();
      boolean _tripleEquals = (_ownedPrimaryKey == null);
      if (_tripleEquals) {
        this.createPrimaryKeyAttr(tbl);
      }
      int _size = tbl.getOwnedAnnotations().size();
      boolean _equals = (_size == 0);
      if (_equals) {
        this.addAnnotations(tbl, Arrays.<String>asList("class", this.kind(ec)));
      }
      final Function1<ForeignKey, Boolean> _function_1 = (ForeignKey k) -> {
        Table _owningTable = k.getOwningTable();
        Table _eObjectTable = this.eObjectTable(schema);
        return Boolean.valueOf(Objects.equals(_owningTable, _eObjectTable));
      };
      final ForeignKey key = IterableExtensions.<ForeignKey>findFirst(tbl.getReferencingForeignKeys(), _function_1);
      if ((key == null)) {
        final Column col = this.createForeignKeyAttr(this.eObjectTable(schema), tbl.getName(), tbl);
        col.getProperties().clear();
        EList<Property> _properties = col.getProperties();
        _properties.add(Property.UNIQUE);
      } else {
        Column _column = key.getColumn();
        _column.setName(tbl.getName());
        EList<Property> _properties_1 = key.getColumn().getProperties();
        _properties_1.add(Property.UNIQUE);
        key.setReferencedTable(tbl);
      }
    };
    IteratorExtensions.<EClass>forEach(Iterators.<EClass>filter(this.sourceModel.getAllContents(), EClass.class), _function);
  }

  /**
   * Creates (or updates) an Ecore {@link EClass} for every {@link Table} annotated with
   * {@code "class"} (excluding the {@code "EObject"} sentinel table).
   * See class-level documentation for the detailed algorithm.
   */
  @Override
  public void targetToSource() {
    final Function1<Table, Boolean> _function = (Table t) -> {
      String _name = t.getName();
      return Boolean.valueOf((!Objects.equals(_name, "EObject")));
    };
    final Function1<Table, Boolean> _function_1 = (Table it) -> {
      final Function1<Annotation, Boolean> _function_2 = (Annotation it_1) -> {
        String _annotation = it_1.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "class"));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_2));
    };
    final Procedure1<Table> _function_2 = (Table tbl) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tbl, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEClass());
      final EClass ec = ((EClass) _orCreateSourceElem);
      ec.setName(tbl.getName());
      final Function1<Annotation, Boolean> _function_3 = (Annotation a) -> {
        String _annotation = a.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "abstract"));
      };
      ec.setAbstract(IterableExtensions.<Annotation>exists(tbl.getOwnedAnnotations(), _function_3));
      EObject _sourceElement = this.getCorrModelElem(tbl.getOwningSchema()).getSourceElement();
      final EPackage ep = ((EPackage) _sourceElement);
      EList<EClassifier> _eClassifiers = ep.getEClassifiers();
      _eClassifiers.add(ec);
    };
    IteratorExtensions.<Table>forEach(IteratorExtensions.<Table>filter(IteratorExtensions.<Table>filter(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function), _function_1), _function_2);
  }

  /**
   * Reconciles concurrent edits: re-runs {@link #sourceToTarget()} (idempotent, reasserts
   * existing class/table correspondences and creates tables for new classes), then absorbs
   * any {@code "class"}-annotated {@link Table} that still has no correspondence at all — a
   * genuine target-side insertion — using the same logic as {@link #targetToSource()}.
   */
  @Override
  public void synch() {
    this.sourceToTarget();
    final Function1<Table, Boolean> _function = (Table t) -> {
      String _name = t.getName();
      return Boolean.valueOf((!Objects.equals(_name, "EObject")));
    };
    final Function1<Table, Boolean> _function_1 = (Table it) -> {
      final Function1<Annotation, Boolean> _function_2 = (Annotation it_1) -> {
        String _annotation = it_1.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "class"));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_2));
    };
    final Function1<Table, Boolean> _function_2 = (Table it) -> {
      Corr _corrModelElem = this.getCorrModelElem(it);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final Procedure1<Table> _function_3 = (Table tbl) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tbl, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEClass());
      final EClass ec = ((EClass) _orCreateSourceElem);
      ec.setName(tbl.getName());
      final Function1<Annotation, Boolean> _function_4 = (Annotation a) -> {
        String _annotation = a.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "abstract"));
      };
      ec.setAbstract(IterableExtensions.<Annotation>exists(tbl.getOwnedAnnotations(), _function_4));
      EObject _sourceElement = this.getCorrModelElem(tbl.getOwningSchema()).getSourceElement();
      final EPackage ep = ((EPackage) _sourceElement);
      EList<EClassifier> _eClassifiers = ep.getEClassifiers();
      _eClassifiers.add(ec);
    };
    IteratorExtensions.<Table>forEach(IteratorExtensions.<Table>filter(IteratorExtensions.<Table>filter(IteratorExtensions.<Table>filter(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function), _function_1), _function_2), _function_3);
  }

  /**
   * Creates an {@code id INT NOT NULL} {@link sql.Column} and an associated
   * {@link sql.PrimaryKey} on the given table, representing the class-level primary key.
   * 
   * @param owner the {@link Table} that should receive the primary key
   */
  public void createPrimaryKeyAttr(final Table owner) {
    Column _createColumn = this.targetFactory.createColumn();
    final Procedure1<Column> _function = (Column it) -> {
      it.setName("id");
      it.setType("int");
      EList<Property> _properties = it.getProperties();
      _properties.add(Property.NOT_NULL);
    };
    final Column col = ObjectExtensions.<Column>operator_doubleArrow(_createColumn, _function);
    EList<Column> _ownedColumns = owner.getOwnedColumns();
    _ownedColumns.add(col);
    PrimaryKey _createPrimaryKey = this.targetFactory.createPrimaryKey();
    final Procedure1<PrimaryKey> _function_1 = (PrimaryKey it) -> {
      it.setColumn(col);
    };
    final PrimaryKey key = ObjectExtensions.<PrimaryKey>operator_doubleArrow(_createPrimaryKey, _function_1);
    owner.setOwnedPrimaryKey(key);
  }

  /**
   * Creates a foreign-key {@link sql.Column} named {@code keyName} in {@code owner} that
   * references {@code refTable}, and attaches an {@code ON DELETE CASCADE} event to it.
   * 
   * @param owner    the table that should receive the new foreign-key column
   * @param keyName  the column name (typically the referenced table's name)
   * @param refTable the table that this foreign key points to
   * @return the newly created {@link sql.Column}
   */
  public Column createForeignKeyAttr(final Table owner, final String keyName, final Table refTable) {
    Column _createColumn = this.targetFactory.createColumn();
    final Procedure1<Column> _function = (Column it) -> {
      it.setName(keyName);
      it.setType("int");
    };
    final Column col = ObjectExtensions.<Column>operator_doubleArrow(_createColumn, _function);
    EList<Column> _ownedColumns = owner.getOwnedColumns();
    _ownedColumns.add(col);
    this.createForeignKey(col, refTable);
    return col;
  }

  /**
   * Wraps a {@link sql.Column} with a {@link sql.ForeignKey} pointing to {@code refTable}
   * and adds an {@code ON DELETE CASCADE} event to the foreign key.
   * 
   * @param owner    the column to wrap as a foreign key
   * @param refTable the referenced table
   * @return the newly created {@link sql.ForeignKey}
   */
  public ForeignKey createForeignKey(final Column owner, final Table refTable) {
    ForeignKey _createForeignKey = this.targetFactory.createForeignKey();
    final Procedure1<ForeignKey> _function = (ForeignKey it) -> {
      it.setColumn(owner);
      it.setReferencedTable(refTable);
    };
    final ForeignKey key = ObjectExtensions.<ForeignKey>operator_doubleArrow(_createForeignKey, _function);
    EList<ForeignKey> _ownedForeignKeys = owner.getOwningTable().getOwnedForeignKeys();
    _ownedForeignKeys.add(key);
    Event _createEvent = this.targetFactory.createEvent();
    final Procedure1<Event> _function_1 = (Event it) -> {
      it.setCondition(Condition.DELETE);
      it.setAction(Action.CASCADE);
    };
    final Event del = ObjectExtensions.<Event>operator_doubleArrow(_createEvent, _function_1);
    EList<Event> _ownedEvents = key.getOwnedEvents();
    _ownedEvents.add(del);
    return key;
  }

  /**
   * Returns the annotation string that describes whether the given EClass is abstract.
   * 
   * @param clazz an Ecore {@link EClass}
   * @return {@code "abstract"} if {@code clazz.isAbstract}, otherwise {@code "concrete"}
   */
  public String kind(final EClass clazz) {
    String _xifexpression = null;
    boolean _isAbstract = clazz.isAbstract();
    if (_isAbstract) {
      _xifexpression = "abstract";
    } else {
      _xifexpression = "concrete";
    }
    return _xifexpression;
  }

  /**
   * Finds the special {@code "EObject"} sentinel table inside {@code schema}.
   * 
   * @param schema the SQL schema to search
   * @return the {@link Table} named {@code "EObject"}, or {@code null} if absent
   */
  public Table eObjectTable(final Schema schema) {
    final Function1<Table, Boolean> _function = (Table t) -> {
      return Boolean.valueOf(t.getName().equals("EObject"));
    };
    return IterableExtensions.<Table>findFirst(schema.getOwnedTables(), _function);
  }

  /**
   * Returns the existing column named {@code colName} in {@code owner}, or creates a new
   * {@code colName colType NOT NULL} column and adds it to the table.
   * 
   * @param owner   the table
   * @param colName the desired column name
   * @param colType the SQL type (e.g. {@code "int"}, {@code "varchar(255)"})
   * @return the existing or newly created {@link sql.Column}
   */
  public Column createColumn(final Table owner, final String colName, final String colType) {
    Column _xifexpression = null;
    final Function1<Column, Boolean> _function = (Column it) -> {
      String _name = it.getName();
      return Boolean.valueOf(Objects.equals(_name, colName));
    };
    boolean _exists = IterableExtensions.<Column>exists(owner.getOwnedColumns(), _function);
    if (_exists) {
      final Function1<Column, Boolean> _function_1 = (Column it) -> {
        String _name = it.getName();
        return Boolean.valueOf(Objects.equals(_name, colName));
      };
      _xifexpression = IterableExtensions.<Column>findFirst(owner.getOwnedColumns(), _function_1);
    } else {
      _xifexpression = this.targetFactory.createColumn();
    }
    final Procedure1<Column> _function_2 = (Column it) -> {
      it.setName(colName);
      it.setType(colType);
      EList<Property> _properties = it.getProperties();
      _properties.add(Property.NOT_NULL);
    };
    final Column col = ObjectExtensions.<Column>operator_doubleArrow(_xifexpression, _function_2);
    EList<Column> _ownedColumns = owner.getOwnedColumns();
    _ownedColumns.add(col);
    return col;
  }
}
