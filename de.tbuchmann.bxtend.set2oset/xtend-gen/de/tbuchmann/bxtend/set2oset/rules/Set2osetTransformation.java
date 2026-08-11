package de.tbuchmann.bxtend.set2oset.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr;
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Set2osetFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import osets.Element;

/**
 * Orchestrator for the Set-to-OSet bidirectional transformation implemented with the
 * <em>BXtend</em> framework.
 * 
 * <p>This class wires together the two EMF models (source and target) with a third
 * <em>correspondence model</em>, instantiates and orders the transformation rules, and
 * exposes the two top-level entry points {@link #sourceToTarget()} and
 * {@link #targetToSource()} that the Benchmarx tool adapter calls.</p>
 * 
 * <h2>Architecture overview</h2>
 * <pre>
 *  ┌──────────────┐   sourceToTarget()   ┌─────────────────┐
 *  │  sets.MySet  │ ──────────────────►  │ osets.MyOrderedSet│
 *  │  (source)    │ ◄──────────────────  │    (target)      │
 *  └──────────────┘   targetToSource()   └─────────────────┘
 *         │                                      │
 *         └──────────────────┬───────────────────┘
 *                            │
 *                    ┌───────▼────────┐
 *                    │  Correspondence│
 *                    │    Model       │
 *                    │ (Transformation│
 *                    │  + Corr list)  │
 *                    └────────────────┘
 * </pre>
 * 
 * <h2>Rule execution order</h2>
 * <p>Rules are executed in the order in which they appear in the {@code rules} list.  The
 * container rule must run first so that the correspondence entries for {@code MySet} /
 * {@code MyOrderedSet} are in place when the element rule navigates to them via
 * {@code eContainer.corrModelElem}:</p>
 * <ol>
 *   <li>{@link MySet2MyOrderedSet} – synchronises the root containers</li>
 *   <li>{@link Element2Element} – synchronises contained elements and maintains the
 *       doubly-linked list in {@code MyOrderedSet}</li>
 * </ol>
 * 
 * <h2>Deletion handling (generated code modification)</h2>
 * <p>BXtend's generated deletion helpers ({@link #deleteUnreferencedTargetElements()} and
 * {@link #deleteUnreferencedSourceElements()}) were <strong>manually extended</strong> beyond
 * the generated scaffold to correctly handle the doubly-linked-list invariant maintained by
 * {@code osets.Element}.</p>
 * 
 * <p>When a source element is deleted its corresponding target {@code osets.Element} must be
 * removed from the linked list before it is physically deleted from the model.  The generated
 * code would leave the list broken (the predecessor's {@code next} pointer would dangle) if
 * this re-linking step were omitted.  The fix repairs the list by patching the predecessor's
 * {@code next} reference to skip over the element being deleted:</p>
 * <pre>
 *   … ←→ prev ←→ toDelete ←→ next ←→ …
 *   becomes after deletion:
 *   … ←→ prev ←→ next ←→ …
 * </pre>
 * <p>The complementary {@code next → previous} re-link is handled automatically by EMF because
 * {@code next} and {@code previous} are declared as an <em>eOpposite</em> pair in
 * {@code OrderedSets.ecore}: setting {@code prev.next = toDelete.next} automatically updates
 * {@code toDelete.next.previous = prev}.</p>
 * 
 * <p>No analogous re-linking is needed in {@link #deleteUnreferencedSourceElements()} because
 * the source metamodel ({@code Sets.ecore}) has no ordering structure.</p>
 * 
 * <h2>Correspondence model initialisation</h2>
 * <p>Both constructors guarantee that the correspondence resource always contains a root
 * {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation} object.  If the
 * resource is freshly created (empty), a new root is added; otherwise the existing root (and
 * all its {@link Corr} links) is reused for incremental synchronisation.</p>
 */
@SuppressWarnings("all")
public class Set2osetTransformation {
  /**
   * EMF resource holding the source ({@code MySet}) model.
   */
  private Resource sourceModel;

  /**
   * EMF resource holding the target ({@code MyOrderedSet}) model.
   */
  private Resource targetModel;

  /**
   * EMF resource holding the correspondence model (root: {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation}).
   */
  private Resource corrModel;

  /**
   * Ordered list of rules to be applied during a synchronisation step.
   * The list is populated by {@link #addRules()} and must not be modified afterwards.
   */
  private List<Elem2Elem> rules = new ArrayList<Elem2Elem>();

  /**
   * Constructs the transformation from three EMF {@link URI}s, loading the corresponding
   * resources from the default {@link ResourceSetImpl}.
   * 
   * <p>If the correspondence resource is empty (first run) a fresh
   * {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation} root is
   * created so that rules can immediately add {@link Corr} entries.</p>
   * 
   * @param source       URI of the source model XMI file
   * @param target       URI of the target model XMI file
   * @param correspondence URI of the correspondence model XMI file
   */
  public Set2osetTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Set2osetFactory.eINSTANCE.createTransformation());
    }
    this.addRules();
  }

  /**
   * Constructs the transformation from three already-loaded EMF {@link Resource}s.
   * 
   * <p>This constructor is used by the Benchmarx tool adapter
   * ({@code BXtendSet2Oset}) which manages its own {@link ResourceSet} and passes the
   * pre-loaded resources directly.</p>
   * 
   * <p>If the correspondence resource is empty a fresh root is created as in the URI-based
   * constructor.</p>
   * 
   * @param source        the EMF resource containing the source model
   * @param target        the EMF resource containing the target model
   * @param correspondence the EMF resource containing the correspondence model
   */
  public Set2osetTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Set2osetFactory.eINSTANCE.createTransformation());
    }
    this.addRules();
  }

  /**
   * Registers the transformation rules in their required execution order:
   * <ol>
   *   <li>{@link MySet2MyOrderedSet} – must run first to establish container correspondences</li>
   *   <li>{@link Element2Element} – relies on the container correspondences being present</li>
   * </ol>
   */
  public boolean addRules() {
    boolean _xblockexpression = false;
    {
      MySet2MyOrderedSet _mySet2MyOrderedSet = new MySet2MyOrderedSet(this.sourceModel, this.targetModel, this.corrModel);
      this.rules.add(_mySet2MyOrderedSet);
      Element2Element _element2Element = new Element2Element(this.sourceModel, this.targetModel, this.corrModel);
      _xblockexpression = this.rules.add(_element2Element);
    }
    return _xblockexpression;
  }

  /**
   * Forward propagation entry point: propagates source-side changes to the target model.
   * 
   * <p>Each rule in {@link #rules} is executed in order.  After the rules have run,
   * {@link #deleteUnreferencedTargetElements()} removes any target elements whose
   * corresponding source element has been deleted (i.e. whose {@link Corr} now has a
   * {@code null} {@code sourceElement}).</p>
   * 
   * <p>If the source model is empty the method is a no-op (guards against a completely
   * uninitialised source resource).</p>
   */
  public void sourceToTarget() {
    int _size = this.sourceModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rules) {
        e.sourceToTarget();
      }
    }
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Backward propagation entry point: propagates target-side changes to the source model.
   * 
   * <p>Each rule in {@link #rules} is executed in order.  After the rules have run,
   * {@link #deleteUnreferencedSourceElements()} removes any source elements whose
   * corresponding target element has been deleted (i.e. whose {@link Corr} now has a
   * {@code null} {@code targetElement}).</p>
   * 
   * <p>If the target model is empty the method is a no-op.</p>
   */
  public void targetToSource() {
    int _size = this.targetModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rules) {
        e.targetToSource();
      }
    }
    this.deleteUnreferencedSourceElements();
  }

  /**
   * Synchronisation entry point: reconciles concurrent edits made to both the source and
   * target models since the last synchronisation point.
   * 
   * <p>Executes each rule's {@link Elem2Elem#synch()} in the same registration order as
   * {@link #sourceToTarget()}/{@link #targetToSource()}, then cleans up dangling
   * correspondences on both sides (reusing the same manually-patched deletion helpers that
   * repair the {@code osets.Element} doubly-linked list).</p>
   */
  public void synch() {
    for (final Elem2Elem e : this.rules) {
      e.synch();
    }
    this.deleteUnreferencedSourceElements();
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Checks whether the current source and target models are mutually consistent according
   * to the correspondence model.
   * 
   * @return {@code true} always (placeholder – full consistency checking not yet implemented)
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Returns an iterator over all {@link Corr} entries in the correspondence model whose
   * {@code sourceElement} has been set to {@code null} — i.e. correspondences that record
   * a deletion on the source side.
   * 
   * <p>These correspondences signal that the corresponding target element must be deleted
   * during forward propagation.</p>
   * 
   * @return a lazy iterator of {@link Corr}s with {@code sourceElement == null}
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      return Boolean.valueOf((_sourceElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Returns an iterator over all {@link Corr} entries in the correspondence model whose
   * {@code targetElement} has been set to {@code null} — i.e. correspondences that record
   * a deletion on the target side.
   * 
   * <p>These correspondences signal that the corresponding source element must be deleted
   * during backward propagation.</p>
   * 
   * @return a lazy iterator of {@link Corr}s with {@code targetElement == null}
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Deletes target elements that have lost their source counterpart (forward-direction
   * deletion handler).
   * 
   * <p>This method is called at the end of {@link #sourceToTarget()} to propagate source-side
   * deletions to the target model.  It iterates over all correspondences detected by
   * {@link #detectSourceDeletions()} and performs the following steps for each:</p>
   * 
   * <ol>
   *   <li><strong>Linked-list repair (manually added):</strong> If the target element is an
   *       {@code osets.Element}, its predecessor ({@code previous}) is re-linked to its
   *       successor ({@code next}) before the element is physically removed.  This keeps the
   *       doubly-linked list in {@code MyOrderedSet} consistent after the deletion.
   *       <br/>Specifically, {@code trg.previous.next} is set to {@code trg.next}.
   *       EMF's opposite-reference machinery automatically updates
   *       {@code trg.next.previous = trg.previous} as a side-effect, so only one side of
   *       the re-link needs to be written explicitly.
   *       <br/><b>Note:</b> This block is a <em>manual modification</em> of the BXtend
   *       generated template. Without it, the linked list would be left with a dangling
   *       pointer after a forward deletion.</li>
   *   <li>Collect the target element and the {@link Corr} object into a deletion list.</li>
   *   <li>After all correspondences have been processed, call {@link EcoreUtil#delete}
   *       on every collected object so that EMF can properly clean up cross-references.</li>
   * </ol>
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      if ((_targetElement instanceof Element)) {
        EObject _targetElement_1 = c.getTargetElement();
        final Element trg = ((Element) _targetElement_1);
        Element _previous = trg.getPrevious();
        boolean _tripleNotEquals = (_previous != null);
        if (_tripleNotEquals) {
          Element _previous_1 = trg.getPrevious();
          _previous_1.setNext(trg.getNext());
        }
      }
      EObject _targetElement_2 = c.getTargetElement();
      deletionList.add(_targetElement_2);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Deletes source elements that have lost their target counterpart (backward-direction
   * deletion handler).
   * 
   * <p>This method is called at the end of {@link #targetToSource()} to propagate target-side
   * deletions to the source model.  It collects the source element and the {@link Corr} for
   * every correspondence detected by {@link #detectTargetDeletions()}, then deletes them all
   * via {@link EcoreUtil#delete}.</p>
   * 
   * <p>No linked-list repair is needed here because {@code sets.Element} has no ordering
   * structure in the source metamodel.</p>
   */
  public void deleteUnreferencedSourceElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _sourceElement = c.getSourceElement();
      deletionList.add(_sourceElement);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectTargetDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }
}
