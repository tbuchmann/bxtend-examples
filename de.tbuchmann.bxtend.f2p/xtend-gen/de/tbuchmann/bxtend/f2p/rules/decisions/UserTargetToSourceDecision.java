/**
 * Interactive Swing-dialog implementation of {@link TargetToSourceDecision} that asks
 * the user to make all backward-transformation (Persons → Families) decisions at
 * runtime.
 * 
 * <p>Before each transformation pass, a dialog is shown that lets the user configure the
 * following settings once for the entire pass:
 * <ul>
 *   <li>Whether members should always be placed in a new Family.</li>
 *   <li>Whether the parent slot should be preferred over child slots.</li>
 *   <li>Whether empty Families should be deleted after member removals.</li>
 * </ul>
 * In addition, whenever a Person is created and matches multiple families by name, the
 * user is presented with a list of candidates and can choose the target Family
 * interactively.
 * 
 * <p>This implementation is intended for demonstration and interactive tool scenarios.
 * For automated tests use {@link DefaultTargetToSourceDecision} or
 * {@link ConfigurableTargetToSourceDecision} instead.
 */
package de.tbuchmann.bxtend.f2p.rules.decisions;

import Families.Family;
import Families.FamilyMember;
import Persons.Male;
import Persons.Person;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.JOptionPane;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Conversions;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ListExtensions;

@SuppressWarnings("all")
public class UserTargetToSourceDecision implements TargetToSourceDecision {
  /**
   * {@code true} when the user chose to always create new families this pass.
   */
  private boolean alwaysNewFamily = false;

  /**
   * {@code true} when the user chose to prefer the parent slot this pass.
   */
  private boolean preferParent = false;

  /**
   * {@code true} when the user chose to delete empty families this pass.
   */
  private boolean deleteEmptyFamilies = false;

  /**
   * Shows a configuration dialog so the user can set the pass-level policy flags.
   * 
   * @return {@code true} if the user confirmed the dialog, {@code false} if cancelled
   */
  @Override
  public boolean init() {
    boolean _xblockexpression = false;
    {
      final List<String> options = Collections.<String>unmodifiableList(CollectionLiterals.<String>newArrayList("Yes", "No"));
      int _showOptionDialog = JOptionPane.showOptionDialog(null, "Always create a new family?", 
        "Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ((Object[])Conversions.unwrapArray(options, Object.class)), options.get(0));
      boolean _equals = (_showOptionDialog == 0);
      this.alwaysNewFamily = _equals;
      int _showOptionDialog_1 = JOptionPane.showOptionDialog(null, "Prefer parent role?", 
        "Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ((Object[])Conversions.unwrapArray(options, Object.class)), options.get(0));
      boolean _equals_1 = (_showOptionDialog_1 == 0);
      this.preferParent = _equals_1;
      int _showOptionDialog_2 = JOptionPane.showOptionDialog(null, "Delete empty families?", 
        "Transformation decision", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, ((Object[])Conversions.unwrapArray(options, Object.class)), options.get(0));
      boolean _equals_2 = (_showOptionDialog_2 == 0);
      this.deleteEmptyFamilies = _equals_2;
      _xblockexpression = true;
    }
    return _xblockexpression;
  }

  /**
   * Selects a target Family according to the user-configured policy.
   * 
   * <p>If {@code alwaysNewFamily} is set, or if {@code families} is empty, returns
   * {@code null} to create a new Family.  If there is exactly one candidate it is
   * returned immediately.  If there are multiple candidates and {@code preferParent} is
   * set, the first family with a free parent slot is preferred; if none is free, or
   * {@code preferParent} is not set, the user is asked to choose from a dialog.
   * 
   * @param families     candidate families whose name matches the person's family name
   * @param person       the Person being transformed back
   * @param actualFamily the Family currently containing the FamilyMember (unused)
   * @return the chosen Family or {@code null} to create a new one
   */
  @Override
  public Family getFamily(final List<Family> families, final Person person, final Family actualFamily) {
    if ((this.alwaysNewFamily || families.isEmpty())) {
      return null;
    }
    int _size = families.size();
    boolean _equals = (_size == 1);
    if (_equals) {
      return families.get(0);
    }
    if (this.preferParent) {
      Family _xifexpression = null;
      if ((person instanceof Male)) {
        final Function1<Family, Boolean> _function = (Family f) -> {
          FamilyMember _father = f.getFather();
          return Boolean.valueOf((_father == null));
        };
        _xifexpression = IterableExtensions.<Family>findFirst(families, _function);
      } else {
        final Function1<Family, Boolean> _function_1 = (Family f) -> {
          FamilyMember _mother = f.getMother();
          return Boolean.valueOf((_mother == null));
        };
        _xifexpression = IterableExtensions.<Family>findFirst(families, _function_1);
      }
      final Family fam = _xifexpression;
      if ((fam != null)) {
        return fam;
      }
    }
    final Function1<Family, String> _function_2 = (Family f) -> {
      return f.getName();
    };
    final List<String> familyNames = IterableExtensions.<String>toList(ListExtensions.<Family, String>map(families, _function_2));
    familyNames.add("Create new family");
    String _name = person.getName();
    String _plus = ("Choose the family for " + _name);
    String _plus_1 = (_plus + ":");
    Object _showInputDialog = JOptionPane.showInputDialog(null, _plus_1, 
      "Select family", JOptionPane.QUESTION_MESSAGE, null, 
      familyNames.toArray(), familyNames.get(0));
    final String choice = ((String) _showInputDialog);
    if (((choice == null) || Objects.equals(choice, "Create new family"))) {
      return null;
    }
    final Function1<Family, Boolean> _function_3 = (Family f) -> {
      String _name_1 = f.getName();
      return Boolean.valueOf(Objects.equals(_name_1, choice));
    };
    return IterableExtensions.<Family>findFirst(families, _function_3);
  }

  /**
   * Decides whether the member should occupy the parent slot.
   * 
   * <p>Returns {@code false} when {@code preferParent} is not set.  Otherwise returns
   * {@code true} only when the relevant parent slot in {@code family} is still vacant.
   * 
   * @param person the Person being placed
   * @param family the target Family
   * @return {@code true} if the member should be placed as a parent
   */
  @Override
  public boolean setAsParent(final Person person, final Family family) {
    boolean _xblockexpression = false;
    {
      if ((!this.preferParent)) {
        return false;
      }
      boolean _xifexpression = false;
      if ((person instanceof Male)) {
        FamilyMember _father = family.getFather();
        _xifexpression = (_father == null);
      } else {
        FamilyMember _mother = family.getMother();
        _xifexpression = (_mother == null);
      }
      _xblockexpression = _xifexpression;
    }
    return _xblockexpression;
  }

  /**
   * Returns the user-configured value of the delete-empty-families flag.
   * 
   * @param family     the now-empty Family (unused)
   * @param lastMember the last removed FamilyMember (unused)
   * @return the configured flag value
   */
  @Override
  public boolean deleteEmptyFamily(final Family family, final FamilyMember lastMember) {
    return this.deleteEmptyFamilies;
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
   * Returns {@code -1} so the framework supplies all matching families for potential
   * interactive selection.
   * 
   * @return {@code -1}
   */
  @Override
  public int getFamilyListSize() {
    return (-1);
  }
}
