/**
 * Utility functions shared across the Families-to-Persons BXtend transformation rules.
 * 
 * <p>This class is declared as a BXtend extension class so that its static methods are
 * accessible as extension methods throughout the rule classes without requiring explicit
 * import of individual methods.
 * 
 * <p>All methods are purely functional (no side effects) and operate on EMF model
 * objects only.
 */
package de.tbuchmann.bxtend.f2p.rules.util;

import Families.Family;
import Families.FamilyMember;
import Families.FamilyRegister;
import Persons.Female;
import Persons.Male;
import Persons.Person;
import Persons.PersonRegister;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import java.util.ArrayList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;

@SuppressWarnings("all")
public class Utils {
  /**
   * Derives the full name of a {@link FamilyMember} in the form
   * {@code "<firstName> <familyName>"}.
   * 
   * <p>The family name is read from the containing {@link Family}.  If the member is
   * not yet attached to a family the family-name portion will be {@code null}.
   * 
   * @param member the FamilyMember whose full name is to be computed
   * @return the full name string, e.g. {@code "Tom Simpson"}
   */
  public static String getFullName(final FamilyMember member) {
    final String firstName = member.getName();
    Family _familyOfMember = Utils.getFamilyOfMember(member);
    String _name = null;
    if (_familyOfMember!=null) {
      _name=_familyOfMember.getName();
    }
    final String familyName = _name;
    return ((firstName + " ") + familyName);
  }

  /**
   * Returns the {@link Family} that directly contains {@code member}, regardless of
   * which role the member occupies (father, mother, son, or daughter).
   * 
   * <p>The method inspects all four inverse references and returns the first non-null
   * result, or {@code null} when the member is not contained in any family.
   * 
   * @param member the FamilyMember to look up
   * @return the containing Family, or {@code null}
   */
  public static Family getFamilyOfMember(final FamilyMember member) {
    Family _fatherInverse = member.getFatherInverse();
    boolean _tripleNotEquals = (_fatherInverse != null);
    if (_tripleNotEquals) {
      return member.getFatherInverse();
    }
    Family _motherInverse = member.getMotherInverse();
    boolean _tripleNotEquals_1 = (_motherInverse != null);
    if (_tripleNotEquals_1) {
      return member.getMotherInverse();
    }
    Family _sonsInverse = member.getSonsInverse();
    boolean _tripleNotEquals_2 = (_sonsInverse != null);
    if (_tripleNotEquals_2) {
      return member.getSonsInverse();
    }
    Family _daughtersInverse = member.getDaughtersInverse();
    boolean _tripleNotEquals_3 = (_daughtersInverse != null);
    if (_tripleNotEquals_3) {
      return member.getDaughtersInverse();
    }
    return null;
  }

  /**
   * Checks whether the source and target elements stored in a {@link Corr} correspondence
   * are still consistent with each other.
   * 
   * <p>Consistency requires that:
   * <ol>
   *   <li>The person's full name equals {@code "<familyName>, <firstName>"}.</li>
   *   <li>The gender of the {@link Person} ({@link Male}/{@link Female}) matches the role
   *       of the {@link FamilyMember} (father/son → Male; mother/daughter → Female).</li>
   * </ol>
   * The special case of a {@link FamilyRegister}–{@link PersonRegister} correspondence is
   * always considered consistent and returns {@code true} immediately.
   * 
   * @param corr the correspondence object to validate
   * @return {@code true} if source and target are mutually consistent
   */
  public static boolean matchFamilyMember2Person(final Corr corr) {
    if (((corr.getSourceElement() instanceof FamilyRegister) && (corr.getTargetElement() instanceof PersonRegister))) {
      return true;
    }
    EObject _sourceElement = corr.getSourceElement();
    final FamilyMember f = ((FamilyMember) _sourceElement);
    EObject _eContainer = f.eContainer();
    final Family family = ((Family) _eContainer);
    EObject _targetElement = corr.getTargetElement();
    final Person p = ((Person) _targetElement);
    String _name = p.getName();
    String _name_1 = family.getName();
    String _plus = (_name_1 + ", ");
    String _name_2 = f.getName();
    String _plus_1 = (_plus + _name_2);
    boolean _equals = _name.equals(_plus_1);
    boolean _not = (!_equals);
    if (_not) {
      return false;
    }
    final ArrayList<FamilyMember> males = CollectionLiterals.<FamilyMember>newArrayList();
    males.addAll(family.getSons());
    FamilyMember _father = family.getFather();
    boolean _tripleNotEquals = (_father != null);
    if (_tripleNotEquals) {
      males.add(family.getFather());
    }
    boolean _contains = males.contains(f);
    if (_contains) {
      return (p instanceof Male);
    }
    final ArrayList<FamilyMember> females = CollectionLiterals.<FamilyMember>newArrayList();
    females.addAll(family.getDaughters());
    FamilyMember _mother = family.getMother();
    boolean _tripleNotEquals_1 = (_mother != null);
    if (_tripleNotEquals_1) {
      females.add(family.getMother());
    }
    boolean _contains_1 = females.contains(f);
    if (_contains_1) {
      return (p instanceof Female);
    }
    return false;
  }
}
