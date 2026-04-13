/**
 */
package de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package
 * @generated
 */
public interface Bag12bag2Factory extends EFactory {
	/**
	 * The singleton instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	Bag12bag2Factory eINSTANCE = de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.impl.Bag12bag2FactoryImpl.init();

	/**
	 * Returns a new object of class '<em>Transformation</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Transformation</em>'.
	 * @generated
	 */
	Transformation createTransformation();

	/**
	 * Returns a new object of class '<em>Corr</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Corr</em>'.
	 * @generated
	 */
	Corr createCorr();

	/**
	 * Returns a new object of class '<em>Multi Elem</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Multi Elem</em>'.
	 * @generated
	 */
	MultiElem createMultiElem();

	/**
	 * Returns a new object of class '<em>Basic Elem</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Basic Elem</em>'.
	 * @generated
	 */
	BasicElem createBasicElem();

	/**
	 * Returns the package supported by this factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the package supported by this factory.
	 * @generated
	 */
	Bag12bag2Package getBag12bag2Package();

} //Bag12bag2Factory
