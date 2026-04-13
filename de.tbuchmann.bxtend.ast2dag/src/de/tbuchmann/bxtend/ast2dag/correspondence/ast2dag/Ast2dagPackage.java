/**
 */
package de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

/**
 * <!-- begin-user-doc -->
 * The <b>Package</b> for the model.
 * It contains accessors for the meta objects to represent
 * <ul>
 *   <li>each class,</li>
 *   <li>each feature of each class,</li>
 *   <li>each enum,</li>
 *   <li>and each data type</li>
 * </ul>
 * <!-- end-user-doc -->
 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagFactory
 * @model kind="package"
 * @generated
 */
public interface Ast2dagPackage extends EPackage {
	/**
	 * The package name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNAME = "ast2dag";

	/**
	 * The package namespace URI.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNS_URI = "http://de.ubt.ai1.m2m.ast2dag/correspondence.ecore";

	/**
	 * The package namespace name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNS_PREFIX = "de.ubt.ai1.m2m.ast2dag.correspondence";

	/**
	 * The singleton instance of the package.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	Ast2dagPackage eINSTANCE = de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl.init();

	/**
	 * The meta object id for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.TransformationImpl <em>Transformation</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.TransformationImpl
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getTransformation()
	 * @generated
	 */
	int TRANSFORMATION = 0;

	/**
	 * The feature id for the '<em><b>Correspondences</b></em>' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int TRANSFORMATION__CORRESPONDENCES = 0;

	/**
	 * The number of structural features of the '<em>Transformation</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int TRANSFORMATION_FEATURE_COUNT = 1;

	/**
	 * The meta object id for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.CorrImpl <em>Corr</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.CorrImpl
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getCorr()
	 * @generated
	 */
	int CORR = 1;

	/**
	 * The feature id for the '<em><b>Source Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CORR__SOURCE_ELEMENT = 0;

	/**
	 * The feature id for the '<em><b>Target Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CORR__TARGET_ELEMENT = 1;

	/**
	 * The feature id for the '<em><b>Desc</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CORR__DESC = 2;

	/**
	 * The number of structural features of the '<em>Corr</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CORR_FEATURE_COUNT = 3;

	/**
	 * The meta object id for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.BasicElemImpl <em>Basic Elem</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.BasicElemImpl
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getBasicElem()
	 * @generated
	 */
	int BASIC_ELEM = 2;

	/**
	 * The feature id for the '<em><b>Source Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int BASIC_ELEM__SOURCE_ELEMENT = CORR__SOURCE_ELEMENT;

	/**
	 * The feature id for the '<em><b>Target Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int BASIC_ELEM__TARGET_ELEMENT = CORR__TARGET_ELEMENT;

	/**
	 * The feature id for the '<em><b>Desc</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int BASIC_ELEM__DESC = CORR__DESC;

	/**
	 * The number of structural features of the '<em>Basic Elem</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int BASIC_ELEM_FEATURE_COUNT = CORR_FEATURE_COUNT + 0;


	/**
	 * The meta object id for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.MultiElemImpl <em>Multi Elem</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.MultiElemImpl
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getMultiElem()
	 * @generated
	 */
	int MULTI_ELEM = 3;

	/**
	 * The feature id for the '<em><b>Source Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int MULTI_ELEM__SOURCE_ELEMENT = CORR__SOURCE_ELEMENT;

	/**
	 * The feature id for the '<em><b>Target Element</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int MULTI_ELEM__TARGET_ELEMENT = CORR__TARGET_ELEMENT;

	/**
	 * The feature id for the '<em><b>Desc</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int MULTI_ELEM__DESC = CORR__DESC;

	/**
	 * The feature id for the '<em><b>Source Elements</b></em>' reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int MULTI_ELEM__SOURCE_ELEMENTS = CORR_FEATURE_COUNT + 0;

	/**
	 * The number of structural features of the '<em>Multi Elem</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int MULTI_ELEM_FEATURE_COUNT = CORR_FEATURE_COUNT + 1;


	/**
	 * Returns the meta object for class '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation <em>Transformation</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Transformation</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation
	 * @generated
	 */
	EClass getTransformation();

	/**
	 * Returns the meta object for the containment reference list '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation#getCorrespondences <em>Correspondences</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the containment reference list '<em>Correspondences</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation#getCorrespondences()
	 * @see #getTransformation()
	 * @generated
	 */
	EReference getTransformation_Correspondences();

	/**
	 * Returns the meta object for class '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr <em>Corr</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Corr</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr
	 * @generated
	 */
	EClass getCorr();

	/**
	 * Returns the meta object for the reference '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getSourceElement <em>Source Element</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Source Element</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getSourceElement()
	 * @see #getCorr()
	 * @generated
	 */
	EReference getCorr_SourceElement();

	/**
	 * Returns the meta object for the reference '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getTargetElement <em>Target Element</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Target Element</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getTargetElement()
	 * @see #getCorr()
	 * @generated
	 */
	EReference getCorr_TargetElement();

	/**
	 * Returns the meta object for the attribute '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getDesc <em>Desc</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Desc</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr#getDesc()
	 * @see #getCorr()
	 * @generated
	 */
	EAttribute getCorr_Desc();

	/**
	 * Returns the meta object for class '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem <em>Basic Elem</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Basic Elem</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem
	 * @generated
	 */
	EClass getBasicElem();

	/**
	 * Returns the meta object for class '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem <em>Multi Elem</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Multi Elem</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
	 * @generated
	 */
	EClass getMultiElem();

	/**
	 * Returns the meta object for the reference list '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem#getSourceElements <em>Source Elements</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference list '<em>Source Elements</em>'.
	 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem#getSourceElements()
	 * @see #getMultiElem()
	 * @generated
	 */
	EReference getMultiElem_SourceElements();

	/**
	 * Returns the factory that creates the instances of the model.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the factory that creates the instances of the model.
	 * @generated
	 */
	Ast2dagFactory getAst2dagFactory();

	/**
	 * <!-- begin-user-doc -->
	 * Defines literals for the meta objects that represent
	 * <ul>
	 *   <li>each class,</li>
	 *   <li>each feature of each class,</li>
	 *   <li>each enum,</li>
	 *   <li>and each data type</li>
	 * </ul>
	 * <!-- end-user-doc -->
	 * @generated
	 */
	interface Literals {
		/**
		 * The meta object literal for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.TransformationImpl <em>Transformation</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.TransformationImpl
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getTransformation()
		 * @generated
		 */
		EClass TRANSFORMATION = eINSTANCE.getTransformation();

		/**
		 * The meta object literal for the '<em><b>Correspondences</b></em>' containment reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference TRANSFORMATION__CORRESPONDENCES = eINSTANCE.getTransformation_Correspondences();

		/**
		 * The meta object literal for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.CorrImpl <em>Corr</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.CorrImpl
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getCorr()
		 * @generated
		 */
		EClass CORR = eINSTANCE.getCorr();

		/**
		 * The meta object literal for the '<em><b>Source Element</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference CORR__SOURCE_ELEMENT = eINSTANCE.getCorr_SourceElement();

		/**
		 * The meta object literal for the '<em><b>Target Element</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference CORR__TARGET_ELEMENT = eINSTANCE.getCorr_TargetElement();

		/**
		 * The meta object literal for the '<em><b>Desc</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute CORR__DESC = eINSTANCE.getCorr_Desc();

		/**
		 * The meta object literal for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.BasicElemImpl <em>Basic Elem</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.BasicElemImpl
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getBasicElem()
		 * @generated
		 */
		EClass BASIC_ELEM = eINSTANCE.getBasicElem();

		/**
		 * The meta object literal for the '{@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.MultiElemImpl <em>Multi Elem</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.MultiElemImpl
		 * @see de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.impl.Ast2dagPackageImpl#getMultiElem()
		 * @generated
		 */
		EClass MULTI_ELEM = eINSTANCE.getMultiElem();

		/**
		 * The meta object literal for the '<em><b>Source Elements</b></em>' reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference MULTI_ELEM__SOURCE_ELEMENTS = eINSTANCE.getMultiElem_SourceElements();

	}

} //Ast2dagPackage
