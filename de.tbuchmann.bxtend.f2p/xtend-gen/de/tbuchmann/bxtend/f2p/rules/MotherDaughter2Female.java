/**
 * Concrete bidirectional transformation rule for the female-gender correspondence:
 * {@link Families.FamilyMember} in the roles <em>mother</em> or <em>daughter</em>
 * ↔ {@link Persons.Female}.
 * 
 * <p>All three transformation directions are implemented:
 * <ul>
 *   <li><b>Forward ({@link #sourceToTarget()}):</b> iterates over all mothers and
 *       daughters in the Families model and creates or updates the corresponding
 *       {@link Persons.Female} elements in the Persons model.</li>
 *   <li><b>Backward ({@link #targetToSource()}):</b> iterates over all
 *       {@link Persons.Female} elements and transforms them back into
 *       {@link Families.FamilyMember} objects placed in the appropriate families.</li>
 *   <li><b>Synchronisation ({@link #synch()}):</b> reconciles concurrent changes in both
 *       models; members without a corr entry are matched to unmatched females by name,
 *       and any leftover females from the Persons side are removed.</li>
 * </ul>
 * 
 * <p>This rule is the female-gender counterpart of {@link FatherSon2Male}.
 * Rule identifier: {@code "MotherDaughter2Female"}.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.Family;
import Families.FamilyMember;
import Persons.Female;
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
public class MotherDaughter2Female extends FamilyMember2Person {
  /**
   * Constructs a new MotherDaughter2Female rule.
   * 
   * @param src  the Families source model resource
   * @param trgt the Persons target model resource
   * @param corr the correspondence model resource
   * @param dec  the strategy for resolving backward-transformation decisions
   */
  public MotherDaughter2Female(final Resource src, final Resource trgt, final Resource corr, final TargetToSourceDecision dec) {
    super(src, trgt, corr, dec);
    this.ruleID = "MotherDaughter2Female";
  }

  /**
   * Forward direction (Families → Persons): processes every mother and daughter
   * {@link Families.FamilyMember} in the source model and produces a corresponding
   * {@link Persons.Female} element in the target model.
   * 
   * <p>The female person is added to the {@link Persons.PersonRegister} that corresponds
   * to the containing {@link Families.FamilyRegister}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Family> _function = (Family family) -> {
      final BasicEList<FamilyMember> females = ECollections.<FamilyMember>newBasicEList();
      females.addAll(family.getDaughters());
      FamilyMember _mother = family.getMother();
      boolean _tripleNotEquals = (_mother != null);
      if (_tripleNotEquals) {
        females.add(family.getMother());
      }
      final Consumer<FamilyMember> _function_1 = (FamilyMember member) -> {
        this.addPerson(member, "MotherDaughter2Female");
      };
      females.forEach(_function_1);
    };
    IteratorExtensions.<Family>forEach(Iterators.<Family>filter(this.sourceModel.getAllContents(), Family.class), _function);
  }

  /**
   * Backward direction (Persons → Families): processes every {@link Persons.Female}
   * element in the target model and places a corresponding {@link Families.FamilyMember}
   * into the appropriate {@link Families.Family} in the source model.
   * 
   * <p>Delegation to {@link FamilyMember2Person#transformPerson} handles family
   * selection, role assignment (mother vs. daughter), and optional empty-family cleanup.
   */
  @Override
  public void targetToSource() {
    final Procedure1<Female> _function = (Female p) -> {
      final Corr corr = this.getOrCreateCorrModelElement(p, "MotherDaughter2Female");
      this.transformPerson(corr, p);
    };
    IteratorExtensions.<Female>forEach(Iterators.<Female>filter(this.targetModel.getAllContents(), Female.class), _function);
  }

  /**
   * Synchronisation direction: reconciles concurrent changes made to mothers/daughters
   * on the Families side and females on the Persons side.
   * 
   * <p>Algorithm:
   * <ol>
   *   <li>Collects all unmatched (no-correspondence) {@link Persons.Female} objects.</li>
   *   <li>For each mother and daughter in the source model, calls
   *       {@link FamilyMember2Person#synchFamilyMember}, which will either update an
   *       existing match, link an unmatched Female, or create a new Female.</li>
   *   <li>Any Females that are still unmatched after step 2 (i.e. they exist in the
   *       Persons model but have no corresponding FamilyMember) are processed via
   *       {@link FamilyMember2Person#transformPerson}.</li>
   * </ol>
   */
  @Override
  public void synch() {
    final Function1<FamilyMember, Boolean> _function = (FamilyMember fm) -> {
      return Boolean.valueOf(((fm.getMotherInverse() != null) || (fm.getDaughtersInverse() != null)));
    };
    final List<FamilyMember> fmList = IteratorExtensions.<FamilyMember>toList(IteratorExtensions.<FamilyMember>filter(Iterators.<FamilyMember>filter(this.sourceModel.getAllContents(), FamilyMember.class), _function));
    final ArrayList<Person> pList = new ArrayList<Person>();
    List<Person> _list = IteratorExtensions.<Person>toList(Iterators.<Female>filter(this.targetModel.getAllContents(), Female.class));
    Iterables.<Person>addAll(pList, _list);
    final Consumer<FamilyMember> _function_1 = (FamilyMember it) -> {
      this.synchFamilyMember(it, pList, "MotherDaughter2Female");
    };
    fmList.forEach(_function_1);
    final Consumer<Person> _function_2 = (Person it) -> {
      Corr _corrModelElem = this.getCorrModelElem(it);
      boolean _tripleEquals = (_corrModelElem == null);
      if (_tripleEquals) {
        this.transformPerson(this.getOrCreateCorrModelElement(it, "MotherDaughter2Female"), it);
      }
    };
    pList.forEach(_function_2);
  }
}
