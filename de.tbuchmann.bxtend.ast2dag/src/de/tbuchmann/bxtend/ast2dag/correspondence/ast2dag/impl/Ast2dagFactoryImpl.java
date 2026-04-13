/**
 */
package de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl;

import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.*;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import org.eclipse.emf.ecore.impl.EFactoryImpl;

import org.eclipse.emf.ecore.plugin.EcorePlugin;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model <b>Factory</b>.
 * <!-- end-user-doc -->
 * @generated
 */
public class Ast2dagFactoryImpl extends EFactoryImpl implements Ast2dagFactory {
	/**
	 * Creates the default factory implementation.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public static Ast2dagFactory init() {
		try {
			Ast2dagFactory theAst2dagFactory = (Ast2dagFactory)EPackage.Registry.INSTANCE.getEFactory(Ast2dagPackage.eNS_URI);
			if (theAst2dagFactory != null) {
				return theAst2dagFactory;
			}
		}
		catch (Exception exception) {
			EcorePlugin.INSTANCE.log(exception);
		}
		return new Ast2dagFactoryImpl();
	}

	/**
	 * Creates an instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Ast2dagFactoryImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EObject create(EClass eClass) {
		switch (eClass.getClassifierID()) {
			case Ast2dagPackage.TRANSFORMATION: return createTransformation();
			case Ast2dagPackage.CORR: return createCorr();
			case Ast2dagPackage.BASIC_ELEM: return createBasicElem();
			case Ast2dagPackage.MULTI_ELEM: return createMultiElem();
			default:
				throw new IllegalArgumentException("The class '" + eClass.getName() + "' is not a valid classifier");
		}
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Transformation createTransformation() {
		TransformationImpl transformation = new TransformationImpl();
		return transformation;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Corr createCorr() {
		CorrImpl corr = new CorrImpl();
		return corr;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public BasicElem createBasicElem() {
		BasicElemImpl basicElem = new BasicElemImpl();
		return basicElem;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public MultiElem createMultiElem() {
		MultiElemImpl multiElem = new MultiElemImpl();
		return multiElem;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Ast2dagPackage getAst2dagPackage() {
		return (Ast2dagPackage)getEPackage();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @deprecated
	 * @generated
	 */
	@Deprecated
	public static Ast2dagPackage getPackage() {
		return Ast2dagPackage.eINSTANCE;
	}

} //Ast2dagFactoryImpl
