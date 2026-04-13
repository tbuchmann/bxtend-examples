package de.tbuchmann.bxtend.pdb12pdb2.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import pdb1.Database;

/**
 * BXtend transformation rule that synchronises {@code pdb1.Database} elements with
 * {@code pdb2.Database} elements in both directions.
 * 
 * <p>This is the "root" rule of the PDB1 ↔ PDB2 transformation.  A Database element
 * exists in both metamodels with an identical structure (a single {@code name}
 * attribute and a containment reference to {@code Person} objects), so the rule is
 * symmetric: the only attribute propagated is the database {@code name}.</p>
 * 
 * <h3>Forward (PDB1 → PDB2)</h3>
 * <ol>
 *   <li>Iterates over every {@code pdb1.Database} in the source model.</li>
 *   <li>Looks up or creates a {@link de.tbuchmann.bxtend.pdb12pdb2.correspondence.pdb12pdb2.Corr}
 *       entry for the source database (keyed with {@code "Database2Database"}).</li>
 *   <li>Looks up or creates a matching {@code pdb2.Database} via the correspondence.</li>
 *   <li>Copies the {@code name} attribute from source to target.</li>
 *   <li>Adds the target database to the target model's root content list
 *       (idempotent because EMF ignores re-additions of already-contained objects).</li>
 * </ol>
 * 
 * <h3>Backward (PDB2 → PDB1)</h3>
 * <p>Mirror image of the forward direction: iterates over {@code pdb2.Database} elements
 * and propagates their {@code name} to the corresponding {@code pdb1.Database}.</p>
 */
@SuppressWarnings("all")
public class Database2Database extends Elem2Elem {
  /**
   * Creates a new {@code Database2Database} rule instance.
   * 
   * @param src  the PDB1 (source) EMF resource
   * @param trgt the PDB2 (target) EMF resource
   * @param corr the correspondence EMF resource
   */
  public Database2Database(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "Database2Database";
  }

  /**
   * Forward propagation: copies the {@code name} of every {@code pdb1.Database}
   * to its corresponding {@code pdb2.Database}, creating target elements and
   * correspondence entries as needed.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Database> _function = (Database source) -> {
      final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getDatabase());
      final pdb2.Database target = ((pdb2.Database) _orCreateTargetElem);
      target.setName(source.getName());
      EList<EObject> _contents = this.targetModel.getContents();
      _contents.add(target);
    };
    IteratorExtensions.<Database>forEach(Iterators.<Database>filter(this.sourceModel.getAllContents(), Database.class), _function);
  }

  /**
   * Backward propagation: copies the {@code name} of every {@code pdb2.Database}
   * to its corresponding {@code pdb1.Database}, creating source elements and
   * correspondence entries as needed.
   */
  @Override
  public void targetToSource() {
    final Procedure1<pdb2.Database> _function = (pdb2.Database target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getDatabase());
      final Database source = ((Database) _orCreateSourceElem);
      source.setName(target.getName());
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(source);
    };
    IteratorExtensions.<pdb2.Database>forEach(Iterators.<pdb2.Database>filter(this.targetModel.getAllContents(), pdb2.Database.class), _function);
  }
}
