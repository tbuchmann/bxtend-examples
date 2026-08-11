package de.tbuchmann.bxtend.ecore2sql.rules

import sql.Action
import sql.Column
import sql.Condition
import sql.Property
import sql.Schema
import sql.Table
import java.util.Arrays
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.resource.Resource

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
class Class2Table extends Elem2Elem {
	/**
	 * Constructs the rule and registers it under the {@code "class2table"} rule identifier.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "class2table"
	}
	
	/**
	 * Creates (or updates) a SQL {@link Table} for every {@link EClass} in the source model.
	 * See class-level documentation for the detailed algorithm.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(EClass))
			.forEach[ec |
				val corr = ec.getOrCreateCorrModelElement(ruleID)
				val tbl = corr.getOrCreateTargetElem(targetPackage.table) as Table
				tbl.name = ec.name;
				val schema = (ec.EPackage.corrModelElem.targetElement as Schema)
				schema.ownedTables += tbl
				if (tbl.ownedPrimaryKey === null) tbl.createPrimaryKeyAttr
				if (tbl.ownedAnnotations.size == 0) tbl.addAnnotations(Arrays.asList("class", ec.kind))
				val key = tbl.referencingForeignKeys.findFirst[k | k.owningTable == schema.eObjectTable]
				if(key === null) {
					val col = schema.eObjectTable.createForeignKeyAttr(tbl.name, tbl)
					col.properties.clear
					col.properties += Property.UNIQUE
				} else {
					key.column.name = tbl.name
					key.column.properties += Property.UNIQUE
					key.referencedTable = tbl
				}
										
			]
	}
	
	/**
	 * Creates (or updates) an Ecore {@link EClass} for every {@link Table} annotated with
	 * {@code "class"} (excluding the {@code "EObject"} sentinel table).
	 * See class-level documentation for the detailed algorithm.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Table)).filter[t | t.name != "EObject"].filter[ownedAnnotations.exists[annotation == "class"]]
			.forEach[tbl |
				val corr = tbl.getOrCreateCorrModelElement(ruleID)
				val ec = corr.getOrCreateSourceElem(sourcePackage.EClass) as EClass
				ec.name = tbl.name
				ec.abstract = tbl.ownedAnnotations.exists[a | a.annotation == "abstract"]
				val ep = tbl.owningSchema.corrModelElem.sourceElement as EPackage
				ep.EClassifiers += ec
			]
	}

	/**
	 * Reconciles concurrent edits: re-runs {@link #sourceToTarget()} (idempotent, reasserts
	 * existing class/table correspondences and creates tables for new classes), then absorbs
	 * any {@code "class"}-annotated {@link Table} that still has no correspondence at all — a
	 * genuine target-side insertion — using the same logic as {@link #targetToSource()}.
	 */
	override void synch() {
		sourceToTarget()
		targetModel.allContents.filter(typeof(Table)).filter[t | t.name != "EObject"].filter[ownedAnnotations.exists[annotation == "class"]]
			.filter[corrModelElem === null]
			.forEach[tbl |
				val corr = tbl.getOrCreateCorrModelElement(ruleID)
				val ec = corr.getOrCreateSourceElem(sourcePackage.EClass) as EClass
				ec.name = tbl.name
				ec.abstract = tbl.ownedAnnotations.exists[a | a.annotation == "abstract"]
				val ep = tbl.owningSchema.corrModelElem.sourceElement as EPackage
				ep.EClassifiers += ec
			]
	}

	/**
	 * Creates an {@code id INT NOT NULL} {@link sql.Column} and an associated
	 * {@link sql.PrimaryKey} on the given table, representing the class-level primary key.
	 *
	 * @param owner the {@link Table} that should receive the primary key
	 */
	def createPrimaryKeyAttr(Table owner) {
		val col = targetFactory.createColumn => [name = "id"; type = "int";
			properties += Property.NOT_NULL;			
		]
		owner.ownedColumns += col
		val key = targetFactory.createPrimaryKey => [column = col]
		owner.ownedPrimaryKey = key
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
	def createForeignKeyAttr(Table owner, String keyName, Table refTable) {
		val col = targetFactory.createColumn => [name = keyName; type = "int";
			//properties += Property.NOT_NULL;	
		]
		owner.ownedColumns += col
		col.createForeignKey(refTable)
		return col
	}	
	
	/**
	 * Wraps a {@link sql.Column} with a {@link sql.ForeignKey} pointing to {@code refTable}
	 * and adds an {@code ON DELETE CASCADE} event to the foreign key.
	 *
	 * @param owner    the column to wrap as a foreign key
	 * @param refTable the referenced table
	 * @return the newly created {@link sql.ForeignKey}
	 */
	def createForeignKey(Column owner, Table refTable) {
		val key = targetFactory.createForeignKey => [column = owner; referencedTable = refTable]
		owner.owningTable.ownedForeignKeys += key
		val del = targetFactory.createEvent => [condition = Condition.DELETE;
			action = Action.CASCADE
		]
		key.ownedEvents += del
		
		return key
	}
	
	/**
	 * Returns the annotation string that describes whether the given EClass is abstract.
	 *
	 * @param clazz an Ecore {@link EClass}
	 * @return {@code "abstract"} if {@code clazz.isAbstract}, otherwise {@code "concrete"}
	 */
	def kind(EClass clazz) {
		if (clazz.isAbstract)
			"abstract"
		else
			"concrete"
	}
	
	/**
	 * Finds the special {@code "EObject"} sentinel table inside {@code schema}.
	 *
	 * @param schema the SQL schema to search
	 * @return the {@link Table} named {@code "EObject"}, or {@code null} if absent
	 */
	def eObjectTable(Schema schema) {
		schema.ownedTables.findFirst[t | t.name.equals("EObject")]
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
	def createColumn(Table owner, String colName, String colType) {
		val col = (if(owner.ownedColumns.exists[name == colName]) owner.ownedColumns.findFirst[name == colName] else targetFactory.createColumn) => [
			name = colName
			type = colType
			properties += sql.Property.NOT_NULL
		]
		
		owner.ownedColumns += col
		return col
	}
}