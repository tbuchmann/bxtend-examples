package de.tbuchmann.bxtend.gantt2cpm.rules;

import cpm.CPMNetwork;
import gantt.GanttDiagram;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
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
    EObject _orCreateTargetElem = this.getOrCreateTargetElem(this.getOrCreateCorrModelElement(diag, this.ruleID), this.targetPackage.getCPMNetwork());
    final Procedure1<CPMNetwork> _function = (CPMNetwork it) -> {
      it.setName(diag.getName());
    };
    CPMNetwork net = ObjectExtensions.<CPMNetwork>operator_doubleArrow(((CPMNetwork) _orCreateTargetElem), _function);
    EList<EObject> _contents = this.targetModel.getContents();
    _contents.add(net);
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
    EObject _orCreateSourceElem = this.getOrCreateSourceElem(this.getOrCreateCorrModelElement(net, this.ruleID), this.sourcePackage.getGanttDiagram());
    final Procedure1<GanttDiagram> _function = (GanttDiagram it) -> {
      it.setName(net.getName());
    };
    GanttDiagram diag = ObjectExtensions.<GanttDiagram>operator_doubleArrow(((GanttDiagram) _orCreateSourceElem), _function);
    EList<EObject> _contents = this.sourceModel.getContents();
    _contents.add(diag);
  }
}
