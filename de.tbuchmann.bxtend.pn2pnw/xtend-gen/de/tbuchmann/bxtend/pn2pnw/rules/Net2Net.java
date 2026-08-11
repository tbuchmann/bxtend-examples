package de.tbuchmann.bxtend.pn2pnw.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
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

  /**
   * Reconciles the root {@code Net} pair. Both models are single-root, so there is
   * normally at most one unmatched element per side.
   * 
   * <ol>
   *   <li>If already linked, push the name forward when it changed on the source since
   *       the last synchronisation ({@link #corrToName}), otherwise pull it backward.</li>
   *   <li>If unlinked, re-link to an unmatched same-named net, or create a new one.</li>
   *   <li>Any net still unmatched afterwards is used to create the missing counterpart
   *       (target-side insertion).</li>
   * </ol>
   */
  @Override
  public void synch() {
    final List<Net> netList = IteratorExtensions.<Net>toList(Iterators.<Net>filter(this.sourceModel.getAllContents(), Net.class));
    final Function1<pnw.Net, Boolean> _function = (pnw.Net n) -> {
      Corr _corrModelElem = this.getCorrModelElem(n);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<pnw.Net> unmatchedNets = IteratorExtensions.<pnw.Net>toList(IteratorExtensions.<pnw.Net>filter(Iterators.<pnw.Net>filter(this.targetModel.getAllContents(), pnw.Net.class), _function));
    final Consumer<Net> _function_1 = (Net n) -> {
      final Corr corr = this.getOrCreateCorrModelElement(n, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      pnw.Net target = ((pnw.Net) _targetElement);
      if ((target != null)) {
        unmatchedNets.remove(target);
        String _get = Elem2Elem.corrToName.get(corr);
        String _name = n.getName();
        boolean _notEquals = (!Objects.equals(_get, _name));
        if (_notEquals) {
          target.setName(n.getName());
        } else {
          n.setName(target.getName());
        }
      } else {
        final Function1<pnw.Net, Boolean> _function_2 = (pnw.Net t) -> {
          String _name_1 = t.getName();
          String _name_2 = n.getName();
          return Boolean.valueOf(Objects.equals(_name_1, _name_2));
        };
        target = IterableExtensions.<pnw.Net>findFirst(unmatchedNets, _function_2);
        if ((target != null)) {
          corr.setTargetElement(target);
          Elem2Elem.elementsToCorr.put(target, corr);
          unmatchedNets.remove(target);
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getNet());
          final Procedure1<pnw.Net> _function_3 = (pnw.Net it) -> {
            it.setName(n.getName());
          };
          pnw.Net _doubleArrow = ObjectExtensions.<pnw.Net>operator_doubleArrow(((pnw.Net) _orCreateTargetElem), _function_3);
          target = _doubleArrow;
          EList<EObject> _contents = this.targetModel.getContents();
          _contents.add(target);
        }
      }
      Elem2Elem.corrToName.put(corr, n.getName());
    };
    netList.forEach(_function_1);
    final Consumer<pnw.Net> _function_2 = (pnw.Net wn) -> {
      final Corr corr = this.getOrCreateCorrModelElement(wn, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getNet());
      final Procedure1<Net> _function_3 = (Net it) -> {
        it.setName(wn.getName());
      };
      final Net n = ObjectExtensions.<Net>operator_doubleArrow(((Net) _orCreateSourceElem), _function_3);
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(n);
      Elem2Elem.corrToName.put(corr, n.getName());
    };
    unmatchedNets.forEach(_function_2);
  }
}
