/**
 */
package de.tbuchmann.bxtend.f2p.correspondence.f2p;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see de.tbuchmann.bxtend.f2p.correspondence.f2p.F2pPackage
 * @generated
 */
public interface F2pFactory extends EFactory {
	/**
	 * The singleton instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	F2pFactory eINSTANCE = de.tbuchmann.bxtend.f2p.correspondence.f2p.impl.F2pFactoryImpl.init();

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
	F2pPackage getF2pPackage();

} //F2pFactory
