package de.tbuchmann.bxtend.pn2pnw.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
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
}
