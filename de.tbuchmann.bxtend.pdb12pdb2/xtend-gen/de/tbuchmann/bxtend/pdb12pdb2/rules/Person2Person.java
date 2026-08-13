package de.tbuchmann.bxtend.pdb12pdb2.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
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
 *       strategy. In {@link #targetToSource()} the split is re-derived whenever
 *       <em>any</em> tracked attribute of the person changed since the last backward
 *       call (not just the name text) — an untouched person keeps its previous split
 *       even if the decision strategy changes in between; {@link #synch()} only
 *       re-splits when the PDB2 {@code name} itself changed since the last
 *       synchronisation (tracked via {@link #corrToName}).</li>
 * </ul>
 * 
 * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id})
 * are identical in both metamodels and are simply copied in both directions; in
 * {@link #synch()} each is tracked independently of the name (see
 * {@link #corrToBirthday}, {@link #corrToPlaceOfBirth}, {@link #corrToId}), since a
 * concurrent edit can change one of them without touching the name.</p>
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
      Elem2Elem.corrToName.put(corr, target.getName());
      Elem2Elem.corrToBirthday.put(corr, source.getBirthday());
      Elem2Elem.corrToPlaceOfBirth.put(corr, source.getPlaceOfBirth());
      Elem2Elem.corrToId.put(corr, source.getId());
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
   *       {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}.
   *       The split is only re-derived when <em>something</em> about this person changed
   *       since the last backward propagation (name text, birthday, placeOfBirth, or id —
   *       tracked via the {@code corrTo*} snapshots); an entirely untouched person keeps
   *       whatever split was chosen last time even if the decision strategy changes in
   *       between (see {@link #synch()} below for why this is per-person, not per-name-text).</li>
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
      final boolean changed = ((((!Objects.equals(Elem2Elem.corrToName.get(corr), target.getName())) || (!Objects.equals(Elem2Elem.corrToBirthday.get(corr), target.getBirthday()))) || (!Objects.equals(Elem2Elem.corrToPlaceOfBirth.get(corr), target.getPlaceOfBirth()))) || (!Objects.equals(Elem2Elem.corrToId.get(corr), target.getId())));
      source.setBirthday(target.getBirthday());
      source.setPlaceOfBirth(target.getPlaceOfBirth());
      source.setId(target.getId());
      EObject _sourceElement = this.getCorrModelElem(target.eContainer()).getSourceElement();
      source.setDatabase(((pdb1.Database) _sourceElement));
      if (changed) {
        source.setFirstName(this.decision.getFirstName(target.getName()));
        source.setLastName(this.decision.getLastName(target.getName()));
      }
      Elem2Elem.corrToName.put(corr, target.getName());
      Elem2Elem.corrToBirthday.put(corr, source.getBirthday());
      Elem2Elem.corrToPlaceOfBirth.put(corr, source.getPlaceOfBirth());
      Elem2Elem.corrToId.put(corr, source.getId());
    };
    IteratorExtensions.<pdb2.Person>forEach(Iterators.<pdb2.Person>filter(this.targetModel.getAllContents(), pdb2.Person.class), _function);
  }

  /**
   * Reconciles concurrent edits to {@code Person} pairs.
   * 
   * <p>The identity/content key is the concatenated name
   * ({@code firstName + " " + lastName} on PDB1, the single {@code name} on PDB2).
   * Following the same push-forward-on-change / pull-backward-otherwise logic as
   * {@link Database2Database#synch()} (using {@link #corrToName}): if the source-side
   * concatenation changed since the last synchronisation, it is pushed forward (verbatim,
   * as a plain string — no re-splitting needed since it originates as a PDB1 split
   * already); otherwise, if the PDB2 {@code name} changed, it is split back via the
   * injected {@link de.tbuchmann.bxtend.pdb12pdb2.rules.decisions.TargetToSourceDecision}.</p>
   * 
   * <p>The remaining attributes ({@code birthday}, {@code placeOfBirth}, {@code id}) are
   * <strong>not</strong> tied to the name decision — each is resolved independently against
   * its own snapshot ({@link #corrToBirthday}, {@link #corrToPlaceOfBirth}, {@link #corrToId}),
   * since a concurrent edit can change one of them while the name stays untouched (see
   * {@code MonotonicDeleting#testCombinedMatchingDeletion}, which changes only
   * {@code placeOfBirth} on the target side of an otherwise-unmodified person).</p>
   */
  @Override
  public void synch() {
    final List<Person> personList = IteratorExtensions.<Person>toList(Iterators.<Person>filter(this.sourceModel.getAllContents(), Person.class));
    final Function1<pdb2.Person, Boolean> _function = (pdb2.Person p) -> {
      Corr _corrModelElem = this.getCorrModelElem(p);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<pdb2.Person> unmatched = IteratorExtensions.<pdb2.Person>toList(IteratorExtensions.<pdb2.Person>filter(Iterators.<pdb2.Person>filter(this.targetModel.getAllContents(), pdb2.Person.class), _function));
    final Consumer<Person> _function_1 = (Person source) -> {
      final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      pdb2.Person target = ((pdb2.Person) _targetElement);
      String _firstName = source.getFirstName();
      String _plus = (_firstName + " ");
      String _lastName = source.getLastName();
      final String sourceKey = (_plus + _lastName);
      if ((target != null)) {
        unmatched.remove(target);
        final String lastKey = Elem2Elem.corrToName.get(corr);
        if (((lastKey == null) || (!Objects.equals(sourceKey, lastKey)))) {
          target.setName(sourceKey);
          Elem2Elem.corrToName.put(corr, sourceKey);
        } else {
          String _name = target.getName();
          boolean _notEquals = (!Objects.equals(_name, lastKey));
          if (_notEquals) {
            source.setFirstName(this.decision.getFirstName(target.getName()));
            source.setLastName(this.decision.getLastName(target.getName()));
            Elem2Elem.corrToName.put(corr, target.getName());
          }
        }
        final String lastBirthday = Elem2Elem.corrToBirthday.get(corr);
        if (((lastBirthday == null) || (!Objects.equals(source.getBirthday(), lastBirthday)))) {
          target.setBirthday(source.getBirthday());
        } else {
          String _birthday = target.getBirthday();
          boolean _notEquals_1 = (!Objects.equals(_birthday, lastBirthday));
          if (_notEquals_1) {
            source.setBirthday(target.getBirthday());
          }
        }
        Elem2Elem.corrToBirthday.put(corr, source.getBirthday());
        final String lastPlaceOfBirth = Elem2Elem.corrToPlaceOfBirth.get(corr);
        if (((lastPlaceOfBirth == null) || (!Objects.equals(source.getPlaceOfBirth(), lastPlaceOfBirth)))) {
          target.setPlaceOfBirth(source.getPlaceOfBirth());
        } else {
          String _placeOfBirth = target.getPlaceOfBirth();
          boolean _notEquals_2 = (!Objects.equals(_placeOfBirth, lastPlaceOfBirth));
          if (_notEquals_2) {
            source.setPlaceOfBirth(target.getPlaceOfBirth());
          }
        }
        Elem2Elem.corrToPlaceOfBirth.put(corr, source.getPlaceOfBirth());
        final String lastId = Elem2Elem.corrToId.get(corr);
        if (((lastId == null) || (!Objects.equals(source.getId(), lastId)))) {
          target.setId(source.getId());
        } else {
          String _id = target.getId();
          boolean _notEquals_3 = (!Objects.equals(_id, lastId));
          if (_notEquals_3) {
            source.setId(target.getId());
          }
        }
        Elem2Elem.corrToId.put(corr, source.getId());
      } else {
        final Function1<pdb2.Person, Boolean> _function_2 = (pdb2.Person t) -> {
          String _name_1 = t.getName();
          return Boolean.valueOf(Objects.equals(_name_1, sourceKey));
        };
        target = IterableExtensions.<pdb2.Person>findFirst(unmatched, _function_2);
        if ((target != null)) {
          corr.setTargetElement(target);
          Elem2Elem.elementsToCorr.put(target, corr);
          unmatched.remove(target);
          target.setBirthday(source.getBirthday());
          target.setPlaceOfBirth(source.getPlaceOfBirth());
          target.setId(source.getId());
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getPerson());
          target = ((pdb2.Person) _orCreateTargetElem);
          target.setBirthday(source.getBirthday());
          target.setPlaceOfBirth(source.getPlaceOfBirth());
          target.setId(source.getId());
          target.setName(sourceKey);
          EObject _targetElement_1 = this.getCorrModelElem(source.eContainer()).getTargetElement();
          target.setDatabase(((Database) _targetElement_1));
        }
        Elem2Elem.corrToName.put(corr, sourceKey);
        Elem2Elem.corrToBirthday.put(corr, source.getBirthday());
        Elem2Elem.corrToPlaceOfBirth.put(corr, source.getPlaceOfBirth());
        Elem2Elem.corrToId.put(corr, source.getId());
      }
    };
    personList.forEach(_function_1);
    final Consumer<pdb2.Person> _function_2 = (pdb2.Person target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getPerson());
      final Person source = ((Person) _orCreateSourceElem);
      source.setBirthday(target.getBirthday());
      source.setPlaceOfBirth(target.getPlaceOfBirth());
      source.setId(target.getId());
      EObject _sourceElement = this.getCorrModelElem(target.eContainer()).getSourceElement();
      source.setDatabase(((pdb1.Database) _sourceElement));
      source.setFirstName(this.decision.getFirstName(target.getName()));
      source.setLastName(this.decision.getLastName(target.getName()));
      Elem2Elem.corrToName.put(corr, target.getName());
      Elem2Elem.corrToBirthday.put(corr, source.getBirthday());
      Elem2Elem.corrToPlaceOfBirth.put(corr, source.getPlaceOfBirth());
      Elem2Elem.corrToId.put(corr, source.getId());
    };
    unmatched.forEach(_function_2);
  }
}
