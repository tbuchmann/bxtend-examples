/**
 * Flag-based implementation of {@link TargetToSourceDecision} that allows fine-grained
 * control over the backward-transformation (Persons → Families) decisions through a set
 * of boolean configuration flags supplied at construction time.
 * 
 * <p>Configuration flags and their effects:
 * <ul>
 *   <li><b>alwaysNewFamily</b> – when {@code true}, every Person always triggers creation
 *       of a brand-new Family; no existing family is ever reused.</li>
 *   <li><b>preferParent</b> – when {@code true} (and {@code alwaysNewFamily} is
 *       {@code false}), the algorithm first tries to place the member in the parent slot
 *       (father for {@link Persons.Male}, mother otherwise); it falls back to child
 *       placement if the parent slot is already taken.</li>
 *   <li><b>forceParent</b> – when {@code true}, the member is <em>always</em> placed as
 *       a parent regardless of whether the slot is free; may displace an existing
 *       parent to the children list.</li>
 *   <li><b>deleteEmptyFamilies</b> – controls whether a {@link Families.Family} that
 *       becomes empty after a member removal is automatically deleted.</li>
 * </ul>
 * 
 * <p>The {@link #getFamilyListSize()} hint varies with the flags so that the framework
 * only retrieves as many candidate families as the policy actually needs.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions;

import Families.Family;
import Families.FamilyMember;
import Persons.Male;
import Persons.Person;
import java.util.List;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;

@SuppressWarnings("all")
public class ConfigurableTargetToSourceDecision implements TargetToSourceDecision {
  /**
   * When {@code true} every Person is placed in a new Family; ignores all candidates.
   */
  private boolean alwaysNewFamily;

  /**
   * When {@code true} the algorithm tries to fill the parent slot before using a child slot.
   */
  private boolean preferParent;

  /**
   * When {@code true} the member is unconditionally placed in the parent slot.
   */
  private boolean forceParent;

  /**
   * Controls automatic deletion of Families that become empty.
   */
  private boolean deleteEmptyFamily;

  /**
   * Constructs a new configurable decision object.
   * 
   * @param alwaysNewFamily     see field documentation
   * @param preferParent        see field documentation
   * @param forceParent         see field documentation
   * @param deleteEmptyFamilies see field documentation
   */
  public ConfigurableTargetToSourceDecision(final boolean alwaysNewFamily, final boolean preferParent, final boolean forceParent, final boolean deleteEmptyFamilies) {
    this.alwaysNewFamily = alwaysNewFamily;
    this.preferParent = preferParent;
    this.forceParent = forceParent;
    this.deleteEmptyFamily = this.deleteEmptyFamily;
  }

  /**
   * Selects an existing Family for the given Person according to the configured policy.
   * 
   * <ul>
   *   <li>If {@code alwaysNewFamily} is set or {@code families} is empty, returns
   *       {@code null} to force creation of a new family.</li>
   *   <li>If {@code preferParent} is set, returns the first family whose parent slot
   *       (father for {@link Male}, mother otherwise) is still vacant; falls back to
   *       {@code families.get(0)} if no vacancy exists.</li>
   *   <li>Otherwise, simply returns {@code families.get(0)}.</li>
   * </ul>
   * 
   * @param families    candidate families with the matching name
   * @param person      the Person being transformed back
   * @param actualFamily the Family currently containing the member (unused here)
   * @return the selected Family or {@code null} to create a new one
   */
  @Override
  public Family getFamily(final List<Family> families, final Person person, final Family actualFamily) {
    if ((this.alwaysNewFamily || families.isEmpty())) {
      return null;
    } else {
      if (((!this.alwaysNewFamily) && this.preferParent)) {
        Family fam = null;
        if ((person instanceof Male)) {
          final Function1<Family, Boolean> _function = (Family f) -> {
            FamilyMember _father = f.getFather();
            return Boolean.valueOf((_father == null));
          };
          fam = IterableExtensions.<Family>findFirst(families, _function);
        } else {
          final Function1<Family, Boolean> _function_1 = (Family f) -> {
            FamilyMember _mother = f.getMother();
            return Boolean.valueOf((_mother == null));
          };
          fam = IterableExtensions.<Family>findFirst(families, _function_1);
        }
        if ((fam != null)) {
          return fam;
        } else {
          return families.get(0);
        }
      } else {
        return families.get(0);
      }
    }
  }

  /**
   * Decides whether the Person should be placed in the parent slot of {@code family}.
   * 
   * <ul>
   *   <li>If {@code forceParent} is set, always returns {@code true}.</li>
   *   <li>If {@code preferParent} is not set, always returns {@code false}.</li>
   *   <li>Otherwise, returns {@code true} only when the relevant parent slot is free.</li>
   * </ul>
   * 
   * @param person the Person being placed
   * @param family the target Family
   * @return {@code true} if the member should become a parent
   */
  @Override
  public boolean setAsParent(final Person person, final Family family) {
    if (this.forceParent) {
      return true;
    }
    if ((!this.preferParent)) {
      return false;
    }
    if ((person instanceof Male)) {
      FamilyMember _father = family.getFather();
      return (_father == null);
    } else {
      FamilyMember _mother = family.getMother();
      return (_mother == null);
    }
  }

  /**
   * Returns the value of the {@code deleteEmptyFamily} configuration flag.
   * 
   * @param family      the now-empty Family (unused)
   * @param lastMembwer the last removed FamilyMember (unused)
   * @return the configured flag value
   */
  @Override
  public boolean deleteEmptyFamily(final Family family, final FamilyMember lastMembwer) {
    return this.deleteEmptyFamily;
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
   * Returns the number of candidate families needed by the configured policy:
   * <ul>
   *   <li>{@code 0} when {@code alwaysNewFamily} is set (no candidates needed)</li>
   *   <li>{@code 1} when {@code preferParent} is not set (only first candidate needed)</li>
   *   <li>{@code -1} otherwise (all matching families may be inspected)</li>
   * </ul>
   * 
   * @return size hint for the candidate family list
   */
  @Override
  public int getFamilyListSize() {
    int _xifexpression = (int) 0;
    if (this.alwaysNewFamily) {
      _xifexpression = 0;
    } else {
      int _xifexpression_1 = (int) 0;
      if ((!this.preferParent)) {
        _xifexpression_1 = 1;
      } else {
        _xifexpression_1 = (-1);
      }
      _xifexpression = _xifexpression_1;
    }
    return _xifexpression;
  }
}
