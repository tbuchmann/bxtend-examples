package de.tbuchmann.bxtend.ecore2sql.rules

import sql.Property
import sql.Schema
import java.util.Arrays
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.resource.Resource

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
class Package2Schema extends Elem2Elem {
	
	/**
	 * Constructs the rule and registers it under the {@code "root"} rule identifier.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "root"
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
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(EPackage))
			.forEach[ep |
				val corr = ep.getOrCreateCorrModelElement(ruleID)
				val schema = corr.getOrCreateTargetElem(targetPackage.schema) as Schema
				schema.name = ep.name
				schema.addAnnotations(Arrays.asList("package"))
				
				// if EObject table does not exist yet, create it
				if(schema.ownedTables.findFirst[name == "EObject"] === null) {
					val tbl = targetFactory.createTable => [name = "EObject"]
					val col = targetFactory.createColumn => [name = "id"; 
						type = "int"; 
						properties += Property.NOT_NULL; 
						properties += Property.AUTO_INCREMENT
					]
					tbl.ownedColumns += col
					val key = targetFactory.createPrimaryKey
					key.column = col
					tbl.ownedPrimaryKey = key
					schema.ownedTables += tbl
				}
				
				targetModel.contents += schema
			]
	}
	
	/**
	 * Maps every {@link Schema} in the target model back to an Ecore {@link EPackage}.
	 *
	 * <p>For each schema the rule gets or creates the correspondence object and then
	 * gets or creates the target {@link EPackage}, setting {@code name}, {@code nsPrefix},
	 * and {@code nsURI} all to the schema name.  The package is then added to the source
	 * model resource.</p>
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Schema))
			.forEach[sc |
				val corr = sc.getOrCreateCorrModelElement(ruleID)
				val ep = corr.getOrCreateSourceElem(sourcePackage.EPackage) as EPackage => [
					name = sc.name
					nsPrefix = sc.name
					nsURI = sc.name
				]
				
				sourceModel.contents += ep
			]
	}

	/**
	 * Reconciles concurrent edits: re-runs {@link #sourceToTarget()} (idempotent, reasserts
	 * existing package/schema correspondences and creates schemas for new packages), then
	 * absorbs any {@link Schema} that still has no correspondence at all — a genuine
	 * target-side insertion — using the same logic as {@link #targetToSource()}.
	 */
	override void synch() {
		sourceToTarget()
		targetModel.allContents.filter(typeof(Schema)).filter[corrModelElem === null]
			.forEach[sc |
				val corr = sc.getOrCreateCorrModelElement(ruleID)
				val ep = corr.getOrCreateSourceElem(sourcePackage.EPackage) as EPackage => [
					name = sc.name
					nsPrefix = sc.name
					nsURI = sc.name
				]
				sourceModel.contents += ep
			]
	}

}