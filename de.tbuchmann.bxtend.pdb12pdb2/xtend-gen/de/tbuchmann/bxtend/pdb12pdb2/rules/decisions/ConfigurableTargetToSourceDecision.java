package de.tbuchmann.bxtend.pdb12pdb2.rules.decisions;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Conversions;
import org.eclipse.xtext.xbase.lib.IterableExtensions;

/**
 * Configurable implementation of {@link TargetToSourceDecision} that splits a PDB2
 * full name into PDB1 {@code firstName} and {@code lastName} at the n-th space.
 * 
 * <h3>Split semantics</h3>
 * <p>The {@code spacePosition} constructor parameter controls the split point:</p>
 * 
 * <table border="1">
 *   <tr><th>spacePosition value</th><th>Split behaviour</th></tr>
 *   <tr><td>{@code 0}</td>
 *       <td>firstName is empty; lastName is the complete name string.</td></tr>
 *   <tr><td>{@code > 0} (e.g. 1)</td>
 *       <td>Split at the n-th space (1-based). Everything before the n-th space
 *           becomes firstName; everything from the n-th token onward becomes lastName.
 *           If the name has fewer spaces than {@code spacePosition}, firstName is
 *           the complete name and lastName is empty.</td></tr>
 *   <tr><td>{@code < 0} (default: -1)</td>
 *       <td>Split at the <em>last</em> space. Everything up to (but excluding) the
 *           last space becomes firstName; the last space-separated token becomes
 *           lastName.  This is the default used by {@link
 *           de.tbuchmann.bxtend.pdb12pdb2.rules.Pdb12pdb2Transformation}.</td></tr>
 * </table>
 * 
 * <h3>Example – {@code "Konrad Hermann Joseph Adenauer"}</h3>
 * <table border="1">
 *   <tr><th>spacePosition</th><th>firstName</th><th>lastName</th></tr>
 *   <tr><td>0</td><td>(empty)</td><td>Konrad Hermann Joseph Adenauer</td></tr>
 *   <tr><td>1</td><td>Konrad</td><td>Hermann Joseph Adenauer</td></tr>
 *   <tr><td>-1 (last)</td><td>Konrad Hermann Joseph</td><td>Adenauer</td></tr>
 * </table>
 */
@SuppressWarnings("all")
public class ConfigurableTargetToSourceDecision implements TargetToSourceDecision {
  /**
   * The 1-based space index at which to split the name, or {@code -1} for the last space.
   */
  private int space;

  /**
   * Constructs a new ConfigurableTargetToSourceDecision. It will divide the given name on the spacePosition's space.
   * If spacePosition is larger as the amount of spaces in the given name, the firstName is the complete name and lastName is empty.
   * If spacePosition is smaller than 0, the last space will be chosen.
   * If spacePosition is 0, the lastName will be the complete name and firstName is empty
   */
  public ConfigurableTargetToSourceDecision(final int spacePosition) {
    this.space = spacePosition;
  }

  /**
   * Returns everything to the left of the chosen split space as the {@code firstName}.
   * 
   * @param name the PDB2 full name string; must not be {@code null}
   * @return the firstName portion, possibly empty when {@code spacePosition == 0}
   */
  @Override
  public String getFirstName(final String name) {
    String _xblockexpression = null;
    {
      if ((this.space == 0)) {
        return "";
      }
      if ((this.space < 0)) {
        return name.substring(0, name.lastIndexOf(" "));
      }
      final ArrayList<String> list = CollectionLiterals.<String>newArrayList();
      String[] _split = name.split(" ");
      Iterables.<String>addAll(list, ((Iterable<? extends String>)Conversions.doWrapArray(_split)));
      int i = this.space;
      while ((i < list.size())) {
        list.remove(i);
      }
      _xblockexpression = IterableExtensions.join(list, " ");
    }
    return _xblockexpression;
  }

  /**
   * Returns everything to the right of the chosen split space as the {@code lastName}.
   * 
   * @param name the PDB2 full name string; must not be {@code null}
   * @return the lastName portion, possibly empty when the name has fewer spaces
   *         than {@code spacePosition}
   */
  @Override
  public String getLastName(final String name) {
    String _xblockexpression = null;
    {
      if ((this.space == 0)) {
        return name;
      }
      if ((this.space < 0)) {
        int _lastIndexOf = name.lastIndexOf(" ");
        int _plus = (_lastIndexOf + 1);
        return name.substring(_plus);
      }
      final ArrayList<String> list = CollectionLiterals.<String>newArrayList();
      String[] _split = name.split(" ");
      Iterables.<String>addAll(list, ((Iterable<? extends String>)Conversions.doWrapArray(_split)));
      int i = 0;
      while (((i < this.space) && (i < list.size()))) {
        int _plusPlus = i++;
        list.remove(_plusPlus);
      }
      _xblockexpression = IterableExtensions.join(list, " ");
    }
    return _xblockexpression;
  }
}
