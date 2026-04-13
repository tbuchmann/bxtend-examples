/**
 */
package de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Multi Elem</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem#getSourceElements <em>Source Elements</em>}</li>
 * </ul>
 *
 * @see de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package#getMultiElem()
 * @model
 * @generated
 */
public interface MultiElem extends Corr {
	/**
	 * Returns the value of the '<em><b>Source Elements</b></em>' reference list.
	 * The list contents are of type {@link org.eclipse.emf.ecore.EObject}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Source Elements</em>' reference list.
	 * @see de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package#getMultiElem_SourceElements()
	 * @model
	 * @generated
	 */
	EList<EObject> getSourceElements();

} // MultiElem
