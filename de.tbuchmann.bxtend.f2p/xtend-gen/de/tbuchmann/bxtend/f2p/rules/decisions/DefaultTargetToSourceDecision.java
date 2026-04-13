/**
 * Default implementation of {@link TargetToSourceDecision} that uses deterministic,
 * rule-based behaviour for all backward-transformation decisions.
 * 
 * <p>Policy summary:
 * <ul>
 *   <li><b>Family selection:</b> always picks the first candidate family (if one exists).</li>
 *   <li><b>Parent vs. child:</b> assigns the member as parent (father/mother) when the
 *       parent slot of the chosen family is still free; otherwise adds as a child.</li>
 *   <li><b>Empty-family cleanup:</b> always deletes a family that has become empty.</li>
 *   <li><b>Candidate list size:</b> returns {@code 1} so the framework only passes in at
 *       most one candidate family.</li>
 * </ul>
 * 
 * <p>This implementation requires no user interaction and is therefore suitable for
 * automated test scenarios and batch transformations.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions;

import Families.Family;
import Families.FamilyMember;
import Persons.Male;
import Persons.Person;
import java.util.List;

@SuppressWarnings("all")
public class DefaultTargetToSourceDecision implements TargetToSourceDecision {
  /**
   * Returns the first family from {@code families} if the list is non-empty,
   * or {@code null} to request creation of a new family.
   * 
   * @param families    candidate families (at most one element due to
   *                    {@link #getFamilyListSize()} returning {@code 1})
   * @param person      the Person being transformed back (unused here)
   * @param actualFamily the Family currently containing the member (unused here)
   * @return {@code families.get(0)} or {@code null}
   */
  @Override
  public Family getFamily(final List<Family> families, final Person person, final Family actualFamily) {
    int _size = families.size();
    boolean _greaterThan = (_size > 0);
    if (_greaterThan) {
      return families.get(0);
    }
    return null;
  }

  /**
   * Returns {@code true} if the parent slot (father for {@link Male}, mother otherwise)
   * in {@code family} is still vacant.
   * 
   * @param person the Person being placed
   * @param family the target Family
   * @return {@code true} when the parent position is free
   */
  @Override
  public boolean setAsParent(final Person person, final Family family) {
    if ((person instanceof Male)) {
      FamilyMember _father = family.getFather();
      return (_father == null);
    } else {
      FamilyMember _mother = family.getMother();
      return (_mother == null);
    }
  }

  /**
   * Always returns {@code true}: empty families are always removed.
   * 
   * @param family     the empty Family (unused)
   * @param lastMember the last removed FamilyMember (unused)
   * @return {@code true}
   */
  @Override
  public boolean deleteEmptyFamily(final Family family, final FamilyMember lastMember) {
    return true;
  }

  /**
   * No-op initialisation; always signals that the transformation should proceed.
   * 
   * @return {@code true}
   */
  @Override
  public boolean init() {
    return true;
  }

  /**
   * No-op hook; this implementation maintains no additional bookkeeping.
   * 
   * @param person the newly placed Person (unused)
   * @param family the newly created Family (unused)
   */
  @Override
  public void linkPersonToFamily(final Person person, final Family family) {
  }

  /**
   * Signals that the framework should pass at most one candidate family to
   * {@link #getFamily}.
   * 
   * @return {@code 1}
   */
  @Override
  public int getFamilyListSize() {
    return 1;
  }
}
