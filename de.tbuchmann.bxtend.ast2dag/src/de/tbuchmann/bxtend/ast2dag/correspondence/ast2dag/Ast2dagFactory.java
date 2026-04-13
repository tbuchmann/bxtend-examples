/**
 */
package de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagPackage
 * @generated
 */
public interface Ast2dagFactory extends EFactory {
	/**
	 * The singleton instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	Ast2dagFactory eINSTANCE = de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagFactoryImpl.init();

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
	 * Returns a new object of class '<em>Multi Elem</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Multi Elem</em>'.
	 * @generated
	 */
	MultiElem createMultiElem();

	/**
	 * Returns the package supported by this factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the package supported by this factory.
	 * @generated
	 */
	Ast2dagPackage getAst2dagPackage();

} //Ast2dagFactory
