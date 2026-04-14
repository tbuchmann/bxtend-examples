package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Annotation;
import sql.Column;
import sql.ForeignKey;
import sql.NamedElement;
import sql.Property;
import sql.Schema;
import sql.Table;

/**
 * Bidirectional transformation rule that maps Ecore {@link EAttribute}s to SQL {@link Column}s
 * (single-valued attributes) or to separate SQL {@link Table}s (multi-valued attributes).
 * 
 * <p>This rule extends {@link Class2Table} to inherit the foreign-key and column helper
 * methods ({@link Class2Table#createForeignKeyAttr}, {@link Class2Table#createColumn}, etc.).</p>
 * 
 * <h3>Single-valued attributes ({@code upperBound == 1})</h3>
 * <p>A single-valued {@link EAttribute} is represented in SQL as a {@link Column} inside the
 * owner class's table.  The column type is derived from the Ecore primitive type via
 * {@link #columnType(EAttribute)}.  The column receives two annotations:</p>
 * <ul>
 *   <li>{@code "attribute"} – marks it as coming from an EAttribute (not an EReference).</li>
 *   <li>{@code "single"} – distinguishes single-valued attributes from multi-valued ones.</li>
 * </ul>
 * 
 * <h3>Multi-valued attributes ({@code upperBound != 1})</h3>
 * <p>A multi-valued {@link EAttribute} (upper bound −1 or > 1) is represented as a separate
 * {@link Table} named {@code <ClassName>_<attributeName>}.  The table has:</p>
 * <ul>
 *   <li>an {@code id INT NOT NULL} foreign-key column pointing to the owner class table, and</li>
 *   <li>a {@code value} column with the mapped SQL type.</li>
 * </ul>
 * <p>Annotations {@code "attribute"} and {@code "multi"} are stored on the table.</p>
 * 
 * <h3>Incremental behaviour</h3>
 * <p>When the multiplicity of an attribute changes between single and multi (e.g. as a result
 * of an incremental source update), the old SQL element (Column or Table) is deleted via
 * {@link EcoreUtil#delete} and the new element type is created from scratch using the same
 * correspondence object.  This avoids stale elements in the target model.</p>
 * 
 * <h3>Backward direction ({@link #targetToSource})</h3>
 * <p>Two passes reconstruct Ecore attributes from the SQL model:</p>
 * <ol>
 *   <li>Every {@link Column} annotated with {@code "attribute"} is mapped to a single-valued
 *       {@link EAttribute} in the class that owns the column's table.</li>
 *   <li>Every {@link Table} annotated with both {@code "attribute"} and {@code "multi"} is
 *       mapped to a multi-valued {@link EAttribute} ({@code upperBound = -1}).  The owner
 *       class is found by extracting the class-name prefix from the table name.</li>
 * </ol>
 * <p>If the correspondence already points to a source element of the wrong kind (e.g. a
 * previous {@link EAttribute} that has since changed type), that element is deleted first.</p>
 * 
 * <h3>Type mapping</h3>
 * <p>Primitive Ecore types are mapped to SQL types as follows:</p>
 * <table border="1">
 *   <tr><th>Ecore</th><th>SQL</th></tr>
 *   <tr><td>{@code EInt} / {@code ELong}</td><td>{@code int}</td></tr>
 *   <tr><td>{@code EBoolean}</td><td>{@code boolean}</td></tr>
 *   <tr><td>{@code EDate}</td><td>{@code date}</td></tr>
 *   <tr><td>{@code EString}</td><td>{@code varchar(30)}</td></tr>
 *   <tr><td>{@code EDouble}</td><td>{@code double}</td></tr>
 * </table>
 */
@SuppressWarnings("all")
public class Attribute2Attribute extends Class2Table {
  /**
   * Constructs the rule and registers it under the {@code "attribute2attribute"} rule identifier.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public Attribute2Attribute(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "attribute2attribute";
  }

  /**
   * Maps every {@link EAttribute} in the source model to a SQL {@link Column} (single-valued)
   * or to a SQL {@link Table} (multi-valued).
   * 
   * <p>The rule detects multiplicity changes between runs and deletes the old SQL element if
   * the multiplicity kind has changed before creating the new element.</p>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<EAttribute> _function = (EAttribute att) -> {
      final Corr corr = this.getOrCreateCorrModelElement(att, this.ruleID);
      int _upperBound = att.getUpperBound();
      boolean _equals = (_upperBound == 1);
      if (_equals) {
        EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getColumn());
        final NamedElement c = ((NamedElement) _orCreateTargetElem);
        Column col = null;
        if ((c instanceof Table)) {
          EcoreUtil.delete(c, true);
          EObject _orCreateTargetElem_1 = this.getOrCreateTargetElem(corr, this.targetPackage.getColumn());
          col = ((Column) _orCreateTargetElem_1);
        } else {
          col = ((Column) c);
        }
        col.setName(att.getName());
        this.addColumnType(col, att);
        EObject _targetElement = this.getCorrModelElem(att.getEContainingClass()).getTargetElement();
        final Table parentTable = ((Table) _targetElement);
        EList<Column> _ownedColumns = parentTable.getOwnedColumns();
        _ownedColumns.add(col);
        this.addAnnotations(col, Arrays.<String>asList("attribute", "single"));
      } else {
        EObject _orCreateTargetElem_2 = this.getOrCreateTargetElem(corr, this.targetPackage.getTable());
        final NamedElement c_1 = ((NamedElement) _orCreateTargetElem_2);
        Table tab = null;
        if ((c_1 instanceof Column)) {
          EcoreUtil.delete(c_1, true);
          EObject _orCreateTargetElem_3 = this.getOrCreateTargetElem(corr, this.targetPackage.getTable());
          tab = ((Table) _orCreateTargetElem_3);
        } else {
          tab = ((Table) c_1);
        }
        String _name = att.getEContainingClass().getName();
        String _plus = (_name + "_");
        String _name_1 = att.getName();
        String _plus_1 = (_plus + _name_1);
        tab.setName(_plus_1);
        EObject _targetElement_1 = this.getCorrModelElem(att.getEContainingClass().getEPackage()).getTargetElement();
        final Schema schema = ((Schema) _targetElement_1);
        EList<Table> _ownedTables = schema.getOwnedTables();
        _ownedTables.add(tab);
        final Function1<ForeignKey, Boolean> _function_1 = (ForeignKey it) -> {
          String _name_2 = it.getColumn().getName();
          return Boolean.valueOf(Objects.equals(_name_2, "id"));
        };
        boolean _exists = IterableExtensions.<ForeignKey>exists(tab.getOwnedForeignKeys(), _function_1);
        boolean _not = (!_exists);
        if (_not) {
          EObject _targetElement_2 = this.getCorrModelElem(att.getEContainingClass()).getTargetElement();
          EList<Property> _properties = this.createForeignKeyAttr(tab, "id", ((Table) _targetElement_2)).getProperties();
          _properties.add(Property.NOT_NULL);
        }
        this.createColumn(tab, "value", this.columnType(att));
        this.addAnnotations(tab, Arrays.<String>asList("attribute", "multi"));
      }
    };
    IteratorExtensions.<EAttribute>forEach(Iterators.<EAttribute>filter(this.sourceModel.getAllContents(), EAttribute.class), _function);
  }

  /**
   * Maps SQL {@link Column}s and {@link Table}s annotated with {@code "attribute"} back to
   * Ecore {@link EAttribute}s.
   * 
   * <p>Single-valued attributes are reconstructed from columns; multi-valued attributes are
   * reconstructed from tables carrying the {@code "multi"} annotation.</p>
   */
  @Override
  public void targetToSource() {
    final Function1<Column, Boolean> _function = (Column it) -> {
      final Function1<Annotation, Boolean> _function_1 = (Annotation it_1) -> {
        String _annotation = it_1.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "attribute"));
      };
      return Boolean.valueOf(IterableExtensions.<Annotation>exists(it.getOwnedAnnotations(), _function_1));
    };
    final Procedure1<Column> _function_1 = (Column col) -> {
      final Corr corr = this.getOrCreateCorrModelElement(col, this.ruleID);
      if (((corr.getSourceElement() != null) && (!(corr.getSourceElement() instanceof EAttribute)))) {
        EcoreUtil.delete(corr.getSourceElement(), true);
      }
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEAttribute());
      final EAttribute att = ((EAttribute) _orCreateSourceElem);
      att.setName(col.getName());
      att.setUpperBound(1);
      this.addAttributeType(att, col);
      EObject _sourceElement = this.getCorrModelElem(col.getOwningTable()).getSourceElement();
      EList<EStructuralFeature> _eStructuralFeatures = ((EClass) _sourceElement).getEStructuralFeatures();
      _eStructuralFeatures.add(att);
    };
    IteratorExtensions.<Column>forEach(IteratorExtensions.<Column>filter(Iterators.<Column>filter(this.targetModel.getAllContents(), Column.class), _function), _function_1);
    final Function1<Table, Boolean> _function_2 = (Table t) -> {
      return Boolean.valueOf((IterableExtensions.<Annotation>exists(t.getOwnedAnnotations(), ((Function1<Annotation, Boolean>) (Annotation a) -> {
        String _annotation = a.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "multi"));
      })) && IterableExtensions.<Annotation>exists(t.getOwnedAnnotations(), ((Function1<Annotation, Boolean>) (Annotation it) -> {
        String _annotation = it.getAnnotation();
        return Boolean.valueOf(Objects.equals(_annotation, "attribute"));
      }))));
    };
    final Procedure1<Table> _function_3 = (Table tab) -> {
      final Corr corr = this.getOrCreateCorrModelElement(tab, this.ruleID);
      if (((corr.getSourceElement() != null) && (!(corr.getSourceElement() instanceof EAttribute)))) {
        EcoreUtil.delete(corr.getSourceElement(), true);
      }
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEAttribute());
      final EAttribute attr = ((EAttribute) _orCreateSourceElem);
      attr.setName(tab.getName().split("_")[1]);
      final Function1<Column, Boolean> _function_4 = (Column c) -> {
        String _name = c.getName();
        return Boolean.valueOf(Objects.equals(_name, "value"));
      };
      this.addAttributeType(attr, IterableExtensions.<Column>findFirst(tab.getOwnedColumns(), _function_4));
      attr.setUpperBound((-1));
      final EClass parentEClass = this.findClassByName(tab.getName().split("_")[0]);
      EList<EStructuralFeature> _eStructuralFeatures = parentEClass.getEStructuralFeatures();
      _eStructuralFeatures.add(attr);
    };
    IteratorExtensions.<Table>forEach(IteratorExtensions.<Table>filter(Iterators.<Table>filter(this.targetModel.getAllContents(), Table.class), _function_2), _function_3);
  }

  /**
   * Returns the SQL column type string for the given {@link EAttribute}'s primitive type.
   * 
   * <p>Supported mappings: {@code EInt}/{@code ELong} → {@code "int"},
   * {@code EBoolean} → {@code "boolean"}, {@code EDate} → {@code "date"},
   * {@code EString} → {@code "varchar(30)"}, {@code EDouble} → {@code "double"}.</p>
   * 
   * @param a the Ecore attribute whose type should be mapped
   * @return the SQL type string, or {@code null} if unmapped
   */
  public String columnType(final EAttribute a) {
    EClassifier _eType = a.getEType();
    boolean _matched = false;
    EDataType _eInt = this.sourcePackage.getEInt();
    if (Objects.equals(_eType, _eInt)) {
      _matched=true;
      return "int";
    }
    if (!_matched) {
      EDataType _eBoolean = this.sourcePackage.getEBoolean();
      if (Objects.equals(_eType, _eBoolean)) {
        _matched=true;
        return "boolean";
      }
    }
    if (!_matched) {
      EDataType _eDate = this.sourcePackage.getEDate();
      if (Objects.equals(_eType, _eDate)) {
        _matched=true;
        return "date";
      }
    }
    if (!_matched) {
      EDataType _eString = this.sourcePackage.getEString();
      if (Objects.equals(_eType, _eString)) {
        _matched=true;
        return "varchar(30)";
      }
    }
    if (!_matched) {
      EDataType _eLong = this.sourcePackage.getELong();
      if (Objects.equals(_eType, _eLong)) {
        _matched=true;
        return "int";
      }
    }
    if (!_matched) {
      EDataType _eDouble = this.sourcePackage.getEDouble();
      if (Objects.equals(_eType, _eDouble)) {
        _matched=true;
        return "double";
      }
    }
    return null;
  }

  /**
   * Sets the SQL type of column {@code c} by delegating to {@link #columnType(EAttribute)}.
   * 
   * @param c the column whose type should be set
   * @param a the source attribute providing the type information
   */
  public void addColumnType(final Column c, final EAttribute a) {
    c.setType(this.columnType(a));
  }

  /**
   * Sets the Ecore type of attribute {@code a} from the SQL type of column {@code c} via
   * {@link #attributeType(Column)}.
   * 
   * @param a the attribute whose EType should be set
   * @param c the column providing the SQL type
   */
  public void addAttributeType(final EAttribute a, final Column c) {
    a.setEType(this.attributeType(c));
  }

  /**
   * Returns the Ecore primitive {@link org.eclipse.emf.ecore.EDataType EDataType} that
   * corresponds to the SQL type of the given column.
   * 
   * <p>Supported reverse mappings: {@code "int"} → {@code EInt},
   * {@code "boolean"} → {@code EBoolean}, {@code "date"} → {@code EDate},
   * {@code "varchar(30)"} → {@code EString}, {@code "double"} → {@code EDouble}.</p>
   * 
   * @param c the SQL column
   * @return the matching Ecore data-type, or {@code null} if unmapped
   */
  public EDataType attributeType(final Column c) {
    String _type = c.getType();
    if (_type != null) {
      switch (_type) {
        case "int":
          return this.sourcePackage.getEInt();
        case "boolean":
          return this.sourcePackage.getEBoolean();
        case "date":
          return this.sourcePackage.getEDate();
        case "varchar(30)":
          return this.sourcePackage.getEString();
        case "double":
          return this.sourcePackage.getEDouble();
      }
    }
    return null;
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
