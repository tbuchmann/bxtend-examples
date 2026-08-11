package de.tbuchmann.bxtend.ecore2sql.rules

import sql.Table
import java.util.Arrays
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil

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
class Generalization2Relation extends Class2Table {
	
	/**
	 * Constructs the rule and registers it under the {@code "generalization2relation"} rule identifier.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "generalization2relation"
	}
	
	/**
	 * Propagates Ecore generalisation edges to SQL foreign-key constraints.
	 * See class-level documentation for the detailed two-pass algorithm.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(EClass)).filter[cl | cl.ESuperTypes.size != 0]
			.forEach[ec |
				// create foreign key for superType table
				val targetTable = ec.corrModelElem.targetElement as Table
				val superTypeTable = ec.ESuperTypes.get(0).corrModelElem.targetElement as Table
				val root = targetTable.ownedForeignKeys.findFirst[ownedAnnotations.exists[annotation=="root"]]
				if(root !== null) EcoreUtil.delete(root, true)
				if(!targetTable.ownedForeignKeys.filter[ownedAnnotations.exists[annotation=="superType"]].exists[referencedTable == superTypeTable]) {
					val key = targetTable.ownedPrimaryKey.column.createForeignKey(superTypeTable)
					key.addAnnotations(Arrays.asList("superType"))
				}
			]
			
		sourceModel.allContents.filter(typeof(EClass)).filter[cl | cl.ESuperTypes.size == 0]
			.forEach[ec |
				// create foreign key for superType table EObject
				val targetTable = ec.corrModelElem.targetElement as Table
				val superTypeTable = targetTable.owningSchema.eObjectTable as Table
				val superTable = targetTable.ownedForeignKeys.findFirst[ownedAnnotations.exists[annotation=="superType"]]
				if(superTable !== null) EcoreUtil.delete(superTable, true)
				if(!targetTable.ownedForeignKeys.filter[ownedAnnotations.exists[annotation=="root"]].exists[referencedTable == superTypeTable]) {
					val key = targetTable.ownedPrimaryKey.column.createForeignKey(superTypeTable)
					key.addAnnotations(Arrays.asList("root"))
				}
			]
	}
	
	/**
	 * Reconstructs Ecore generalisation edges from SQL {@code "superType"} foreign keys.
	 * See class-level documentation for the detailed algorithm.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Table))
			.forEach[tbl |
				val foreignKey = tbl.ownedForeignKeys.findFirst[fk | fk.ownedAnnotations.exists[a | a.annotation == "superType"]]
				if (foreignKey !== null) {
					val sourceClass = tbl.corrModelElem.sourceElement as EClass
					val sourceSuperClass = foreignKey.referencedTable.corrModelElem.sourceElement as EClass
					sourceClass.ESuperTypes += sourceSuperClass
				}
				val rootKey = tbl.ownedForeignKeys.findFirst[fk | fk.ownedAnnotations.exists[a | a.annotation == "root"]]
				if (rootKey !== null) {
					val sourceClass = tbl.corrModelElem.sourceElement as EClass
					sourceClass.ESuperTypes.clear()
				}
			]
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
	override void synch() {
		sourceToTarget()
	}

}