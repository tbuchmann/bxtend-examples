package de.tbuchmann.bxtend.ecore2sql.rules

import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr
import java.util.Arrays
import java.util.List
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil
import sql.Action
import sql.Column
import sql.ForeignKey
import sql.Property
import sql.Schema
import sql.Table

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
class EReference2Relation extends Class2Table {
	
	/**
	 * The SQL name of the column or table to be created for the current reference.
	 * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
	 */
	var String targetName
	/**
	 * The table that the new foreign-key column should point to.
	 * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
	 */
	var Table refTable
	/**
	 * The table that should own the new foreign-key column.
	 * Set in {@link #sourceToTarget} before each call to {@link #getOrCreateTargetElem}.
	 */
	var Table owningTable
	
	/**
	 * Constructs the rule and registers it under the {@code "ereference2relation"} rule identifier.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "ereference2relation"
	}
	
	/**
	 * Maps every {@link EReference} in the source model to the appropriate SQL construct.
	 * See the class-level documentation for the four cases handled.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(EReference))
			.forEach[eref |
				var corr = eref.getOrCreateCorrModelElement(ruleID)
								
				val List<String> annotations = newArrayList
				//annotations += "reference"
				
				// containment refs
				if (eref.containment) {
					annotations += "containment"
					if (eref.EOpposite === null) {
						annotations += "unidirectional"
						// inverse FK column placed in the child table; name encodes the reference name
						targetName = eref.name + "_inverse"
					} else {
						// bidirectional containment: name encodes both ends
						targetName = eref.EOpposite.name + "_inverse_" + eref.name
						annotations += "bidirectional"
					}
					if (eref.upperBound == 1) annotations += "single"
					else annotations += "multi"
					
					// FK from child table → parent table
					refTable = eref.EContainingClass.corrModelElem.targetElement as Table
					owningTable = eref.EReferenceType.corrModelElem.targetElement as Table
					val col = corr.getOrCreateTargetElem(targetPackage.column) as Column 									
					
					col.addAnnotations(annotations)
					col.keys.get(0).addAnnotations(annotations)
				}
				else if (eref.upperBound == 1 && eref.EOpposite === null) {
					// single-valued unidirectional cross-reference: FK column in source table
					owningTable = eref.EContainingClass.corrModelElem.targetElement as Table
					refTable = eref.EReferenceType.corrModelElem.targetElement as Table
					targetName = eref.name
					val col = corr.getOrCreateTargetElem(targetPackage.column) as Column
					(col.keys.get(0) as ForeignKey).ownedEvents.get(0).action = Action.SET_NULL
					annotations += Arrays.asList("single", "unidirectional", "cross")
					col.addAnnotations(annotations)
					col.keys.get(0).addAnnotations(annotations)
				}
				else if (eref.EOpposite === null) {
					// multi-valued unidirectional cross-reference: separate relation table
					targetName = eref.EContainingClass.name + "_" + eref.name
					val tbl = corr.getOrCreateTargetElem(targetPackage.table) as Table
					tbl.name = targetName
					// add to schema
					val schema = eref.EContainingClass.EPackage.corrModelElem.targetElement as Schema
					schema.ownedTables += tbl
					tbl.createForeignKeyAttr("id", eref.EContainingClass.corrModelElem.targetElement as Table).properties += Property.NOT_NULL
					tbl.createForeignKeyAttr("reference", eref.EReferenceType.corrModelElem.targetElement as Table)
					tbl.addAnnotations(Arrays.asList("cross", "multi", "unidirectional"))
				}
				else if (!eref.EOpposite.containment) {
					// bidirectional cross-reference: single relation table, created by lexicographically smaller name
					val sourceName = eref.EContainingClass.name + "_" + eref.name
					val oppositeName = eref.EType.name + "_" + eref.EOpposite.name
					// check is necessary to prevent double creation of the reference table
					if (sourceName.compareTo(oppositeName) < 0) {
						if(eref.EOpposite.corrModelElem !== null) {
							corr.targetElement = eref.EOpposite.corrModelElem.targetElement
							EcoreUtil.delete(eref.EOpposite.corrModelElem, true)
						}
						val refTargetName = sourceName + "_inverse_" + oppositeName
						val tbl = corr.getOrCreateTargetElem(targetPackage.table) as Table
						tbl.name = refTargetName
						// add to schema
						val schema = eref.EContainingClass.EPackage.corrModelElem.targetElement as Schema
						schema.ownedTables += tbl
						if(!tbl.ownedForeignKeys.exists[column.name == "source"])
							tbl.createForeignKeyAttr("source", eref.EContainingClass.corrModelElem.targetElement as Table).properties += Property.NOT_NULL
						else {
							val fk = tbl.ownedForeignKeys.findFirst[column.name == "source"]
							fk.referencedTable = eref.EContainingClass.corrModelElem.targetElement as Table
							fk.column.properties += Property.NOT_NULL
						}
						if(!tbl.ownedForeignKeys.exists[column.name == "target"])
							tbl.createForeignKeyAttr("target", eref.EReferenceType.corrModelElem.targetElement as Table).properties += Property.NOT_NULL
						else {
							val fk = tbl.ownedForeignKeys.findFirst[column.name == "target"]
							fk.referencedTable = eref.EReferenceType.corrModelElem.targetElement as Table
							fk.column.properties += Property.NOT_NULL
						}
						annotations += Arrays.asList("cross", "bidirectional")
						if (eref.upperBound == 1) annotations += "forwardSingle"
						else annotations += "forwardMulti"
						if (eref.EOpposite.upperBound == 1) annotations += "backwardSingle"
						else annotations += "backwardMulti"
						tbl.addAnnotations(annotations)	
					} else {
						// The opposite end creates the table; delete our redundant correspondence
						EcoreUtil.delete(corr, true)
					}
						
				}
				refTable = null
				owningTable = null
				targetName = ""
			]
	}
	
	/**
	 * Reconstructs Ecore {@link EReference}s from SQL columns and tables annotated with
	 * {@code "cross"} or {@code "containment"}.
	 *
	 * <p>Two passes are performed: one for {@link Column}s (single-valued and containment),
	 * one for {@link Table}s (multi-valued and bidirectional cross-references).</p>
	 */
	override targetToSource() {
		//transform single-valued uni-directional and containment references (columns in tables)
		targetModel.allContents.filter(typeof(Column)).filter[ownedAnnotations.exists[annotation=="cross" || annotation == "containment"]]
			.forEach[col |
				val corr = col.getOrCreateCorrModelElement(ruleID)
				if(corr.sourceElement !== null && !(corr.sourceElement instanceof EReference)) {
					EcoreUtil.delete(corr.sourceElement, true);
				}
				val ref = corr.getOrCreateSourceElem(sourcePackage.EReference) as EReference
				val sourceClass = (col.eContainer as Table).corrModelElem.sourceElement as EClass
				val targetClass = (col.eContainer as Table).ownedForeignKeys.findFirst[column == col].referencedTable.corrModelElem.sourceElement as EClass
				
				ref.upperBound = if(col.ownedAnnotations.exists[annotation == "single"]) 1 else -1
				if(col.ownedAnnotations.exists[annotation == "containment"]) {
					ref.containment = true
					if(col.ownedAnnotations.exists[annotation == "bidirectional"]) {
						ref.name = col.name.split("_").get(2)
						var invRef = sourceClass.EReferences.findFirst[r | r.name == col.name.split("_").get(0)]
						if(invRef === null) {
							invRef = createSourceElement(sourcePackage.EReference) as EReference
						}
						invRef.name = col.name.split("_").get(0)
						invRef.EType = targetClass
						ref.EOpposite = invRef
						invRef.EOpposite = ref
						sourceClass.EStructuralFeatures += invRef
					} else {
						if(ref.EOpposite !== null) EcoreUtil.delete(ref.EOpposite, true);
						ref.name = col.name.split("_").get(0)
					}
					ref.EType = sourceClass
					targetClass.EStructuralFeatures += ref
				} else {
					ref.name = col.name
					ref.EType = targetClass
					sourceClass.EStructuralFeatures += ref
				}
			]
			
		// transform multi-valued references (represented by tables containing the respective annotation)
		targetModel.allContents.filter(typeof(Table)).filter[t | t.ownedAnnotations.exists[a | a.annotation == "cross" || a.annotation == "containment"]]
			.forEach[tab |
				val corr = tab.getOrCreateCorrModelElement(ruleID)
				if(corr.sourceElement !== null && !(corr.sourceElement instanceof EReference)) {
					EcoreUtil.delete(corr.sourceElement, true);
				}
				val ref = corr.getOrCreateSourceElem(sourcePackage.EReference) as EReference
				ref.upperBound = if(tab.ownedAnnotations.exists[annotation == "forwardSingle"]) 1 else -1
				if(tab.ownedAnnotations.exists[annotation == "unidirectional"]) {
					ref.name = tab.name.split("_").get(1)
					ref.EType = tab.ownedForeignKeys.findFirst[f | f.column.name == "reference"].referencedTable.corrModelElem.sourceElement as EClass
					val parentEClass = findClassByName(tab.name.split("_").get(0))
					parentEClass.EStructuralFeatures += ref
					if(ref.EOpposite !== null) {
						EcoreUtil.delete(ref.EOpposite, true)
						ref.EOpposite = null
					}
				} else {
					// bidirectional: reconstruct both ends from the composite table name
					ref.name = tab.name.split("_").get(1)
					val sourceEClass = findClassByName(tab.name.split("_").get(0))
					val targetEClass = findClassByName(tab.name.split("_").get(3))
					ref.EType = targetEClass

					var invRef = ref.EOpposite
					if(invRef === null) {
						invRef = createSourceElement(sourcePackage.EReference) as EReference
					}
					invRef.name = tab.name.split("_").get(4);
					invRef.upperBound = if(tab.ownedAnnotations.exists[annotation == "backwardSingle"]) 1 else -1
					invRef.EType = sourceEClass
					invRef.EOpposite = ref
					ref.EOpposite = invRef
					sourceEClass.EStructuralFeatures += ref
					targetEClass.EStructuralFeatures  += invRef
				}
			]
	}

	/**
	 * Reconciles concurrent edits: re-runs {@link #sourceToTarget()} (idempotent — handles
	 * updates and reshaping between Column/Table representations for existing references, and
	 * creates SQL elements for new source references), then absorbs any
	 * {@code "cross"}/{@code "containment"}-annotated {@link Column}/{@link Table} that still
	 * has no correspondence at all — a genuine target-side insertion — using the same logic as
	 * {@link #targetToSource()}.
	 */
	override void synch() {
		sourceToTarget()
		targetModel.allContents.filter(typeof(Column)).filter[ownedAnnotations.exists[annotation=="cross" || annotation == "containment"]]
			.filter[corrModelElem === null]
			.forEach[col |
				val corr = col.getOrCreateCorrModelElement(ruleID)
				val ref = corr.getOrCreateSourceElem(sourcePackage.EReference) as EReference
				val sourceClass = (col.eContainer as Table).corrModelElem.sourceElement as EClass
				val targetClass = (col.eContainer as Table).ownedForeignKeys.findFirst[column == col].referencedTable.corrModelElem.sourceElement as EClass

				ref.upperBound = if(col.ownedAnnotations.exists[annotation == "single"]) 1 else -1
				if(col.ownedAnnotations.exists[annotation == "containment"]) {
					ref.containment = true
					if(col.ownedAnnotations.exists[annotation == "bidirectional"]) {
						ref.name = col.name.split("_").get(2)
						var invRef = sourceClass.EReferences.findFirst[r | r.name == col.name.split("_").get(0)]
						if(invRef === null) {
							invRef = createSourceElement(sourcePackage.EReference) as EReference
						}
						invRef.name = col.name.split("_").get(0)
						invRef.EType = targetClass
						ref.EOpposite = invRef
						invRef.EOpposite = ref
						sourceClass.EStructuralFeatures += invRef
					} else {
						if(ref.EOpposite !== null) EcoreUtil.delete(ref.EOpposite, true);
						ref.name = col.name.split("_").get(0)
					}
					ref.EType = sourceClass
					targetClass.EStructuralFeatures += ref
				} else {
					ref.name = col.name
					ref.EType = targetClass
					sourceClass.EStructuralFeatures += ref
				}
			]
		targetModel.allContents.filter(typeof(Table)).filter[t | t.ownedAnnotations.exists[a | a.annotation == "cross" || a.annotation == "containment"]]
			.filter[corrModelElem === null]
			.forEach[tab |
				val corr = tab.getOrCreateCorrModelElement(ruleID)
				val ref = corr.getOrCreateSourceElem(sourcePackage.EReference) as EReference
				ref.upperBound = if(tab.ownedAnnotations.exists[annotation == "forwardSingle"]) 1 else -1
				if(tab.ownedAnnotations.exists[annotation == "unidirectional"]) {
					ref.name = tab.name.split("_").get(1)
					ref.EType = tab.ownedForeignKeys.findFirst[f | f.column.name == "reference"].referencedTable.corrModelElem.sourceElement as EClass
					val parentEClass = findClassByName(tab.name.split("_").get(0))
					parentEClass.EStructuralFeatures += ref
					if(ref.EOpposite !== null) {
						EcoreUtil.delete(ref.EOpposite, true)
						ref.EOpposite = null
					}
				} else {
					// bidirectional: reconstruct both ends from the composite table name
					ref.name = tab.name.split("_").get(1)
					val sourceEClass = findClassByName(tab.name.split("_").get(0))
					val targetEClass = findClassByName(tab.name.split("_").get(3))
					ref.EType = targetEClass

					var invRef = ref.EOpposite
					if(invRef === null) {
						invRef = createSourceElement(sourcePackage.EReference) as EReference
					}
					invRef.name = tab.name.split("_").get(4);
					invRef.upperBound = if(tab.ownedAnnotations.exists[annotation == "backwardSingle"]) 1 else -1
					invRef.EType = sourceEClass
					invRef.EOpposite = ref
					ref.EOpposite = invRef
					sourceEClass.EStructuralFeatures += ref
					targetEClass.EStructuralFeatures  += invRef
				}
			]
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
	override getOrCreateTargetElem(Corr corr, EClass clazz) {
		var EObject target = corr.targetElement
		if (clazz == targetPackage.column) {			
			// check, if targetElement exists and is of the same type, if not, delete it and create a new column instead			
			if (target !== null && !(target instanceof Column)) {
				EcoreUtil.delete(target, true)
				target = owningTable.createForeignKeyAttr(targetName, refTable)
				corr.targetElement = target				
			}
			else if (target === null) {
				target = owningTable.createForeignKeyAttr(targetName, refTable)
				corr.targetElement = target			
			} else {
				// update existing column in-place
				(target as Column).name = targetName;
				(target as Column).type = "int";
				((target as Column).keys.get(0) as ForeignKey).referencedTable = refTable
			}
		}
		else if (clazz == targetPackage.table) {
			if (target !== null && !(target instanceof Table)) {
				EcoreUtil.delete(target, true)
				target = targetFactory.createTable								
				corr.targetElement = target
			}
			else if (target === null) {
				target = targetFactory.createTable
				corr.targetElement = target
			}
		}	
		
		return target
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