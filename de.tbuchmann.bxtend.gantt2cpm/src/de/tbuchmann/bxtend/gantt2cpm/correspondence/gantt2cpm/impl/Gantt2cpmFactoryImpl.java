/**
 */
package de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.impl;

import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.*;

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
public class Gantt2cpmFactoryImpl extends EFactoryImpl implements Gantt2cpmFactory {
	/**
	 * Creates the default factory implementation.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public static Gantt2cpmFactory init() {
		try {
			Gantt2cpmFactory theGantt2cpmFactory = (Gantt2cpmFactory)EPackage.Registry.INSTANCE.getEFactory(Gantt2cpmPackage.eNS_URI);
			if (theGantt2cpmFactory != null) {
				return theGantt2cpmFactory;
			}
		}
		catch (Exception exception) {
			EcorePlugin.INSTANCE.log(exception);
		}
		return new Gantt2cpmFactoryImpl();
	}

	/**
	 * Creates an instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Gantt2cpmFactoryImpl() {
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
			case Gantt2cpmPackage.TRANSFORMATION: return createTransformation();
			case Gantt2cpmPackage.CORR: return createCorr();
			case Gantt2cpmPackage.BASIC_ELEM: return createBasicElem();
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
	public Gantt2cpmPackage getGantt2cpmPackage() {
		return (Gantt2cpmPackage)getEPackage();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @deprecated
	 * @generated
	 */
	@Deprecated
	public static Gantt2cpmPackage getPackage() {
		return Gantt2cpmPackage.eINSTANCE;
	}

} //Gantt2cpmFactoryImpl
