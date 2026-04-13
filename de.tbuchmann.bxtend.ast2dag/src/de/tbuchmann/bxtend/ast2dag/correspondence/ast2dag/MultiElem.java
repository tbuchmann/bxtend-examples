/**
 */
package de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag;

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
 *   <li>{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem#getSourceElements <em>Source Elements</em>}</li>
 * </ul>
 *
 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagPackage#getMultiElem()
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
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagPackage#getMultiElem_SourceElements()
	 * @model
	 * @generated
	 */
	EList<EObject> getSourceElements();

} // MultiElem
