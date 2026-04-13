package de.tbuchmann.bxtend.pn2pnw.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import pn.Net;

/**
 * Bidirectional transformation rule that synchronises the root {@code Net}
 * element between the unweighted Petri net ({@code pn}) and the weighted
 * Petri net ({@code pnw}).
 * 
 * <p>This is always the <em>first</em> rule executed during a transformation
 * pass, because places and transitions can only be added to a net that already
 * exists on both sides.</p>
 * 
 * <p><b>Correspondence:</b></p>
 * <pre>
 *   pn.Net  ←→  pnw.Net
 *   Synchronised attributes: {@code name}
 * </pre>
 * 
 * <p>The rule uses the rule identifier {@code "root"} to tag every
 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr Corr} it
 * creates, allowing the correspondence model to distinguish net-level entries
 * from element-level entries.</p>
 */
@SuppressWarnings("all")
public class Net2Net extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "root"}.
   * 
   * @param src   the source-model resource (unweighted Petri net)
   * @param trgt  the target-model resource (weighted Petri net)
   * @param corr  the correspondence-model resource
   */
  public Net2Net(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "root";
  }

  /**
   * Forward pass: for every {@code pn.Net} in the source model, finds or
   * creates the corresponding {@code pnw.Net} in the target model, and
   * synchronises the {@code name} attribute.
   * 
   * <p>The net object is added to the target resource's root-content list
   * (which is idempotent for EMF resources – adding an already-contained
   * object is a no-op).</p>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Net> _function = (Net n) -> {
      final Corr corr = this.getOrCreateCorrModelElement(n, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getNet());
      final pnw.Net targetNet = ((pnw.Net) _orCreateTargetElem);
      targetNet.setName(n.getName());
      EList<EObject> _contents = this.targetModel.getContents();
      _contents.add(targetNet);
    };
    IteratorExtensions.<Net>forEach(Iterators.<Net>filter(this.sourceModel.getAllContents(), Net.class), _function);
  }

  /**
   * Backward pass: for every {@code pnw.Net} in the target model, finds or
   * creates the corresponding {@code pn.Net} in the source model, and
   * synchronises the {@code name} attribute.
   * 
   * <p>The net object is added to the source resource's root-content list
   * (idempotent for EMF resources).</p>
   */
  @Override
  public void targetToSource() {
    final Procedure1<pnw.Net> _function = (pnw.Net wn) -> {
      final Corr corr = this.getOrCreateCorrModelElement(wn, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getNet());
      final Net sourceNet = ((Net) _orCreateSourceElem);
      sourceNet.setName(wn.getName());
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(sourceNet);
    };
    IteratorExtensions.<pnw.Net>forEach(Iterators.<pnw.Net>filter(this.targetModel.getAllContents(), pnw.Net.class), _function);
  }
}
