package de.tbuchmann.bxtend.gantt2cpm.rules;

import com.google.common.collect.Iterables;
import cpm.CPMNetwork;
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr;
import gantt.GanttDiagram;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Transformation rule that maps the root {@code gantt.GanttDiagram} container to
 * the root {@code cpm.CPMNetwork} container (and vice versa).
 * 
 * <p><b>Metamodel correspondence:</b></p>
 * <pre>
 *   gantt.GanttDiagram  ←→  cpm.CPMNetwork
 *     name                  name
 *     (root of source)      (root of target)
 * </pre>
 * 
 * <p><b>Role in the transformation chain:</b> This rule <em>must</em> execute
 * before {@link Activity2Activity} and {@link Dependency2Activity} so that the
 * {@code CPMNetwork} / {@code GanttDiagram} containers exist in the target /
 * source model and in the correspondence map before any child elements try to
 * look them up.  The rule is therefore always added first to the rule list in
 * {@link Gantt2cpmTransformation}.</p>
 * 
 * <p><b>Cardinality assumption:</b> Exactly one {@code GanttDiagram} / one
 * {@code CPMNetwork} is present in the respective model resource (accessed via
 * {@code contents.get(0)}).  Multi-root models are not supported by this rule.</p>
 * 
 * <p><b>Rule identifier:</b> {@code "root"} – stored in the {@link Corr}
 * correspondence entry created for the diagram/network pair and used to
 * distinguish the root correspondence from element-level correspondences during
 * deletion detection.</p>
 */
@SuppressWarnings("all")
public class Diagram2Network extends Elem2Elem {
  /**
   * Constructs the rule and registers it under the rule identifier {@code "root"}.
   * 
   * @param src  EMF resource holding the source (Gantt) model
   * @param trgt EMF resource holding the target (CPM) model
   * @param corr EMF resource holding the correspondence model
   */
  public Diagram2Network(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "root";
  }

  /**
   * Propagates the {@code GanttDiagram} root element to the CPM target model
   * (forward direction: Gantt → CPM).
   * 
   * <p>Steps:</p>
   * <ol>
   *   <li>Retrieve the single {@link GanttDiagram} from the source resource.</li>
   *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
   *       for the diagram (keyed by rule ID {@code "root"}).</li>
   *   <li>Look up or create the corresponding {@link CPMNetwork}; copy {@code name}.</li>
   *   <li>Ensure the network is added to the target resource's content root.</li>
   * </ol>
   */
  @Override
  public void sourceToTarget() {
    EObject _get = this.sourceModel.getContents().get(0);
    final GanttDiagram diag = ((GanttDiagram) _get);
    final Corr corr = this.getOrCreateCorrModelElement(diag, this.ruleID);
    EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getCPMNetwork());
    final Procedure1<CPMNetwork> _function = (CPMNetwork it) -> {
      it.setName(diag.getName());
    };
    CPMNetwork net = ObjectExtensions.<CPMNetwork>operator_doubleArrow(((CPMNetwork) _orCreateTargetElem), _function);
    EList<EObject> _contents = this.targetModel.getContents();
    _contents.add(net);
    Elem2Elem.corrToName.put(corr, diag.getName());
  }

  /**
   * Propagates the {@code CPMNetwork} root element back to the Gantt source model
   * (backward direction: CPM → Gantt).
   * 
   * <p>Steps:</p>
   * <ol>
   *   <li>Retrieve the single {@link CPMNetwork} from the target resource.</li>
   *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
   *       for the network.</li>
   *   <li>Look up or create the corresponding {@link GanttDiagram}; copy {@code name}.</li>
   *   <li>Ensure the diagram is added to the source resource's content root.</li>
   * </ol>
   */
  @Override
  public void targetToSource() {
    EObject _get = this.targetModel.getContents().get(0);
    final CPMNetwork net = ((CPMNetwork) _get);
    final Corr corr = this.getOrCreateCorrModelElement(net, this.ruleID);
    EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getGanttDiagram());
    final Procedure1<GanttDiagram> _function = (GanttDiagram it) -> {
      it.setName(net.getName());
    };
    GanttDiagram diag = ObjectExtensions.<GanttDiagram>operator_doubleArrow(((GanttDiagram) _orCreateSourceElem), _function);
    EList<EObject> _contents = this.sourceModel.getContents();
    _contents.add(diag);
    Elem2Elem.corrToName.put(corr, diag.getName());
  }

  /**
   * Reconciles the root {@code GanttDiagram} ↔ {@code CPMNetwork} pair.
   * 
   * <p>Both models are single-root, so there is normally at most one unmatched
   * element per side (e.g. after loading two previously unrelated models for
   * the first time, or after a delete/recreate cycle). Algorithm:</p>
   * <ol>
   *   <li>If the diagram is already linked, either push its (changed) name to
   *       the network or pull the network's name back into the diagram,
   *       depending on which side changed since the last synchronisation
   *       (tracked via {@link #corrToName}).</li>
   *   <li>If unlinked, try to re-link to an unmatched network with the same
   *       name; otherwise create a new network from the diagram.</li>
   *   <li>Any network still unmatched afterwards is used to create a new
   *       diagram (target-side insertion).</li>
   * </ol>
   */
  @Override
  public void synch() {
    final List<GanttDiagram> diagList = IterableExtensions.<GanttDiagram>toList(Iterables.<GanttDiagram>filter(this.sourceModel.getContents(), GanttDiagram.class));
    final Function1<CPMNetwork, Boolean> _function = (CPMNetwork n) -> {
      Corr _corrModelElem = this.getCorrModelElem(n);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<CPMNetwork> unmatchedNets = IterableExtensions.<CPMNetwork>toList(IterableExtensions.<CPMNetwork>filter(Iterables.<CPMNetwork>filter(this.targetModel.getContents(), CPMNetwork.class), _function));
    final Consumer<GanttDiagram> _function_1 = (GanttDiagram diag) -> {
      final Corr corr = this.getOrCreateCorrModelElement(diag, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      CPMNetwork net = ((CPMNetwork) _targetElement);
      if ((net != null)) {
        unmatchedNets.remove(net);
        String _get = Elem2Elem.corrToName.get(corr);
        String _name = diag.getName();
        boolean _notEquals = (!Objects.equals(_get, _name));
        if (_notEquals) {
          net.setName(diag.getName());
        } else {
          diag.setName(net.getName());
        }
      } else {
        final Function1<CPMNetwork, Boolean> _function_2 = (CPMNetwork n) -> {
          String _name_1 = n.getName();
          String _name_2 = diag.getName();
          return Boolean.valueOf(Objects.equals(_name_1, _name_2));
        };
        net = IterableExtensions.<CPMNetwork>findFirst(unmatchedNets, _function_2);
        if ((net != null)) {
          corr.setTargetElement(net);
          Elem2Elem.elementsToCorr.put(net, corr);
          unmatchedNets.remove(net);
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getCPMNetwork());
          final Procedure1<CPMNetwork> _function_3 = (CPMNetwork it) -> {
            it.setName(diag.getName());
          };
          CPMNetwork _doubleArrow = ObjectExtensions.<CPMNetwork>operator_doubleArrow(((CPMNetwork) _orCreateTargetElem), _function_3);
          net = _doubleArrow;
          boolean _contains = this.targetModel.getContents().contains(net);
          boolean _not = (!_contains);
          if (_not) {
            EList<EObject> _contents = this.targetModel.getContents();
            _contents.add(net);
          }
        }
      }
      Elem2Elem.corrToName.put(corr, diag.getName());
    };
    diagList.forEach(_function_1);
    final Consumer<CPMNetwork> _function_2 = (CPMNetwork net) -> {
      final Corr corr = this.getOrCreateCorrModelElement(net, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getGanttDiagram());
      final Procedure1<GanttDiagram> _function_3 = (GanttDiagram it) -> {
        it.setName(net.getName());
      };
      final GanttDiagram diag = ObjectExtensions.<GanttDiagram>operator_doubleArrow(((GanttDiagram) _orCreateSourceElem), _function_3);
      boolean _contains = this.sourceModel.getContents().contains(diag);
      boolean _not = (!_contains);
      if (_not) {
        EList<EObject> _contents = this.sourceModel.getContents();
        _contents.add(diag);
      }
      Elem2Elem.corrToName.put(corr, diag.getName());
    };
    unmatchedNets.forEach(_function_2);
  }
}
