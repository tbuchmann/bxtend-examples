/**
 * Abstract rule that handles the core element-level bidirectional transformation between
 * {@link Families.FamilyMember} (source) and {@link Persons.Person} (target).
 * 
 * <p>A {@link Families.FamilyMember} represents a named member of a {@link Families.Family}
 * playing one of four roles: father, mother, son, or daughter.  On the Persons side, the
 * gender information is encoded by the concrete subtype:
 * {@link Persons.Male} (father/son) or {@link Persons.Female} (mother/daughter).
 * 
 * <p>Name encoding: person names are stored in the Persons model using the convention
 * {@code "<familyName>, <firstName>"}, e.g. {@code "Simpson, Bart"}.  The family name is
 * taken from the containing {@link Families.Family}.
 * 
 * <p>This class is abstract; concrete direction-specific rules are implemented in:
 * <ul>
 *   <li>{@link FatherSon2Male} – handles forward (Families → Persons) for males</li>
 *   <li>{@link MotherDaughter2Female} – handles forward for females</li>
 * </ul>
 * Both subclasses also cover the backward and synchronisation directions by delegating
 * to the generic methods defined here.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.FamiliesPackage;
import Families.Family;
import Families.FamilyMember;
import Families.FamilyRegister;
import Persons.Female;
import Persons.Male;
import Persons.Person;
import Persons.PersonRegister;
import Persons.PersonsPackage;
import com.google.common.collect.Iterables;
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr;
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function0;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

@SuppressWarnings("all")
public abstract class FamilyMember2Person extends Elem2Elem {
  /**
   * Constructs a new FamilyMember2Person rule.
   * 
   * @param src  the Families source model resource
   * @param trgt the Persons target model resource
   * @param corr the correspondence model resource
   * @param dec  the strategy for resolving backward-transformation decisions
   */
  public FamilyMember2Person(final Resource src, final Resource trgt, final Resource corr, final TargetToSourceDecision dec) {
    super(src, trgt, corr, dec);
  }

  /**
   * Returns an existing {@link Family} that should receive the backward-transformed
   * member, or creates and registers a new one when required by the decision strategy.
   * 
   * <p>The family name is extracted from the {@code person.name} string
   * ({@code "<familyName>, <firstName>"}). A list of candidate families with that name
   * is assembled from {@link Families2personsTransformation#familiesMap} and passed to
   * the {@link TargetToSourceDecision#getFamily} strategy.  If the strategy returns
   * {@code null} a new Family is created, added to {@code fregister}, and registered in
   * the families map.
   * 
   * @param sourceFamily the Family currently containing the FamilyMember, or
   *                     {@code null} if the member has not been placed yet
   * @param p            the Person object being transformed back
   * @param fregister    the FamilyRegister in which to create a new Family if needed
   * @return the selected or newly created Family
   */
  protected Family getOrCreateFamily(final Family sourceFamily, final Person p, final FamilyRegister fregister) {
    Family _xblockexpression = null;
    {
      final String familyname = p.getName().split(", ")[0];
      final ArrayList<Family> families = this.getFamilies(fregister, familyname);
      Family family = this.decision.getFamily(families, p, sourceFamily);
      if ((family == null)) {
        EObject _createSourceElement = this.createSourceElement(FamiliesPackage.eINSTANCE.getFamily());
        final Procedure1<Family> _function = (Family it) -> {
          it.setName(familyname);
        };
        Family _doubleArrow = ObjectExtensions.<Family>operator_doubleArrow(((Family) _createSourceElement), _function);
        family = _doubleArrow;
        EList<Family> _families = fregister.getFamilies();
        _families.add(family);
        this.decision.linkPersonToFamily(p, family);
        List<Family> _get = Families2personsTransformation.familiesMap.get(familyname);
        boolean _tripleEquals = (_get == null);
        if (_tripleEquals) {
          final List<Family> fams = CollectionLiterals.<Family>newArrayList();
          fams.add(family);
          Families2personsTransformation.familiesMap.put(familyname, fams);
        } else {
          List<Family> _get_1 = Families2personsTransformation.familiesMap.get(familyname);
          _get_1.add(family);
        }
      }
      _xblockexpression = family;
    }
    return _xblockexpression;
  }

  /**
   * Places {@code newMember} into the given {@code family}, choosing between the parent
   * role (father/mother) and child role (son/daughter) according to the decision strategy.
   * 
   * <p>If the decision strategy selects the parent role but the slot is already occupied,
   * the existing parent is demoted to the children list before the new member is installed.
   * 
   * @param p            the Person that is being mapped back; used by the decision strategy
   * @param family       the Family into which the member is being inserted
   * @param newMember    the FamilyMember element to insert
   * @param parentGetter lambda that retrieves the current occupant of the parent slot
   * @param parentSetter lambda that sets the parent slot to the given FamilyMember
   * @param childSetter  lambda that appends a FamilyMember to the children list
   */
  protected void addToFamily(final Person p, final Family family, final FamilyMember newMember, final Function0<? extends FamilyMember> parentGetter, final Procedure1<? super FamilyMember> parentSetter, final Procedure1<? super FamilyMember> childSetter) {
    boolean _setAsParent = this.decision.setAsParent(p, family);
    if (_setAsParent) {
      final FamilyMember parent = parentGetter.apply();
      if ((parent != null)) {
        childSetter.apply(parent);
      }
      parentSetter.apply(newMember);
    } else {
      childSetter.apply(newMember);
    }
  }

  /**
   * Retrieves or creates the {@link Person} element for the given name, ensuring the
   * concrete person type matches {@code personClass}.
   * 
   * <p>If the existing target element in {@code corrPerson} has the wrong type (e.g.
   * {@link Male} where {@link Female} is required), the old element is deleted and a
   * fresh one is created.  The birthday attribute is preserved across type changes if
   * possible; otherwise the metamodel default value is used.
   * 
   * @param name        the full person name in the form {@code "<familyName>, <firstName>"}
   * @param corrPerson  the correspondence element whose target slot is managed
   * @param personClass the required concrete EClass ({@code Male} or {@code Female})
   * @return the existing or newly created {@link Person} element with updated name
   */
  protected Person getOrCreatePersonElement(final String name, final Corr corrPerson, final EClass personClass) {
    Person _xblockexpression = null;
    {
      Date _elvis = null;
      EObject _targetElement = corrPerson.getTargetElement();
      Date _birthday = null;
      if (((Person) _targetElement)!=null) {
        _birthday=((Person) _targetElement).getBirthday();
      }
      if (_birthday != null) {
        _elvis = _birthday;
      } else {
        Object _defaultValue = PersonsPackage.eINSTANCE.getPerson_Birthday().getDefaultValue();
        _elvis = ((Date) _defaultValue);
      }
      final Date birthday = _elvis;
      if (((corrPerson.getTargetElement() != null) && (!Objects.equals(corrPerson.getTargetElement().eClass(), personClass)))) {
        EcoreUtil.delete(corrPerson.getTargetElement(), true);
        corrPerson.setTargetElement(null);
      }
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corrPerson, personClass);
      final Person person = ((Person) _orCreateTargetElem);
      person.setName(name);
      person.setBirthday(birthday);
      _xblockexpression = person;
    }
    return _xblockexpression;
  }

  /**
   * Returns a list of {@link Family} objects from the families map whose name equals
   * {@code familyname}.
   * 
   * <p>The number of candidates returned is controlled by
   * {@link TargetToSourceDecision#getFamilyListSize()}:
   * <ul>
   *   <li>{@code < 0}: all matching families are returned.</li>
   *   <li>{@code == 0}: an empty list is returned.</li>
   *   <li>{@code == 1}: only the first matching family (if any) is returned.</li>
   *   <li>{@code > 1}: up to {@code size} matching families are returned.</li>
   * </ul>
   * 
   * @param fregister  the FamilyRegister (currently used only for context; the actual
   *                   lookup is done via {@link Families2personsTransformation#familiesMap})
   * @param familyname the family name to search for
   * @return a (possibly empty) list of matching families
   */
  private ArrayList<Family> getFamilies(final FamilyRegister fregister, final String familyname) {
    ArrayList<Family> _xblockexpression = null;
    {
      final int size = this.decision.getFamilyListSize();
      final ArrayList<Family> families = CollectionLiterals.<Family>newArrayList();
      boolean _matched = false;
      if ((size < 0)) {
        _matched=true;
        List<Family> _get = Families2personsTransformation.familiesMap.get(familyname);
        boolean _tripleNotEquals = (_get != null);
        if (_tripleNotEquals) {
          List<Family> _get_1 = Families2personsTransformation.familiesMap.get(familyname);
          Iterables.<Family>addAll(families, _get_1);
        }
      }
      if (!_matched) {
        if ((size == 0)) {
          _matched=true;
          families.clear();
        }
      }
      if (!_matched) {
        if ((size == 1)) {
          _matched=true;
          List<Family> _get_2 = Families2personsTransformation.familiesMap.get(familyname);
          boolean _tripleNotEquals_1 = (_get_2 != null);
          if (_tripleNotEquals_1) {
            Family _first = Families2personsTransformation.familiesMap.get(familyname).getFirst();
            families.add(_first);
          }
        }
      }
      if (!_matched) {
        List<Family> _get_3 = Families2personsTransformation.familiesMap.get(familyname);
        boolean _tripleNotEquals_2 = (_get_3 != null);
        if (_tripleNotEquals_2) {
          final Consumer<Family> _function = (Family f) -> {
            families.add(f);
            int _size = families.size();
            boolean _greaterThan = (_size > size);
            if (_greaterThan) {
              return;
            }
          };
          Families2personsTransformation.familiesMap.get(familyname).forEach(_function);
        }
      }
      _xblockexpression = families;
    }
    return _xblockexpression;
  }

  /**
   * Backward-transformation method: transforms a {@link Person} element into a
   * {@link FamilyMember}, placing it in the correct Family and role.
   * 
   * <p>The family name and first name are extracted from {@code person.name}.
   * If the member is already contained in a family with the correct name, only
   * attribute updates are performed.  Otherwise the member is re-parented: an
   * appropriate family is located or created ({@link #getOrCreateFamily}), the member
   * is inserted into the correct slot ({@link #addToFamily}), and any now-empty source
   * family is optionally deleted according to the decision strategy.
   * 
   * @param corr   the correspondence element linking the FamilyMember and the Person
   * @param person the Person element to transform back into a FamilyMember
   */
  public String transformPerson(final Corr corr, final Person person) {
    String _xblockexpression = null;
    {
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, FamiliesPackage.eINSTANCE.getFamilyMember());
      final FamilyMember source = ((FamilyMember) _orCreateSourceElem);
      final String firstname = person.getName().split(", ")[1];
      final String familyname = person.getName().split(", ")[0];
      EObject _eContainer = source.eContainer();
      final Family sourceFamily = ((Family) _eContainer);
      EObject _sourceElement = this.getCorrModelElem(person.eContainer()).getSourceElement();
      final FamilyRegister fregister = ((FamilyRegister) _sourceElement);
      source.setName(firstname);
      if (((sourceFamily == null) || (!Objects.equals(sourceFamily.getName(), familyname)))) {
        final Family family = this.getOrCreateFamily(sourceFamily, person, fregister);
        if ((person instanceof Female)) {
          final Function0<FamilyMember> _function = () -> {
            return family.getMother();
          };
          final Procedure1<FamilyMember> _function_1 = (FamilyMember it) -> {
            family.setMother(it);
          };
          final Procedure1<FamilyMember> _function_2 = (FamilyMember it) -> {
            EList<FamilyMember> _daughters = family.getDaughters();
            _daughters.add(it);
          };
          this.addToFamily(person, family, source, _function, _function_1, _function_2);
        } else {
          final Function0<FamilyMember> _function_3 = () -> {
            return family.getFather();
          };
          final Procedure1<FamilyMember> _function_4 = (FamilyMember it) -> {
            family.setFather(it);
          };
          final Procedure1<FamilyMember> _function_5 = (FamilyMember it) -> {
            EList<FamilyMember> _sons = family.getSons();
            _sons.add(it);
          };
          this.addToFamily(person, family, source, _function_3, _function_4, _function_5);
        }
        if (((((((sourceFamily != null) && (sourceFamily.getFather() == null)) && (sourceFamily.getMother() == null)) && 
          sourceFamily.getSons().isEmpty()) && sourceFamily.getDaughters().isEmpty()) && 
          this.decision.deleteEmptyFamily(sourceFamily, source))) {
          EcoreUtil.delete(sourceFamily, true);
        }
      }
      _xblockexpression = Elem2Elem.corrToName.put(corr, person.getName());
    }
    return _xblockexpression;
  }

  /**
   * Forward-transformation helper: creates or updates a {@link Person} element for
   * the given {@link FamilyMember} and adds it to the {@link PersonRegister}.
   * 
   * <p>The person name is composed as {@code "<familyName>, <firstName>"}.
   * The concrete person type ({@link Male} or {@link Female}) is inferred from
   * {@code desc} ({@code "FatherSon2Male"} → {@link Male}, otherwise → {@link Female}).
   * 
   * @param member the FamilyMember to transform forward
   * @param desc   a rule identifier string used to determine gender and as the
   *               correspondence description
   */
  public String addPerson(final FamilyMember member, final String desc) {
    String _xblockexpression = null;
    {
      final Corr corrMale = this.getOrCreateCorrModelElement(member, desc);
      corrMale.setDesc(desc);
      EClass _xifexpression = null;
      boolean _equals = desc.equals("FatherSon2Male");
      if (_equals) {
        _xifexpression = PersonsPackage.eINSTANCE.getMale();
      } else {
        _xifexpression = PersonsPackage.eINSTANCE.getFemale();
      }
      EClass elem = _xifexpression;
      EObject _eContainer = member.eContainer();
      String _name = ((Family) _eContainer).getName();
      String _plus = (_name + ", ");
      String _name_1 = member.getName();
      String _plus_1 = (_plus + _name_1);
      final Person male = this.getOrCreatePersonElement(_plus_1, corrMale, elem);
      EObject _eContainer_1 = member.eContainer().eContainer();
      EObject _targetElement = this.getCorrModelElem(((FamilyRegister) _eContainer_1)).getTargetElement();
      ((PersonRegister) _targetElement).getPersons().add(male);
      _xblockexpression = Elem2Elem.corrToName.put(corrMale, male.getName());
    }
    return _xblockexpression;
  }

  /**
   * Synchronisation helper: reconciles a single {@link FamilyMember} against the
   * list of unmatched {@link Person} objects.
   * 
   * <p>Three cases are handled:
   * <ol>
   *   <li>The member already has a correspondence entry with a non-null target:
   *       if the name has changed on the Families side, the Person's name is updated;
   *       if the name is unchanged, the Person-side changes are propagated back via
   *       {@link #transformPerson}.</li>
   *   <li>The member has a correspondence entry but the target slot is empty: a
   *       matching Person is searched in {@code pList} by name; if found it is linked,
   *       otherwise a new Person is created.</li>
   *   <li>The member has no correspondence at all: same as case 2 after creating the
   *       correspondence entry.</li>
   * </ol>
   * Matched persons are removed from {@code pList} to avoid double-processing.
   * 
   * @param member the FamilyMember to reconcile
   * @param pList  the mutable list of Persons not yet matched to a FamilyMember
   * @param desc   the rule identifier ({@code "FatherSon2Male"} or other)
   */
  public Object synchFamilyMember(final FamilyMember member, final List<Person> pList, final String desc) {
    Object _xblockexpression = null;
    {
      EObject _eContainer = member.eContainer();
      String _name = ((Family) _eContainer).getName();
      String _plus = (_name + ", ");
      String _name_1 = member.getName();
      final String personName = (_plus + _name_1);
      Object _xifexpression = null;
      Corr _corrModelElem = this.getCorrModelElem(member);
      boolean _tripleNotEquals = (_corrModelElem != null);
      if (_tripleNotEquals) {
        String _xblockexpression_1 = null;
        {
          final EObject target = this.getCorrModelElem(member).getTargetElement();
          String _xifexpression_1 = null;
          if ((target != null)) {
            String _xblockexpression_2 = null;
            {
              pList.remove(target);
              String _xifexpression_2 = null;
              String _get = Elem2Elem.corrToName.get(this.getCorrModelElem(member));
              boolean _notEquals = (!Objects.equals(_get, personName));
              if (_notEquals) {
                ((Person) target).setName(personName);
              } else {
                _xifexpression_2 = this.transformPerson(this.getCorrModelElem(member), ((Person) target));
              }
              _xblockexpression_2 = _xifexpression_2;
            }
            _xifexpression_1 = _xblockexpression_2;
          }
          _xblockexpression_1 = _xifexpression_1;
        }
        _xifexpression = _xblockexpression_1;
      } else {
        boolean _xblockexpression_2 = false;
        {
          final Corr corr = this.getOrCreateCorrModelElement(member, desc);
          Person pers = this.findFirstMatchingPerson(pList, personName);
          boolean _xifexpression_1 = false;
          if ((pers != null)) {
            boolean _xblockexpression_3 = false;
            {
              corr.setTargetElement(pers);
              Elem2Elem.elementsToCorr.put(corr.getTargetElement(), corr);
              _xblockexpression_3 = pList.remove(pers);
            }
            _xifexpression_1 = _xblockexpression_3;
          } else {
            boolean _xblockexpression_4 = false;
            {
              boolean _equals = desc.equals("FatherSon2Male");
              if (_equals) {
                Person _orCreatePersonElement = this.getOrCreatePersonElement(personName, corr, PersonsPackage.eINSTANCE.getMale());
                pers = ((Male) _orCreatePersonElement);
              } else {
                Person _orCreatePersonElement_1 = this.getOrCreatePersonElement(personName, corr, PersonsPackage.eINSTANCE.getFemale());
                pers = ((Female) _orCreatePersonElement_1);
              }
              EObject _eContainer_1 = member.eContainer().eContainer();
              EObject _targetElement = this.getCorrModelElem(((FamilyRegister) _eContainer_1)).getTargetElement();
              _xblockexpression_4 = ((PersonRegister) _targetElement).getPersons().add(pers);
            }
            _xifexpression_1 = _xblockexpression_4;
          }
          _xblockexpression_2 = _xifexpression_1;
        }
        _xifexpression = Boolean.valueOf(_xblockexpression_2);
      }
      _xblockexpression = ((Object)_xifexpression);
    }
    return _xblockexpression;
  }

  /**
   * Returns the first {@link Person} in {@code persons} whose name equals {@code name}
   * and who does not yet have a correspondence entry.
   * 
   * @param persons the list of candidate Persons
   * @param name    the full name to match (format {@code "<familyName>, <firstName>"})
   * @return the first unmatched Person with the given name, or {@code null}
   */
  public Person findFirstMatchingPerson(final List<Person> persons, final String name) {
    final Function1<Person, Boolean> _function = (Person p) -> {
      return Boolean.valueOf((Objects.equals(p.getName(), name) && (this.getCorrModelElem(p) == null)));
    };
    return IterableExtensions.<Person>findFirst(persons, _function);
  }
}
