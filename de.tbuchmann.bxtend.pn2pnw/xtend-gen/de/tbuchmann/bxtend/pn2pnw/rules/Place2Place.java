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
import pn.Place;
import pnw.Net;
import pnw.NetElement;

/**
 * Bidirectional transformation rule that synchronises {@code Place} elements
 * between the unweighted Petri net ({@code pn}) and the weighted Petri net
 * ({@code pnw}).
 * 
 * <p><b>Correspondence:</b></p>
 * <pre>
 *   pn.Place  ←→  pnw.Place
 *   Synchronised attributes: {@code name}, {@code noOfTokens}
 * </pre>
 * 
 * <p>Place containment is maintained via the owning {@code Net}: every place
 * is added to the {@code elements} list of the net that corresponds to its
 * own source/target net (looked up through the shared correspondence map).
 * Therefore this rule must be executed <em>after</em> {@link Net2Net}.</p>
 * 
 * <p>Arc connectivity (edges to/from transitions) is handled separately by
 * {@link Transition2Transition}.</p>
 * 
 * <p>The rule uses the rule identifier {@code "place"} to tag every
 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr Corr} it
 * creates.</p>
 */
@SuppressWarnings("all")
public class Place2Place extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "place"}.
   * 
   * @param src   the source-model resource (unweighted Petri net)
   * @param trgt  the target-model resource (weighted Petri net)
   * @param corr  the correspondence-model resource
   */
  public Place2Place(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "place";
  }

  /**
   * Forward pass: for every {@code pn.Place} in the source model, finds or
   * creates the corresponding {@code pnw.Place} in the target model,
   * synchronises {@code name} and {@code noOfTokens}, and adds the place
   * to the elements list of the corresponding target {@link Net}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Place> _function = (Place p) -> {
      final Corr corr = this.getOrCreateCorrModelElement(p, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getPlace());
      final Procedure1<pnw.Place> _function_1 = (pnw.Place it) -> {
        it.setName(p.getName());
        it.setNoOfTokens(p.getNoOfTokens());
      };
      final pnw.Place targetPlace = ObjectExtensions.<pnw.Place>operator_doubleArrow(((pnw.Place) _orCreateTargetElem), _function_1);
      EObject _targetElement = this.getCorrModelElem(p.getNet()).getTargetElement();
      EList<NetElement> _elements = ((Net) _targetElement).getElements();
      _elements.add(targetPlace);
    };
    IteratorExtensions.<Place>forEach(Iterators.<Place>filter(this.sourceModel.getAllContents(), Place.class), _function);
  }

  /**
   * Backward pass: for every {@code pnw.Place} in the target model, finds or
   * creates the corresponding {@code pn.Place} in the source model,
   * synchronises {@code name} and {@code noOfTokens}, and adds the place
   * to the elements list of the corresponding source {@link pn.Net}.
   */
  @Override
  public void targetToSource() {
    final Procedure1<pnw.Place> _function = (pnw.Place p) -> {
      final Corr corr = this.getOrCreateCorrModelElement(p, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getPlace());
      final Procedure1<Place> _function_1 = (Place it) -> {
        it.setName(p.getName());
        it.setNoOfTokens(p.getNoOfTokens());
      };
      final Place sourcePlace = ObjectExtensions.<Place>operator_doubleArrow(((Place) _orCreateSourceElem), _function_1);
      EObject _sourceElement = this.getCorrModelElem(p.getNet()).getSourceElement();
      EList<pn.NetElement> _elements = ((pn.Net) _sourceElement).getElements();
      _elements.add(sourcePlace);
    };
    IteratorExtensions.<pnw.Place>forEach(Iterators.<pnw.Place>filter(this.targetModel.getAllContents(), pnw.Place.class), _function);
  }

  /**
   * Reconciles concurrent edits to {@code Place} pairs.
   * 
   * <p>{@code name} (the identity/matching key) and {@code noOfTokens} (independent of the
   * key) are resolved separately: {@code name} follows the same push-forward-on-change /
   * pull-backward-otherwise logic as {@link Net2Net#synch()} (using {@link #corrToName});
   * {@code noOfTokens} is compared on <em>both</em> sides against the last-known snapshot
   * ({@link #corrToTokens}) so a source-only or target-only edit is never silently
   * discarded, and a genuine conflict (both changed) lets the source win.</p>
   */
  @Override
  public void synch() {
    final List<Place> placeList = IteratorExtensions.<Place>toList(Iterators.<Place>filter(this.sourceModel.getAllContents(), Place.class));
    final Function1<pnw.Place, Boolean> _function = (pnw.Place p) -> {
      Corr _corrModelElem = this.getCorrModelElem(p);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<pnw.Place> unmatched = IteratorExtensions.<pnw.Place>toList(IteratorExtensions.<pnw.Place>filter(Iterators.<pnw.Place>filter(this.targetModel.getAllContents(), pnw.Place.class), _function));
    final Consumer<Place> _function_1 = (Place p) -> {
      final Corr corr = this.getOrCreateCorrModelElement(p, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      pnw.Place target = ((pnw.Place) _targetElement);
      if ((target != null)) {
        unmatched.remove(target);
        String _get = Elem2Elem.corrToName.get(corr);
        String _name = p.getName();
        boolean _notEquals = (!Objects.equals(_get, _name));
        if (_notEquals) {
          target.setName(p.getName());
        } else {
          p.setName(target.getName());
        }
        final Integer lastTokens = Elem2Elem.corrToTokens.get(corr);
        final boolean sourceChanged = ((lastTokens == null) || ((lastTokens).intValue() != p.getNoOfTokens()));
        final boolean targetChanged = ((lastTokens == null) || ((lastTokens).intValue() != target.getNoOfTokens()));
        if (sourceChanged) {
          target.setNoOfTokens(p.getNoOfTokens());
        } else {
          if (targetChanged) {
            p.setNoOfTokens(target.getNoOfTokens());
          }
        }
      } else {
        final Function1<pnw.Place, Boolean> _function_2 = (pnw.Place t) -> {
          String _name_1 = t.getName();
          String _name_2 = p.getName();
          return Boolean.valueOf(Objects.equals(_name_1, _name_2));
        };
        target = IterableExtensions.<pnw.Place>findFirst(unmatched, _function_2);
        if ((target != null)) {
          corr.setTargetElement(target);
          Elem2Elem.elementsToCorr.put(target, corr);
          unmatched.remove(target);
          target.setNoOfTokens(p.getNoOfTokens());
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getPlace());
          final Procedure1<pnw.Place> _function_3 = (pnw.Place it) -> {
            it.setName(p.getName());
            it.setNoOfTokens(p.getNoOfTokens());
          };
          pnw.Place _doubleArrow = ObjectExtensions.<pnw.Place>operator_doubleArrow(((pnw.Place) _orCreateTargetElem), _function_3);
          target = _doubleArrow;
          EObject _targetElement_1 = this.getCorrModelElem(p.getNet()).getTargetElement();
          EList<NetElement> _elements = ((Net) _targetElement_1).getElements();
          _elements.add(target);
        }
      }
      Elem2Elem.corrToName.put(corr, p.getName());
      Elem2Elem.corrToTokens.put(corr, Integer.valueOf(target.getNoOfTokens()));
    };
    placeList.forEach(_function_1);
    final Consumer<pnw.Place> _function_2 = (pnw.Place wp) -> {
      final Corr corr = this.getOrCreateCorrModelElement(wp, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getPlace());
      final Procedure1<Place> _function_3 = (Place it) -> {
        it.setName(wp.getName());
        it.setNoOfTokens(wp.getNoOfTokens());
      };
      final Place sp = ObjectExtensions.<Place>operator_doubleArrow(((Place) _orCreateSourceElem), _function_3);
      EObject _sourceElement = this.getCorrModelElem(wp.getNet()).getSourceElement();
      EList<pn.NetElement> _elements = ((pn.Net) _sourceElement).getElements();
      _elements.add(sp);
      Elem2Elem.corrToName.put(corr, sp.getName());
      Elem2Elem.corrToTokens.put(corr, Integer.valueOf(sp.getNoOfTokens()));
    };
    unmatched.forEach(_function_2);
  }
}
