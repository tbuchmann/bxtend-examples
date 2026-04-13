/**
 */
package de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.impl;

import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Factory;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Corr;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Transformation;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;

import org.eclipse.emf.ecore.impl.EPackageImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model <b>Package</b>.
 * <!-- end-user-doc -->
 * @generated
 */
public class Bag12bag2PackageImpl extends EPackageImpl implements Bag12bag2Package {
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass transformationEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass corrEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass multiElemEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass basicElemEClass = null;

	/**
	 * Creates an instance of the model <b>Package</b>, registered with
	 * {@link org.eclipse.emf.ecore.EPackage.Registry EPackage.Registry} by the package
	 * package URI value.
	 * <p>Note: the correct way to create the package is via the static
	 * factory method {@link #init init()}, which also performs
	 * initialization of the package, or returns the registered package,
	 * if one already exists.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see org.eclipse.emf.ecore.EPackage.Registry
	 * @see de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package#eNS_URI
	 * @see #init()
	 * @generated
	 */
	private Bag12bag2PackageImpl() {
		super(eNS_URI, Bag12bag2Factory.eINSTANCE);
	}
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private static boolean isInited = false;

	/**
	 * Creates, registers, and initializes the <b>Package</b> for this model, and for any others upon which it depends.
	 *
	 * <p>This method is used to initialize {@link Bag12bag2Package#eINSTANCE} when that field is accessed.
	 * Clients should not invoke it directly. Instead, they should simply access that field to obtain the package.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #eNS_URI
	 * @see #createPackageContents()
	 * @see #initializePackageContents()
	 * @generated
	 */
	public static Bag12bag2Package init() {
		if (isInited) return (Bag12bag2Package)EPackage.Registry.INSTANCE.getEPackage(Bag12bag2Package.eNS_URI);

		// Obtain or create and register package
		Object registeredBag12bag2Package = EPackage.Registry.INSTANCE.get(eNS_URI);
		Bag12bag2PackageImpl theBag12bag2Package = registeredBag12bag2Package instanceof Bag12bag2PackageImpl ? (Bag12bag2PackageImpl)registeredBag12bag2Package : new Bag12bag2PackageImpl();

		isInited = true;

		// Create package meta-data objects
		theBag12bag2Package.createPackageContents();

		// Initialize created meta-data
		theBag12bag2Package.initializePackageContents();

		// Mark meta-data to indicate it can't be changed
		theBag12bag2Package.freeze();

		// Update the registry and return the package
		EPackage.Registry.INSTANCE.put(Bag12bag2Package.eNS_URI, theBag12bag2Package);
		return theBag12bag2Package;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getTransformation() {
		return transformationEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getTransformation_Correspondences() {
		return (EReference)transformationEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getCorr() {
		return corrEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getCorr_SourceElement() {
		return (EReference)corrEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getCorr_TargetElement() {
		return (EReference)corrEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getCorr_Desc() {
		return (EAttribute)corrEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getMultiElem() {
		return multiElemEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getMultiElem_SourceElements() {
		return (EReference)multiElemEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getBasicElem() {
		return basicElemEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Bag12bag2Factory getBag12bag2Factory() {
		return (Bag12bag2Factory)getEFactoryInstance();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private boolean isCreated = false;

	/**
	 * Creates the meta-model objects for the package.  This method is
	 * guarded to have no affect on any invocation but its first.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void createPackageContents() {
		if (isCreated) return;
		isCreated = true;

		// Create classes and their features
		transformationEClass = createEClass(TRANSFORMATION);
		createEReference(transformationEClass, TRANSFORMATION__CORRESPONDENCES);

		corrEClass = createEClass(CORR);
		createEReference(corrEClass, CORR__SOURCE_ELEMENT);
		createEReference(corrEClass, CORR__TARGET_ELEMENT);
		createEAttribute(corrEClass, CORR__DESC);

		multiElemEClass = createEClass(MULTI_ELEM);
		createEReference(multiElemEClass, MULTI_ELEM__SOURCE_ELEMENTS);

		basicElemEClass = createEClass(BASIC_ELEM);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private boolean isInitialized = false;

	/**
	 * Complete the initialization of the package and its meta-model.  This
	 * method is guarded to have no affect on any invocation but its first.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void initializePackageContents() {
		if (isInitialized) return;
		isInitialized = true;

		// Initialize package
		setName(eNAME);
		setNsPrefix(eNS_PREFIX);
		setNsURI(eNS_URI);

		// Create type parameters

		// Set bounds for type parameters

		// Add supertypes to classes
		multiElemEClass.getESuperTypes().add(this.getCorr());
		basicElemEClass.getESuperTypes().add(this.getCorr());

		// Initialize classes and features; add operations and parameters
		initEClass(transformationEClass, Transformation.class, "Transformation", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getTransformation_Correspondences(), this.getCorr(), null, "correspondences", null, 0, -1, Transformation.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(corrEClass, Corr.class, "Corr", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getCorr_SourceElement(), ecorePackage.getEObject(), null, "sourceElement", null, 0, 1, Corr.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getCorr_TargetElement(), ecorePackage.getEObject(), null, "targetElement", null, 0, 1, Corr.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getCorr_Desc(), ecorePackage.getEString(), "desc", null, 0, 1, Corr.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(multiElemEClass, MultiElem.class, "MultiElem", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getMultiElem_SourceElements(), ecorePackage.getEObject(), null, "sourceElements", null, 0, -1, MultiElem.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(basicElemEClass, BasicElem.class, "BasicElem", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);

		// Create resource
		createResource(eNS_URI);
	}

} //Bag12bag2PackageImpl
