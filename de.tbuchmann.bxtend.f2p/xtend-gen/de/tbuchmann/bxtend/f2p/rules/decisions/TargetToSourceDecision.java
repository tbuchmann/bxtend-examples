/**
 * Strategy interface for resolving ambiguous decisions during the target-to-source
 * (Persons → Families) backward transformation.
 * 
 * <p>When a {@link Persons.Person} is transformed back into the Families model, several
 * decisions cannot be derived from the data alone and require external policy input:
 * <ul>
 *   <li>Which existing {@link Families.Family} (if any) should the new member join?</li>
 *   <li>Should the member be assigned as a parent (father/mother) or as a child
 *       (son/daughter)?</li>
 *   <li>Should a {@link Families.Family} that becomes empty after a member removal be
 *       deleted?</li>
 * </ul>
 * 
 * <p>Multiple implementations are provided:
 * <ul>
 *   <li>{@link DefaultTargetToSourceDecision} – deterministic default behaviour</li>
 *   <li>{@link ConfigurableTargetToSourceDecision} – flag-based configuration</li>
 *   <li>{@link UserTargetToSourceDecision} – interactive Swing dialog</li>
 * </ul>
 * 
 * <p>Part of the <em>BXtend Families-to-Persons</em> bidirectional transformation.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions;

import Families.Family;
import Families.FamilyMember;
import Persons.Person;
import java.util.List;

@SuppressWarnings("all")
public interface TargetToSourceDecision {
  /**
   * Returns the maximum number of candidate families that {@link #getFamily} will ever
   * inspect.  The framework uses this hint to limit the size of the list passed in.
   * 
   * @return the maximum list size needed, or {@code -1} if all matching families are
   *         required (e.g. when the user must choose from a full list).
   */
  int getFamilyListSize();

  /**
   * Selects the {@link Family} into which the {@link FamilyMember} corresponding to
   * {@code person} should be placed.
   * 
   * @param families     candidate families whose name matches the family part of
   *                     {@code person.name}; may be empty
   * @param person       the Person element being transformed back to a FamilyMember
   * @param actualFamily the Family that currently contains the FamilyMember, or
   *                     {@code null} if none exists yet
   * @return the chosen {@link Family}, or {@code null} to request the creation of a
   *         brand-new Family
   */
  Family getFamily(final List<Family> families, final Person person, final Family actualFamily);

  /**
   * Decides whether the {@link FamilyMember} corresponding to {@code person} should
   * be registered as a parent (father/mother) rather than a child (son/daughter) in
   * {@code family}.
   * 
   * @param person the Person element being transformed back
   * @param family the target Family
   * @return {@code true} if the member should be set as a parent, {@code false} if it
   *         should be added as a child
   */
  boolean setAsParent(final Person person, final Family family);

  /**
   * Decides whether a {@link Family} that has become empty after the removal of its
   * last member should be deleted from the source model.
   * 
   * @param family     the now-empty Family
   * @param lastMember the FamilyMember that was just removed
   * @return {@code true} if {@code family} should be deleted, {@code false} to keep it
   */
  boolean deleteEmptyFamily(final Family family, final FamilyMember lastMember);

  /**
   * Called once before each transformation pass.  Implementations may use this hook
   * to show a user interface, reset state, or perform any other initialisation.
   * 
   * @return {@code true} if the transformation should proceed, {@code false} to abort
   */
  boolean init();

  /**
   * Called whenever a {@link Person} is assigned to a newly created {@link Family}.
   * Implementations may use this hook to update internal bookkeeping structures.
   * 
   * @param person the Person that triggered the Family creation
   * @param family the newly created Family
   */
  void linkPersonToFamily(final Person person, final Family family);
}
