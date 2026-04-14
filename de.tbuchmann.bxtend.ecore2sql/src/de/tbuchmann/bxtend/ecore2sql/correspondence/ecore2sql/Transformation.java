/**
 */
package de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql;

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
 *   <li>{@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation#getCorrespondences <em>Correspondences</em>}</li>
 * </ul>
 *
 * @see de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlPackage#getTransformation()
 * @model
 * @generated
 */
public interface Transformation extends EObject {
	/**
	 * Returns the value of the '<em><b>Correspondences</b></em>' containment reference list.
	 * The list contents are of type {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Correspondences</em>' containment reference list.
	 * @see de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlPackage#getTransformation_Correspondences()
	 * @model containment="true"
	 * @generated
	 */
	EList<Corr> getCorrespondences();

} // Transformation
