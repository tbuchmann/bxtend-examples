/**
 */
package de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Pn2pnwPackage
 * @generated
 */
public interface Pn2pnwFactory extends EFactory {
	/**
	 * The singleton instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	Pn2pnwFactory eINSTANCE = de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.impl.Pn2pnwFactoryImpl.init();

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
	Pn2pnwPackage getPn2pnwPackage();

} //Pn2pnwFactory
