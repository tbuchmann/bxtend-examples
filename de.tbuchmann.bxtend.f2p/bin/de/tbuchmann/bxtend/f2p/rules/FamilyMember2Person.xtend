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
package de.tbuchmann.bxtend.f2p.rules

import Families.FamiliesPackage
import Families.Family
import Families.FamilyMember
import Families.FamilyRegister
import Persons.Female
import Persons.Male
import Persons.Person
import Persons.PersonRegister
import Persons.PersonsPackage
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import java.util.Date
import java.util.List
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil

abstract class FamilyMember2Person extends Elem2Elem {

	/**
	 * Constructs a new FamilyMember2Person rule.
	 *
	 * @param src  the Families source model resource
	 * @param trgt the Persons target model resource
	 * @param corr the correspondence model resource
	 * @param dec  the strategy for resolving backward-transformation decisions
	 */
	new(Resource src, Resource trgt, Resource corr, TargetToSourceDecision dec) {
		super(src, trgt, corr, dec)
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
	def protected getOrCreateFamily(Family sourceFamily, Person p, FamilyRegister fregister) {
		val familyname = p.name.split(", ").get(0)
		val families = fregister.getFamilies(familyname)
		var family = decision.getFamily(families, p, sourceFamily)
		if (family === null) {
			family = createSourceElement(FamiliesPackage.eINSTANCE.family) as Family => [name = familyname]
			fregister.families += family
			decision.linkPersonToFamily(p, family)
			// put new Family in HashMap
			if (Families2personsTransformation.familiesMap.get(familyname) === null) {
				val List<Family> fams = newArrayList
				fams += family
				Families2personsTransformation.familiesMap.put(familyname, fams)
			} else {
				Families2personsTransformation.familiesMap.get(familyname) += family
			}
		}
		family
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
	def protected void addToFamily(Person p, Family family, FamilyMember newMember, ()=>FamilyMember parentGetter,
		(FamilyMember)=>void parentSetter, (FamilyMember)=>void childSetter) {
		if (decision.setAsParent(p, family)) {
			val parent = parentGetter.apply()
			if (parent !== null) {
				childSetter.apply(parent)
			}
			parentSetter.apply(newMember)
		} else {
			childSetter.apply(newMember)
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
	def protected getOrCreatePersonElement(String name, Corr corrPerson, EClass personClass) {
		val birthday = ((corrPerson.targetElement as Person)?.birthday) ?:
			PersonsPackage.eINSTANCE.person_Birthday.defaultValue as Date

		if (corrPerson.targetElement !== null && corrPerson.targetElement.eClass != personClass) {
			EcoreUtil.delete(corrPerson.targetElement, true)
			corrPerson.targetElement = null
		}
		val person = corrPerson.getOrCreateTargetElem(personClass) as Person
		person.name = name
		person.birthday = birthday
		person
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
	def private getFamilies(FamilyRegister fregister, String familyname) {

		val size = decision.getFamilyListSize
		val families = newArrayList
		switch size {
			case size < 0:
				if (Families2personsTransformation.familiesMap.get(familyname) !== null)
					families += Families2personsTransformation.familiesMap.get(familyname)//fregister.families.filter[name == familyname]
			case size == 0:
				families.clear
			case size == 1:
				if (Families2personsTransformation.familiesMap.get(familyname) !== null)
					families += Families2personsTransformation.familiesMap.get(familyname).first//fregister.families.findFirst[name == familyname]
			default:
				//fregister.families.forEach [ f |
				if (Families2personsTransformation.familiesMap.get(familyname) !== null)
				Families2personsTransformation.familiesMap.get(familyname).forEach[ f | 
					//if (f.name == familyname)
						families += f
					if (families.size > size)
						return
				]
		}
		families

		//familiesMap.get(familyname)
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
	def transformPerson(Corr corr, Person person) {		
		val source = corr.getOrCreateSourceElem(FamiliesPackage.eINSTANCE.familyMember) as FamilyMember
		val firstname = person.name.split(", ").get(1)
		val familyname = person.name.split(", ").get(0)
		val sourceFamily = source.eContainer() as Family
		val fregister = person.eContainer().getCorrModelElem().sourceElement as FamilyRegister
	
		source.name = firstname
	
		if (sourceFamily === null || sourceFamily.name != familyname) {
			
			val family = getOrCreateFamily(sourceFamily, person, fregister)
			if (person instanceof Female)
				person.addToFamily(family, source, [family.mother], [family.mother = it], [family.daughters += it])
			else
				person.addToFamily(family, source, [family.father], [family.father = it], [family.sons += it])
	
			if (sourceFamily !== null && sourceFamily.father === null && sourceFamily.mother === null &&
				sourceFamily.sons.empty && sourceFamily.daughters.empty &&
				decision.deleteEmptyFamily(sourceFamily, source)) {
				EcoreUtil.delete(sourceFamily, true)
			}
		}
		corrToName.put(corr, person.name)
		
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
	def addPerson(FamilyMember member, String desc) {
		val corrMale = member.getOrCreateCorrModelElement(desc)
		corrMale.desc = desc;
		var elem = (desc.equals("FatherSon2Male") ? PersonsPackage.eINSTANCE.male : PersonsPackage.eINSTANCE.female);
		val male = getOrCreatePersonElement((member.eContainer as Family).name + ", " + member.name, corrMale, elem)
		((member.eContainer.eContainer as FamilyRegister).corrModelElem.targetElement as PersonRegister).getPersons().add(male)
		corrToName.put(corrMale, male.name)
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
	def synchFamilyMember(FamilyMember member, List<Person> pList, String desc) {
		val personName = (member.eContainer as Family).name + ", " + member.name
			if (member.corrModelElem !== null) {
				val target = member.corrModelElem.targetElement
				if (target !== null) {
					pList.remove(target)
					if (corrToName.get(member.corrModelElem) != personName)
						(target as Person).name = personName
					else {
						//member.name = (target as Person).name.split(", ").get(1)
						member.corrModelElem.transformPerson(target as Person)	
					}
				}
			} else {
				val corr = member.getOrCreateCorrModelElement(desc) //"FatherSon2Male")		
				var pers = findFirstMatchingPerson(pList, personName)
				if (pers !== null) {
					corr.targetElement = pers
					elementsToCorr.put(corr.targetElement, corr)
					pList.remove(pers)
				}								
				else {// if no match is possible, create a new person element
					if (desc.equals("FatherSon2Male")) 
						pers = getOrCreatePersonElement( personName, corr, PersonsPackage.eINSTANCE.male) as Male
					else
						pers = getOrCreatePersonElement( personName, corr, PersonsPackage.eINSTANCE.female) as Female
					((member.eContainer.eContainer as FamilyRegister).corrModelElem.targetElement as PersonRegister).getPersons().add(pers)					
				}
			}
	}
	
	/**
	 * Returns the first {@link Person} in {@code persons} whose name equals {@code name}
	 * and who does not yet have a correspondence entry.
	 *
	 * @param persons the list of candidate Persons
	 * @param name    the full name to match (format {@code "<familyName>, <firstName>"})
	 * @return the first unmatched Person with the given name, or {@code null}
	 */
	def findFirstMatchingPerson(List<Person> persons, String name) {
		persons.findFirst[p |
			p.name == name && p.corrModelElem === null
		]
	}
}