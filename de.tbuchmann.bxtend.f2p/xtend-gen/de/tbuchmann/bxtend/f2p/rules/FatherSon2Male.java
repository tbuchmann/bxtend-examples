/**
 * Concrete bidirectional transformation rule for the male-gender correspondence:
 * {@link Families.FamilyMember} in the roles <em>father</em> or <em>son</em>
 * ↔ {@link Persons.Male}.
 * 
 * <p>All three transformation directions are implemented:
 * <ul>
 *   <li><b>Forward ({@link #sourceToTarget()}):</b> iterates over all fathers and sons
 *       in the Families model and creates or updates the corresponding {@link Persons.Male}
 *       elements in the Persons model.</li>
 *   <li><b>Backward ({@link #targetToSource()}):</b> iterates over all {@link Persons.Male}
 *       elements and transforms them back into {@link Families.FamilyMember} objects placed
 *       in the appropriate families.</li>
 *   <li><b>Synchronisation ({@link #synch()}):</b> reconciles concurrent changes in both
 *       models; members without a corr entry are matched to unmatched males by name,
 *       and any leftover males from the Persons side are removed.</li>
 * </ul>
 * 
 * <p>Rule identifier: {@code "FatherSon2Male"}.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.Family;
import Families.FamilyMember;
import Persons.Male;
import Persons.Person;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

@SuppressWarnings("all")
public class FatherSon2Male extends FamilyMember2Person {
  /**
   * Constructs a new FatherSon2Male rule.
   * 
   * @param src  the Families source model resource
   * @param trgt the Persons target model resource
   * @param corr the correspondence model resource
   * @param dec  the strategy for resolving backward-transformation decisions
   */
  public FatherSon2Male(final Resource src, final Resource trgt, final Resource corr, final TargetToSourceDecision dec) {
    super(src, trgt, corr, dec);
    this.ruleID = "FatherSon2Male";
  }

  /**
   * Forward direction (Families → Persons): processes every father and son
   * {@link Families.FamilyMember} in the source model and produces a corresponding
   * {@link Persons.Male} element in the target model.
   * 
   * <p>The male person is added to the {@link Persons.PersonRegister} that corresponds
   * to the containing {@link Families.FamilyRegister}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Family> _function = (Family family) -> {
      final BasicEList<FamilyMember> males = ECollections.<FamilyMember>newBasicEList();
      males.addAll(family.getSons());
      FamilyMember _father = family.getFather();
      boolean _tripleNotEquals = (_father != null);
      if (_tripleNotEquals) {
        males.add(family.getFather());
      }
      final Consumer<FamilyMember> _function_1 = (FamilyMember member) -> {
        this.addPerson(member, "FatherSon2Male");
      };
      males.forEach(_function_1);
    };
    IteratorExtensions.<Family>forEach(Iterators.<Family>filter(this.sourceModel.getAllContents(), Family.class), _function);
  }

  /**
   * Backward direction (Persons → Families): processes every {@link Persons.Male}
   * element in the target model and places a corresponding {@link Families.FamilyMember}
   * into the appropriate {@link Families.Family} in the source model.
   * 
   * <p>Delegation to {@link FamilyMember2Person#transformPerson} handles family
   * selection, role assignment (father vs. son), and optional empty-family cleanup.
   */
  @Override
  public void targetToSource() {
    final Procedure1<Male> _function = (Male p) -> {
      final Corr corr = this.getOrCreateCorrModelElement(p, "FatherSon2Male");
      this.transformPerson(corr, p);
    };
    IteratorExtensions.<Male>forEach(Iterators.<Male>filter(this.targetModel.getAllContents(), Male.class), _function);
  }

  /**
   * Synchronisation direction: reconciles concurrent changes made to fathers/sons on
   * the Families side and males on the Persons side.
   * 
   * <p>Algorithm:
   * <ol>
   *   <li>Collects all unmatched (no-correspondence) {@link Persons.Male} objects.</li>
   *   <li>For each father and son in the source model, calls
   *       {@link FamilyMember2Person#synchFamilyMember}, which will either update an
   *       existing match, link an unmatched Male, or create a new Male.</li>
   *   <li>Any Males that are still unmatched after step 2 (i.e. they exist in the
   *       Persons model but have no corresponding FamilyMember) are deleted.</li>
   * </ol>
   */
  @Override
  public void synch() {
    final Function1<FamilyMember, Boolean> _function = (FamilyMember fm) -> {
      return Boolean.valueOf(((fm.getFatherInverse() != null) || (fm.getSonsInverse() != null)));
    };
    final List<FamilyMember> fmList = IteratorExtensions.<FamilyMember>toList(IteratorExtensions.<FamilyMember>filter(Iterators.<FamilyMember>filter(this.sourceModel.getAllContents(), FamilyMember.class), _function));
    final ArrayList<Person> pList = new ArrayList<Person>();
    List<Person> _list = IteratorExtensions.<Person>toList(Iterators.<Male>filter(this.targetModel.getAllContents(), Male.class));
    Iterables.<Person>addAll(pList, _list);
    final Consumer<FamilyMember> _function_1 = (FamilyMember it) -> {
      this.synchFamilyMember(it, pList, "FatherSon2Male");
    };
    fmList.forEach(_function_1);
    final Consumer<Person> _function_2 = (Person it) -> {
      Corr _corrModelElem = this.getCorrModelElem(it);
      boolean _tripleEquals = (_corrModelElem == null);
      if (_tripleEquals) {
        this.transformPerson(this.getOrCreateCorrModelElement(it, "FatherSon2Male"), it);
      }
    };
    pList.forEach(_function_2);
  }
}
