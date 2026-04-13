/**
 */
package de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Transformation</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation#getCorrespondences <em>Correspondences</em>}</li>
 * </ul>
 *
 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagPackage#getTransformation()
 * @model
 * @generated
 */
public interface Transformation extends EObject {
	/**
	 * Returns the value of the '<em><b>Correspondences</b></em>' containment reference list.
	 * The list contents are of type {@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Correspondences</em>' containment reference list.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagPackage#getTransformation_Correspondences()
	 * @model containment="true"
	 * @generated
	 */
	EList<Corr> getCorrespondences();

} // Transformation
