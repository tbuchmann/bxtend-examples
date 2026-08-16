/**
 */
package de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.impl;

import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Package;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem;

import java.util.Collection;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import org.eclipse.emf.ecore.util.EObjectResolvingEList;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Multi Elem</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.impl.MultiElemImpl#getSourceElements <em>Source Elements</em>}</li>
 * </ul>
 *
 * @generated
 */
public class MultiElemImpl extends CorrImpl implements MultiElem {
	/**
	 * The cached value of the '{@link #getSourceElements() <em>Source Elements</em>}' reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSourceElements()
	 * @generated
	 * @ordered
	 */
	protected EList<EObject> sourceElements;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected MultiElemImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return Bag12bag2Package.Literals.MULTI_ELEM;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<EObject> getSourceElements() {
		if (sourceElements == null) {
			// A source Element only ever belongs to one MultiElem group by construction
			// (Element2Element.addToTargetElem removes it from its old group before adding
			// it to a new one), so the uniqueness check EObjectResolvingEList performs on
			// every add() (an O(n) contains() scan against the whole list) is redundant here
			// and turns bulk group growth into O(n^2). NonUniqueEObjectResolvingEList
			// overrides isUnique() to false to skip that check while keeping every other
			// EObjectResolvingEList behaviour (proxy resolution, notifications) intact.
			sourceElements = new NonUniqueEObjectResolvingEList<EObject>(EObject.class, this, Bag12bag2Package.MULTI_ELEM__SOURCE_ELEMENTS);
		}
		return sourceElements;
	}

	/**
	 * {@link EObjectResolvingEList} variant that skips the O(n)-per-add()
	 * uniqueness check. See {@link #getSourceElements()} for why this is safe here.
	 */
	private static final class NonUniqueEObjectResolvingEList<E> extends EObjectResolvingEList<E> {
		private static final long serialVersionUID = 1L;

		NonUniqueEObjectResolvingEList(Class<?> dataClass, org.eclipse.emf.ecore.InternalEObject owner, int featureID) {
			super(dataClass, owner, featureID);
		}

		@Override
		public boolean isUnique() {
			return false;
		}
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case Bag12bag2Package.MULTI_ELEM__SOURCE_ELEMENTS:
				return getSourceElements();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case Bag12bag2Package.MULTI_ELEM__SOURCE_ELEMENTS:
				getSourceElements().clear();
				getSourceElements().addAll((Collection<? extends EObject>)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case Bag12bag2Package.MULTI_ELEM__SOURCE_ELEMENTS:
				getSourceElements().clear();
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case Bag12bag2Package.MULTI_ELEM__SOURCE_ELEMENTS:
				return sourceElements != null && !sourceElements.isEmpty();
		}
		return super.eIsSet(featureID);
	}

} //MultiElemImpl
