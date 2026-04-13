package de.tbuchmann.bxtend.pdb12pdb2.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr;
import java.util.Objects;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import pdb1.Person;
import pdb2.Database;

/**
 * BXtend transformation rule that synchronises {@code pdb1.Person} elements with
 * {@code pdb2.Person} elements in both directions.
 * 
 * <p>This rule handles the non-trivial part of the PDB1 ↔ PDB2 transformation: the
 * name attribute mismatch.  PDB1 stores a person's name as two separate attributes
 * ({@code firstName} and {@code lastName}), while PDB2 stores it as a single
 * {@code name} string.  The rules for each direction are therefore asymmetric:</p>
 * 
 * <ul>
 *   <li><b>Forward (PDB1 → PDB2):</b> {@code name = firstName + " " + lastName}
 *       — deterministic concatenation.</li>
 *   <li><b>Backward (PDB2 → PDB1):</b> the full {@code name} must be split back
 *       into {@code firstName} and {@code lastName}.  Because this split is
 *       inherently ambiguous (e.g. {@code "Konrad Hermann Joseph Adenauer"} could
 *       split at any space), the rule delegates the decision to the injected
 *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}
 *       strategy. The split is only re-computed when the concatenated name in PDB1
 *       no longer matches the PDB2 {@code name}, avoiding unnecessary overwrites
 *       during incremental propagation.</li>
 * </ul>
 * 
 * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id})
 * are identical in both metamodels and are simply copied in both directions.</p>
 * 
 * <p>The containment relationship ({@code database} reference) is resolved via the
 * correspondence model: the parent container of the source/target person is looked
 * up in the {@code elementsToCorr} index, and its counterpart element is used as
 * the new container in the other model.</p>
 */
@SuppressWarnings("all")
public class Person2Person extends Elem2Elem {
  /**
   * Creates a new {@code Person2Person} rule instance.
   * 
   * @param src  the PDB1 (source) EMF resource
   * @param trgt the PDB2 (target) EMF resource
   * @param corr the correspondence EMF resource
   */
  public Person2Person(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "Person2Person";
  }

  /**
   * Forward propagation: for every {@code pdb1.Person} in the source model,
   * creates or updates the corresponding {@code pdb2.Person} in the target model.
   * 
   * <p>Attribute mapping:</p>
   * <ul>
   *   <li>{@code birthday}, {@code placeOfBirth}, {@code id} → copied directly</li>
   *   <li>{@code firstName + " " + lastName} → {@code name}</li>
   *   <li>parent {@code pdb1.Database} → corresponding {@code pdb2.Database}
   *       (resolved through the correspondence model)</li>
   * </ul>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Person> _function = (Person source) -> {
      final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getPerson());
      final pdb2.Person target = ((pdb2.Person) _orCreateTargetElem);
      target.setBirthday(source.getBirthday());
      target.setPlaceOfBirth(source.getPlaceOfBirth());
      target.setId(source.getId());
      EObject _targetElement = this.getCorrModelElem(source.eContainer()).getTargetElement();
      target.setDatabase(((Database) _targetElement));
      String _firstName = source.getFirstName();
      String _plus = (_firstName + " ");
      String _lastName = source.getLastName();
      String _plus_1 = (_plus + _lastName);
      target.setName(_plus_1);
    };
    IteratorExtensions.<Person>forEach(Iterators.<Person>filter(this.sourceModel.getAllContents(), Person.class), _function);
  }

  /**
   * Backward propagation: for every {@code pdb2.Person} in the target model,
   * creates or updates the corresponding {@code pdb1.Person} in the source model.
   * 
   * <p>Attribute mapping:</p>
   * <ul>
   *   <li>{@code birthday}, {@code placeOfBirth}, {@code id} → copied directly</li>
   *   <li>{@code name} → {@code firstName} + {@code lastName} via the injected
   *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision};
   *       the split is only applied when the current PDB1 concatenated name differs
   *       from the PDB2 name, preserving existing splits during incremental runs.</li>
   *   <li>parent {@code pdb2.Database} → corresponding {@code pdb1.Database}
   *       (resolved through the correspondence model)</li>
   * </ul>
   */
  @Override
  public void targetToSource() {
    final Procedure1<pdb2.Person> _function = (pdb2.Person target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getPerson());
      final Person source = ((Person) _orCreateSourceElem);
      source.setBirthday(target.getBirthday());
      source.setPlaceOfBirth(target.getPlaceOfBirth());
      source.setId(target.getId());
      EObject _sourceElement = this.getCorrModelElem(target.eContainer()).getSourceElement();
      source.setDatabase(((pdb1.Database) _sourceElement));
      String _firstName = source.getFirstName();
      String _plus = (_firstName + " ");
      String _lastName = source.getLastName();
      String _plus_1 = (_plus + _lastName);
      String _name = target.getName();
      boolean _notEquals = (!Objects.equals(_plus_1, _name));
      if (_notEquals) {
        source.setFirstName(this.decision.getFirstName(target.getName()));
        source.setLastName(this.decision.getLastName(target.getName()));
      }
    };
    IteratorExtensions.<pdb2.Person>forEach(Iterators.<pdb2.Person>filter(this.targetModel.getAllContents(), pdb2.Person.class), _function);
  }
}
