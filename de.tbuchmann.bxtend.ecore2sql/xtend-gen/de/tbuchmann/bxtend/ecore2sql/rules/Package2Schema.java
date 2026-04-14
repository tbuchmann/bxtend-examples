package de.tbuchmann.bxtend.ecore2sql.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Column;
import sql.PrimaryKey;
import sql.Property;
import sql.Schema;
import sql.Table;

/**
 * Bidirectional transformation rule that maps an Ecore {@link EPackage} to a SQL {@link Schema}.
 * 
 * <h3>Forward direction ({@link #sourceToTarget})</h3>
 * <p>Each {@link EPackage} found in the source model is mapped to a {@link Schema} with the same
 * name.  In addition, a special sentinel table called {@code "EObject"} is created (once) inside
 * the schema.  This table acts as the root of the inheritance hierarchy in the SQL model: every
 * class table gets a foreign-key column in the {@code EObject} table that serves as a unique
 * object identifier across the entire schema, allowing the backward direction to reconstruct the
 * Ecore class hierarchy.</p>
 * 
 * <p>The {@code EObject} table has a single column {@code id INT NOT NULL AUTO_INCREMENT} that
 * serves as the primary key.  Every concrete and abstract class table links back to it via a
 * foreign key managed by the {@link Class2Table} rule.</p>
 * 
 * <p>The schema is annotated with {@code "package"} so that the backward rule can identify it
 * unambiguously among all SQL elements.</p>
 * 
 * <h3>Backward direction ({@link #targetToSource})</h3>
 * <p>Each {@link Schema} annotated in the correspondence model is mapped back to an
 * {@link EPackage}.  The package name, namespace prefix, and namespace URI are all set to the
 * schema name (a simplification that is sufficient for the benchmark scenarios).</p>
 * 
 * <h3>Correspondence</h3>
 * <p>The rule uses the correspondence tag {@code "root"} to identify the
 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr Corr} objects it owns.</p>
 */
@SuppressWarnings("all")
public class Package2Schema extends Elem2Elem {
  /**
   * Constructs the rule and registers it under the {@code "root"} rule identifier.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public Package2Schema(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "root";
  }

  /**
   * Maps every {@link EPackage} in the source model to a SQL {@link Schema}.
   * 
   * <p>Steps performed for each package:</p>
   * <ol>
   *   <li>Get or create the correspondence object tagged {@code "root"}.</li>
   *   <li>Get or create the target {@link Schema}; set its name.</li>
   *   <li>Annotate the schema with {@code "package"}.</li>
   *   <li>If no {@code EObject} sentinel table exists yet, create it with an
   *       {@code id INT NOT NULL AUTO_INCREMENT} primary-key column.</li>
   *   <li>Add the schema to the target model resource.</li>
   * </ol>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<EPackage> _function = (EPackage ep) -> {
      final Corr corr = this.getOrCreateCorrModelElement(ep, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getSchema());
      final Schema schema = ((Schema) _orCreateTargetElem);
      schema.setName(ep.getName());
      this.addAnnotations(schema, Arrays.<String>asList("package"));
      final Function1<Table, Boolean> _function_1 = (Table it) -> {
        String _name = it.getName();
        return Boolean.valueOf(Objects.equals(_name, "EObject"));
      };
      Table _findFirst = IterableExtensions.<Table>findFirst(schema.getOwnedTables(), _function_1);
      boolean _tripleEquals = (_findFirst == null);
      if (_tripleEquals) {
        Table _createTable = this.targetFactory.createTable();
        final Procedure1<Table> _function_2 = (Table it) -> {
          it.setName("EObject");
        };
        final Table tbl = ObjectExtensions.<Table>operator_doubleArrow(_createTable, _function_2);
        Column _createColumn = this.targetFactory.createColumn();
        final Procedure1<Column> _function_3 = (Column it) -> {
          it.setName("id");
          it.setType("int");
          EList<Property> _properties = it.getProperties();
          _properties.add(Property.NOT_NULL);
          EList<Property> _properties_1 = it.getProperties();
          _properties_1.add(Property.AUTO_INCREMENT);
        };
        final Column col = ObjectExtensions.<Column>operator_doubleArrow(_createColumn, _function_3);
        EList<Column> _ownedColumns = tbl.getOwnedColumns();
        _ownedColumns.add(col);
        final PrimaryKey key = this.targetFactory.createPrimaryKey();
        key.setColumn(col);
        tbl.setOwnedPrimaryKey(key);
        EList<Table> _ownedTables = schema.getOwnedTables();
        _ownedTables.add(tbl);
      }
      EList<EObject> _contents = this.targetModel.getContents();
      _contents.add(schema);
    };
    IteratorExtensions.<EPackage>forEach(Iterators.<EPackage>filter(this.sourceModel.getAllContents(), EPackage.class), _function);
  }

  /**
   * Maps every {@link Schema} in the target model back to an Ecore {@link EPackage}.
   * 
   * <p>For each schema the rule gets or creates the correspondence object and then
   * gets or creates the target {@link EPackage}, setting {@code name}, {@code nsPrefix},
   * and {@code nsURI} all to the schema name.  The package is then added to the source
   * model resource.</p>
   */
  @Override
  public void targetToSource() {
    final Procedure1<Schema> _function = (Schema sc) -> {
      final Corr corr = this.getOrCreateCorrModelElement(sc, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getEPackage());
      final Procedure1<EPackage> _function_1 = (EPackage it) -> {
        it.setName(sc.getName());
        it.setNsPrefix(sc.getName());
        it.setNsURI(sc.getName());
      };
      final EPackage ep = ObjectExtensions.<EPackage>operator_doubleArrow(((EPackage) _orCreateSourceElem), _function_1);
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(ep);
    };
    IteratorExtensions.<Schema>forEach(Iterators.<Schema>filter(this.targetModel.getAllContents(), Schema.class), _function);
  }
}
