package de.tbuchmann.bxtend.ecore2sql.rules

import sql.Column
import sql.NamedElement
import sql.Schema
import sql.Table
import java.util.Arrays
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil

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
class Attribute2Attribute extends Class2Table {
	
	/**
	 * Constructs the rule and registers it under the {@code "attribute2attribute"} rule identifier.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "attribute2attribute"
	}
	
	/**
	 * Maps every {@link EAttribute} in the source model to a SQL {@link Column} (single-valued)
	 * or to a SQL {@link Table} (multi-valued).
	 *
	 * <p>The rule detects multiplicity changes between runs and deletes the old SQL element if
	 * the multiplicity kind has changed before creating the new element.</p>
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(EAttribute))
			.forEach[att |
				val corr = att.getOrCreateCorrModelElement(ruleID)
				
				if (att.upperBound == 1) {
					val c = corr.getOrCreateTargetElem(targetPackage.column) as NamedElement
					// wenn col vom Typ Table => inkrementelles Verhalten, EAttribut war vorher mehrwertig...
					// Table löschen, stattdessen eine Column erzeugen...
					var Column col = null
					if (c instanceof Table) { EcoreUtil.delete(c, true)
						col = corr.getOrCreateTargetElem(targetPackage.column) as Column						
					}
					else
						col = c as Column
					
					col.name = att.name
					col.addColumnType(att)

					val parentTable = att.EContainingClass.corrModelElem.targetElement as Table
					parentTable.ownedColumns += col
					col.addAnnotations(Arrays.asList("attribute", "single"))
					corrToName.put(corr, col.name)
				}
				else { // mehrwertiges Feature
					val c = corr.getOrCreateTargetElem(targetPackage.table) as NamedElement
					// wenn col vom Typ Column => inkrementelles Verhalten, EAttribut war vorher einwertig...
					// Column löschen, stattdessen eine Table erzeugen...
					var Table tab = null
					if (c instanceof Column) {
						EcoreUtil.delete(c, true)
						tab = corr.getOrCreateTargetElem(targetPackage.table) as Table
					}
					else 
						tab = c as Table
					
					tab.name = att.EContainingClass.name + "_" + att.name
					// add to schema
					val schema = att.EContainingClass.EPackage.corrModelElem.targetElement as Schema
					schema.ownedTables += tab
					if(!tab.ownedForeignKeys.exists[column.name == "id"])
						tab.createForeignKeyAttr("id", att.EContainingClass.corrModelElem.targetElement as Table).properties += sql.Property.NOT_NULL
					tab.createColumn("value", att.columnType)
					tab.addAnnotations(Arrays.asList("attribute", "multi"))
					corrToName.put(corr, tab.name)
				}

			]
	}
	
	/**
	 * Maps SQL {@link Column}s and {@link Table}s annotated with {@code "attribute"} back to
	 * Ecore {@link EAttribute}s.
	 *
	 * <p>Single-valued attributes are reconstructed from columns; multi-valued attributes are
	 * reconstructed from tables carrying the {@code "multi"} annotation.</p>
	 */
	override targetToSource() {
		// transform single-valued attributes first (represented by columns in tables)
		targetModel.allContents.filter(typeof(Column)).filter[ownedAnnotations.exists[annotation == "attribute"]]
			.forEach[col |
				val corr = col.getOrCreateCorrModelElement(ruleID)
				if(corr.sourceElement !== null && !(corr.sourceElement instanceof EAttribute)) {
					EcoreUtil.delete(corr.sourceElement, true);
				}
				val att = corr.getOrCreateSourceElem(sourcePackage.EAttribute) as EAttribute
				att.name = col.name
				att.upperBound = 1
				att.addAttributeType(col)
				(col.owningTable.corrModelElem.sourceElement as EClass).EStructuralFeatures += att
				corrToName.put(corr, att.name)
			]
		// transform multi-valued attributes (represented by tables containing the respective annotation
		targetModel.allContents.filter(typeof(Table)).filter[t | t.ownedAnnotations.exists[a | a.annotation == "multi"] && t.ownedAnnotations.exists[annotation == "attribute"]]
			.forEach[tab |
				val corr = tab.getOrCreateCorrModelElement(ruleID)
				if(corr.sourceElement !== null && !(corr.sourceElement instanceof EAttribute)) {
					EcoreUtil.delete(corr.sourceElement, true);
				}
				val attr = corr.getOrCreateSourceElem(sourcePackage.EAttribute) as EAttribute
				attr.name = tab.name.split("_").get(1)
				attr.addAttributeType(tab.ownedColumns.findFirst[c | c.name == "value"])
				attr.upperBound = -1
				// find parent Class by name
				val parentEClass = findClassByName(tab.name.split("_").get(0))
				parentEClass.EStructuralFeatures += attr
				corrToName.put(corr, attr.name)
			]
	}

	/**
	 * Reconciles concurrent edits to {@code EAttribute} ↔ {@code Column}/{@code Table} pairs.
	 *
	 * <p>Unlike most other rules in this project, this one does <em>not</em> simply re-run
	 * {@link #sourceToTarget()}: {@code EAttribute.name} and {@code Column.name}/{@code Table.name}
	 * (derived from the attribute name) can each be renamed independently on their own side, so
	 * blindly pushing the source's name forward would silently discard a target-side rename. For
	 * every existing correspondence the name is instead resolved against the last-known snapshot
	 * ({@link #corrToName}): if only the target changed, the target's name wins outright — per
	 * this benchmark's {@code SyncConflictPolicy.TARGET_WINS} — otherwise the source's (possibly
	 * changed) name is pushed forward. Multiplicity (single ↔ multi) changes remain purely
	 * source-driven, matching {@link #sourceToTarget()}'s existing type-swap behaviour. Any
	 * {@code "attribute"}-annotated {@link Column}/{@link Table} that still has no correspondence
	 * at all is absorbed as a genuine target-side insertion, mirroring {@link #targetToSource()}.
	 */
	override void synch() {
		sourceModel.allContents.filter(typeof(EAttribute)).forEach[att |
			val corr = att.getOrCreateCorrModelElement(ruleID)
			val lastName = corrToName.get(corr)

			if (att.upperBound == 1) {
				val c = corr.getOrCreateTargetElem(targetPackage.column) as NamedElement
				var Column col = null
				if (c instanceof Table) {
					// multiplicity changed multi → single: no target-side name to preserve
					EcoreUtil.delete(c, true)
					col = corr.getOrCreateTargetElem(targetPackage.column) as Column
					col.name = att.name
				} else {
					col = c as Column
					// lastName === null means this correspondence has never been synced
					// before (col.name may not even be set yet) - always push in that case,
					// never treat it as a target-side change to pull.
					val targetChanged = lastName !== null && lastName != col.name
					val sourceChanged = lastName === null || lastName != att.name
					if (targetChanged)
						att.name = col.name
					else if (sourceChanged)
						col.name = att.name
				}
				col.addColumnType(att)
				val parentTable = att.EContainingClass.corrModelElem.targetElement as Table
				parentTable.ownedColumns += col
				col.addAnnotations(Arrays.asList("attribute", "single"))
				corrToName.put(corr, col.name)
			} else { // mehrwertiges Feature
				val c = corr.getOrCreateTargetElem(targetPackage.table) as NamedElement
				var Table tab = null
				val desiredName = att.EContainingClass.name + "_" + att.name
				if (c instanceof Column) {
					// multiplicity changed single → multi: no target-side name to preserve
					EcoreUtil.delete(c, true)
					tab = corr.getOrCreateTargetElem(targetPackage.table) as Table
					tab.name = desiredName
				} else {
					tab = c as Table
					// lastName === null means this correspondence has never been synced
					// before (tab.name may not even be set yet) - always push in that case,
					// never treat it as a target-side change to pull.
					val targetChanged = lastName !== null && lastName != tab.name
					val sourceChanged = lastName === null || lastName != desiredName
					if (targetChanged) {
						// pull: derive the attribute's own name back from the table's current
						// name, keeping the "<ClassName>_" prefix convention intact
						val parts = tab.name.split("_", 2)
						if (parts.length == 2) att.name = parts.get(1)
					} else if (sourceChanged) {
						tab.name = desiredName
					}
				}
				// add to schema
				val schema = att.EContainingClass.EPackage.corrModelElem.targetElement as Schema
				schema.ownedTables += tab
				if(!tab.ownedForeignKeys.exists[column.name == "id"])
					tab.createForeignKeyAttr("id", att.EContainingClass.corrModelElem.targetElement as Table).properties += sql.Property.NOT_NULL
				tab.createColumn("value", att.columnType)
				tab.addAnnotations(Arrays.asList("attribute", "multi"))
				corrToName.put(corr, tab.name)
			}
		]

		targetModel.allContents.filter(typeof(Column)).filter[ownedAnnotations.exists[annotation == "attribute"]]
			.filter[corrModelElem === null]
			.forEach[col |
				val corr = col.getOrCreateCorrModelElement(ruleID)
				val att = corr.getOrCreateSourceElem(sourcePackage.EAttribute) as EAttribute
				att.name = col.name
				att.upperBound = 1
				att.addAttributeType(col)
				(col.owningTable.corrModelElem.sourceElement as EClass).EStructuralFeatures += att
				corrToName.put(corr, att.name)
			]
		targetModel.allContents.filter(typeof(Table)).filter[t | t.ownedAnnotations.exists[a | a.annotation == "multi"] && t.ownedAnnotations.exists[annotation == "attribute"]]
			.filter[corrModelElem === null]
			.forEach[tab |
				val corr = tab.getOrCreateCorrModelElement(ruleID)
				val attr = corr.getOrCreateSourceElem(sourcePackage.EAttribute) as EAttribute
				attr.name = tab.name.split("_").get(1)
				attr.addAttributeType(tab.ownedColumns.findFirst[c | c.name == "value"])
				attr.upperBound = -1
				val parentEClass = findClassByName(tab.name.split("_").get(0))
				parentEClass.EStructuralFeatures += attr
				corrToName.put(corr, attr.name)
			]
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
	def columnType(EAttribute a) {
		switch (a.EType) {
			case sourcePackage.EInt:
				return "int"			
			case sourcePackage.EBoolean:
				return "boolean"
			case sourcePackage.EDate:
				return "date"
			case sourcePackage.EString:
				return "varchar(30)"
			case sourcePackage.ELong:
				return "int"
			case sourcePackage.EDouble:
				return "double"
		}
	}
	
	/**
	 * Sets the SQL type of column {@code c} by delegating to {@link #columnType(EAttribute)}.
	 *
	 * @param c the column whose type should be set
	 * @param a the source attribute providing the type information
	 */
	def addColumnType(Column c, EAttribute a) {
		c.type = a.columnType
	}
	
	/**
	 * Sets the Ecore type of attribute {@code a} from the SQL type of column {@code c} via
	 * {@link #attributeType(Column)}.
	 *
	 * @param a the attribute whose EType should be set
	 * @param c the column providing the SQL type
	 */
	def addAttributeType(EAttribute a, Column c) {
		a.EType = c.attributeType		
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
	def attributeType(Column c) {
		switch (c.type) {
			case "int":
				return sourcePackage.EInt
			case "boolean":
				return sourcePackage.EBoolean
			case "date":
				return sourcePackage.EDate
			case "varchar(30)":
				return sourcePackage.EString
			case "double":
				return sourcePackage.EDouble
		}
	}
	
	/**
	 * Finds the first {@link EClass} in the source model with the given name.
	 *
	 * @param clzName the class name to look up
	 * @return the matching {@link EClass}, or {@code null} if not found
	 */
	def findClassByName(String clzName) {
		sourceModel.allContents.filter(typeof(EClass)).findFirst[c | c.name == clzName]
	}
	
}