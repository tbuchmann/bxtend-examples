/**
 * Bidirectional transformation rule that synchronises the top-level register elements:
 * {@link Families.FamilyRegister} (source) ↔ {@link Persons.PersonRegister} (target).
 * 
 * <p>This rule is responsible for the root-level correspondence only.  It does <em>not</em>
 * recurse into child elements; that is handled by {@link FamilyMember2Person} and its
 * subclasses.
 * 
 * <p>All three transformation directions are supported:
 * <ul>
 *   <li>{@link #sourceToTarget()} – ensures a {@link Persons.PersonRegister} exists and
 *       is linked to the {@link Families.FamilyRegister} via a correspondence.</li>
 *   <li>{@link #targetToSource()} – ensures a {@link Families.FamilyRegister} exists and
 *       is linked to the {@link Persons.PersonRegister} via a correspondence.</li>
 *   <li>{@link #synch()} – ensures that both registers exist and are cross-linked.</li>
 * </ul>
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.FamiliesPackage;
import Families.FamilyRegister;
import Persons.PersonRegister;
import Persons.PersonsPackage;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

@SuppressWarnings("all")
public class Register2Register extends Elem2Elem {
  /**
   * Constructs a new Register2Register rule.
   * 
   * @param src  the Families source model resource
   * @param trgt the Persons target model resource
   * @param corr the correspondence model resource
   * @param dec  the strategy for resolving backward-transformation decisions
   */
  public Register2Register(final Resource src, final Resource trgt, final Resource corr, final TargetToSourceDecision dec) {
    super(src, trgt, corr, dec);
    this.ruleID = "Register2Register";
  }

  /**
   * Forward direction: Families → Persons.
   * 
   * <p>Reads the {@link FamilyRegister} from the source model, creates (or reuses) a
   * {@link PersonRegister} in the target model, and establishes a correspondence
   * between the two root objects.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<FamilyRegister> _function = (FamilyRegister c) -> {
      final Corr corrTarget = this.getOrCreateCorrModelElement(c, "Register2Register");
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corrTarget, PersonsPackage.eINSTANCE.getPersonRegister());
      final PersonRegister targetElement = ((PersonRegister) _orCreateTargetElem);
      boolean _contains = this.targetModel.getContents().contains(targetElement);
      boolean _not = (!_contains);
      if (_not) {
        this.targetModel.getContents().add(targetElement);
      }
    };
    IteratorExtensions.<FamilyRegister>forEach(Iterators.<FamilyRegister>filter(this.sourceModel.getAllContents(), FamilyRegister.class), _function);
  }

  /**
   * Backward direction: Persons → Families.
   * 
   * <p>Reads the {@link PersonRegister} from the target model, creates (or reuses) a
   * {@link FamilyRegister} in the source model, and establishes a correspondence
   * between the two root objects.
   */
  @Override
  public void targetToSource() {
    final Procedure1<PersonRegister> _function = (PersonRegister c) -> {
      final Corr corrTarget = this.getOrCreateCorrModelElement(c, "Register2Register");
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corrTarget, FamiliesPackage.eINSTANCE.getFamilyRegister());
      final FamilyRegister sourceElement = ((FamilyRegister) _orCreateSourceElem);
      boolean _contains = this.sourceModel.getContents().contains(sourceElement);
      boolean _not = (!_contains);
      if (_not) {
        this.sourceModel.getContents().add(sourceElement);
      }
    };
    IteratorExtensions.<PersonRegister>forEach(Iterators.<PersonRegister>filter(this.targetModel.getAllContents(), PersonRegister.class), _function);
  }

  /**
   * Synchronisation direction: concurrent edits in both models.
   * 
   * <p>Ensures that both a {@link FamilyRegister} and a {@link PersonRegister} exist
   * and that they are mutually linked via a correspondence entry.
   */
  @Override
  public void synch() {
    final List<FamilyRegister> famRegList = IteratorExtensions.<FamilyRegister>toList(Iterators.<FamilyRegister>filter(this.sourceModel.getAllContents(), FamilyRegister.class));
    final List<PersonRegister> persRegList = IteratorExtensions.<PersonRegister>toList(Iterators.<PersonRegister>filter(this.targetModel.getAllContents(), PersonRegister.class));
    for (final FamilyRegister fr : famRegList) {
      {
        final Corr corr = this.getOrCreateCorrModelElement(fr, "Register2Register");
        final EObject target = corr.getTargetElement();
        if ((target != null)) {
          persRegList.remove(target);
        } else {
          corr.setTargetElement(IterableExtensions.<PersonRegister>head(persRegList));
          persRegList.remove(corr.getTargetElement());
        }
      }
    }
    int _size = persRegList.size();
    boolean _greaterThan = (_size > 0);
    if (_greaterThan) {
      for (final PersonRegister pr : persRegList) {
        {
          final Corr corr = this.getOrCreateCorrModelElement(pr, "Register2Register");
          EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, FamiliesPackage.eINSTANCE.getFamilyRegister());
          final FamilyRegister sourceElement = ((FamilyRegister) _orCreateSourceElem);
          boolean _contains = this.sourceModel.getContents().contains(sourceElement);
          boolean _not = (!_contains);
          if (_not) {
            this.sourceModel.getContents().add(sourceElement);
          }
        }
      }
    }
  }
}
