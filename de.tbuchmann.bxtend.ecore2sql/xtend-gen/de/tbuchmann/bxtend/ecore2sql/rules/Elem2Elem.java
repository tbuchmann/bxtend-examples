package de.tbuchmann.bxtend.ecore2sql.rules;

import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.BasicElem;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlFactory;
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import sql.Annotation;
import sql.ModelElement;
import sql.SqlFactory;
import sql.SqlPackage;

/**
 * Abstract base class for all bidirectional transformation rules in the Ecore-to-SQL BXtend transformation.
 * 
 * <p>Each concrete subclass represents one rule of the transformation, implementing both the
 * forward direction ({@link #sourceToTarget}) and the backward direction ({@link #targetToSource}).
 * The BXtend approach is <em>correspondence-based</em>: every pair of corresponding source and target
 * elements is linked by a {@link Corr} object stored in a dedicated correspondence model.  This makes
 * the transformation <em>incremental</em> – on re-propagation only the elements that have actually
 * changed need to be updated, because the existing correspondences are reused.</p>
 * 
 * <h3>Model roles</h3>
 * <ul>
 *   <li><b>sourceModel</b> – an Ecore model (instances of {@link org.eclipse.emf.ecore.EPackage},
 *       {@link org.eclipse.emf.ecore.EClass}, {@link org.eclipse.emf.ecore.EAttribute},
 *       {@link org.eclipse.emf.ecore.EReference}, etc.)</li>
 *   <li><b>targetModel</b> – a SQL model (instances of {@code sql.Schema}, {@code sql.Table},
 *       {@code sql.Column}, {@code sql.ForeignKey}, etc.)</li>
 *   <li><b>corrModel</b> – the correspondence model, a {@link Transformation} root containing
 *       a flat list of {@link Corr} objects, each linking exactly one source element to one
 *       target element together with a {@link Corr#desc descriptive tag} that identifies
 *       which rule created the correspondence.</li>
 * </ul>
 * 
 * <h3>Helper protocol</h3>
 * <ul>
 *   <li>{@link #getOrCreateCorrModelElement} – looks up an existing {@link Corr} for a given model
 *       element or creates a fresh one when none exists yet.</li>
 *   <li>{@link #getOrCreateSourceElem} / {@link #getOrCreateTargetElem} – given a {@link Corr}, return
 *       the already-linked source/target element or instantiate a new one of the specified metaclass.</li>
 *   <li>{@link #addAnnotations} – attaches string-valued {@code Annotation} objects to SQL
 *       {@link ModelElement}s; these annotations carry the semantic metadata (e.g. {@code "class"},
 *       {@code "attribute"}, {@code "containment"}) that the backward direction uses to reconstruct
 *       the Ecore structure from SQL tables and columns.</li>
 * </ul>
 */
@SuppressWarnings("all")
public abstract class Elem2Elem {
  /**
   * The Ecore source model resource.
   */
  protected Resource sourceModel;

  /**
   * The SQL target model resource.
   */
  protected Resource targetModel;

  /**
   * The correspondence model resource holding all {@link Corr} links.
   */
  protected Resource corrModel;

  /**
   * Factory used to create Ecore elements in the backward direction.
   */
  protected final EcoreFactory sourceFactory = EcoreFactory.eINSTANCE;

  /**
   * Factory used to create SQL elements in the forward direction.
   */
  protected final SqlFactory targetFactory = SqlFactory.eINSTANCE;

  /**
   * Factory used to create new correspondence ({@link Corr}) objects.
   */
  protected final Ecore2sqlFactory corrFactory = Ecore2sqlFactory.eINSTANCE;

  /**
   * The singleton Ecore metamodel package, used for metaclass look-ups.
   */
  protected final EcorePackage sourcePackage = EcorePackage.eINSTANCE;

  /**
   * The singleton SQL metamodel package, used for metaclass look-ups.
   */
  protected final SqlPackage targetPackage = SqlPackage.eINSTANCE;

  /**
   * Human-readable rule identifier stored in every {@link Corr#desc} created by this rule.
   * Subclasses set this in their constructor, e.g. {@code "class2table"}.
   */
  protected String ruleID;

  /**
   * Legacy map kept for potential future optimisation (currently unused at runtime).
   */
  protected static Map<EObject, Corr> elementsToCorr = CollectionLiterals.<EObject, Corr>newHashMap();

  /**
   * Shared, static map from a {@link Corr} to the identity/name attribute of its element
   * pair (e.g. an {@code EAttribute}'s {@code name} / the corresponding {@code Column}'s
   * {@code name}) as observed at the end of the last direction call. Used by rules whose
   * {@code synch()} needs to distinguish a source-side rename from a target-side rename —
   * see {@link Attribute2Attribute#synch()} — to decide whether to push, pull, or (per this
   * tool's target-wins conflict policy) let the target win when both changed.
   */
  protected static Map<Corr, String> corrToName = CollectionLiterals.<Corr, String>newHashMap();

  /**
   * Constructs an Elem2Elem rule wired to the three model resources.
   * 
   * @param src  the Ecore source model resource
   * @param trgt the SQL target model resource
   * @param corr the correspondence model resource
   */
  public Elem2Elem(final Resource src, final Resource trgt, final Resource corr) {
    this.sourceModel = src;
    this.targetModel = trgt;
    this.corrModel = corr;
    this.ruleID = "base";
  }

  /**
   * Forward propagation: transforms (a subset of) the source Ecore model into SQL elements.
   * Subclasses override this to implement the rule-specific forward logic.
   */
  public void sourceToTarget() {
  }

  /**
   * Backward propagation: transforms (a subset of) the SQL target model back into Ecore elements.
   * Subclasses override this to implement the rule-specific backward logic.
   */
  public void targetToSource() {
  }

  /**
   * Synchronisation: reconciles concurrent edits made to both the Ecore source model and the
   * SQL target model since the last synchronisation point. Subclasses override this; the
   * default implementation is a no-op.
   * 
   * <p>The baseline pattern (see e.g. {@link Package2Schema#synch()}, {@link Class2Table#synch()})
   * is <em>source-led</em>: re-run this rule's own {@link #sourceToTarget()} (already idempotent
   * get-or-create logic, so it safely reasserts existing correspondences and creates new ones for
   * source-side insertions), then absorb any SQL-side elements that still have no correspondence
   * at all — genuine target-side insertions — using the same per-element logic as
   * {@link #targetToSource()}, filtered to only the unmatched ones so already-processed
   * correspondences are not revisited.</p>
   * 
   * <p><b>Exception — rename conflicts:</b> for a rule whose element pair has an independently
   * renamable identity attribute on <em>both</em> sides (e.g. {@link Attribute2Attribute}'s
   * {@code EAttribute.name} / {@code Column.name}), blindly re-running {@link #sourceToTarget()}
   * would silently discard a target-side rename. Those rules instead resolve the name
   * independently per element using {@link #corrToName} as a last-synced snapshot, and — per
   * this benchmark tool's {@code SyncConflictPolicy.TARGET_WINS} policy (see
   * {@code org.benchmarx.examples.ecore2sql.testsuite.concurrent.Conflicts}) — let the target
   * win whenever both sides changed since the last synchronisation. See
   * {@link Attribute2Attribute#synch()} for the concrete pattern.</p>
   */
  public void synch() {
  }

  /**
   * Looks up the {@link Corr} object whose {@code sourceElement} or {@code targetElement}
   * equals {@code obj}.
   * 
   * @param obj any source or target model element
   * @return the corresponding {@link Corr}, or {@code null} if none exists yet
   */
  public Corr getCorrModelElem(final EObject obj) {
    EList<EObject> _contents = this.corrModel.getContents();
    EObject _get = null;
    if (_contents!=null) {
      _get=_contents.get(0);
    }
    final Function1<Corr, Boolean> _function = (Corr corr) -> {
      return Boolean.valueOf((Objects.equals(corr.getSourceElement(), obj) || Objects.equals(corr.getTargetElement(), obj)));
    };
    return IterableExtensions.<Corr>findFirst(((Transformation) _get).getCorrespondences(), _function);
  }

  /**
   * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new
   * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.BasicElem BasicElem}
   * correspondence if none exists yet.
   * 
   * <p>The new correspondence is automatically wired to either the {@code sourceElement}
   * or {@code targetElement} slot depending on the metamodel package of {@code obj}.</p>
   * 
   * @param obj         the model element for which a correspondence is needed
   * @param description a short label identifying the creating rule (stored in {@link Corr#desc})
   * @return the (possibly newly created) {@link Corr} object
   */
  public Corr getOrCreateCorrModelElement(final EObject obj, final String description) {
    Corr corr = this.getCorrModelElem(obj);
    if ((corr == null)) {
      BasicElem _createBasicElem = this.corrFactory.createBasicElem();
      final Procedure1<BasicElem> _function = (BasicElem it) -> {
        EPackage _ePackage = obj.eClass().getEPackage();
        if ((_ePackage instanceof EcorePackage)) {
          it.setSourceElement(obj);
        }
        EPackage _ePackage_1 = obj.eClass().getEPackage();
        if ((_ePackage_1 instanceof SqlPackage)) {
          it.setTargetElement(obj);
        }
        it.setDesc(description);
      };
      BasicElem _doubleArrow = ObjectExtensions.<BasicElem>operator_doubleArrow(_createBasicElem, _function);
      corr = _doubleArrow;
      EObject _get = this.corrModel.getContents().get(0);
      EList<Corr> _correspondences = ((Transformation) _get).getCorrespondences();
      _correspondences.add(corr);
    }
    return corr;
  }

  /**
   * Creates a new Ecore element of the given metaclass using the Ecore factory.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return a freshly created Ecore {@link EObject}
   */
  public EObject createSourceElement(final EClass clazz) {
    return this.sourceFactory.create(clazz);
  }

  /**
   * Creates a new SQL element of the given metaclass using the SQL factory.
   * 
   * @param clazz the {@link EClass} to instantiate
   * @return a freshly created SQL {@link EObject}
   */
  public EObject createTargetElement(final EClass clazz) {
    return this.targetFactory.create(clazz);
  }

  /**
   * Returns the source element linked by {@code corr}, creating and linking a new instance
   * of {@code clazz} when the source slot is still empty.
   * 
   * @param corr  the correspondence whose source element is needed
   * @param clazz the Ecore metaclass to instantiate if no source element exists yet
   * @return the existing or newly created source element
   */
  public EObject getOrCreateSourceElem(final Corr corr, final EClass clazz) {
    EObject source = corr.getSourceElement();
    EObject _sourceElement = corr.getSourceElement();
    boolean _tripleEquals = (_sourceElement == null);
    if (_tripleEquals) {
      source = this.createSourceElement(clazz);
      corr.setSourceElement(source);
    }
    return source;
  }

  /**
   * Returns the target element linked by {@code corr}, creating and linking a new instance
   * of {@code clazz} when the target slot is still empty.
   * 
   * @param corr  the correspondence whose target element is needed
   * @param clazz the SQL metaclass to instantiate if no target element exists yet
   * @return the existing or newly created target element
   */
  public EObject getOrCreateTargetElem(final Corr corr, final EClass clazz) {
    EObject target = corr.getTargetElement();
    if ((target == null)) {
      target = this.createTargetElement(clazz);
      corr.setTargetElement(target);
    }
    return target;
  }

  /**
   * Adds the given string annotations to a SQL {@link ModelElement}, skipping any string
   * that is already present.  The special strings {@code "unidirectional"} and
   * {@code "bidirectional"} are treated as mutually exclusive: if the opposite annotation
   * already exists it is updated in-place instead of adding a duplicate.
   * 
   * <p>Annotations are the primary mechanism by which the SQL model encodes the semantic
   * context of each element so that the backward transformation can reconstruct the
   * appropriate Ecore construct (e.g. distinguish a column that came from an EAttribute
   * from one that came from an EReference).</p>
   * 
   * @param owner   the SQL element to annotate
   * @param strings the list of annotation strings to attach
   */
  public void addAnnotations(final ModelElement owner, final List<String> strings) {
    final Consumer<String> _function = (String s) -> {
      if (s != null) {
        switch (s) {
          case "unidirectional":
            final Function1<Annotation, Boolean> _function_1 = (Annotation it) -> {
              String _annotation = it.getAnnotation();
              return Boolean.valueOf(Objects.equals(_annotation, "bidirectional"));
            };
            final Annotation annot = IterableExtensions.<Annotation>findFirst(owner.getOwnedAnnotations(), _function_1);
            if ((annot != null)) {
              annot.setAnnotation(s);
            }
            break;
          case "bidirectional":
            final Function1<Annotation, Boolean> _function_2 = (Annotation it) -> {
              String _annotation = it.getAnnotation();
              return Boolean.valueOf(Objects.equals(_annotation, "unidirectional"));
            };
            final Annotation annot_1 = IterableExtensions.<Annotation>findFirst(owner.getOwnedAnnotations(), _function_2);
            if ((annot_1 != null)) {
              annot_1.setAnnotation(s);
            }
            break;
        }
      }
      final Function1<Annotation, Boolean> _function_3 = (Annotation a) -> {
        return Boolean.valueOf(a.getAnnotation().equals(s));
      };
      Annotation _findFirst = IterableExtensions.<Annotation>findFirst(owner.getOwnedAnnotations(), _function_3);
      boolean _tripleEquals = (_findFirst == null);
      if (_tripleEquals) {
        Annotation _createAnnotation = this.targetFactory.createAnnotation();
        final Procedure1<Annotation> _function_4 = (Annotation it) -> {
          it.setAnnotation(s);
        };
        final Annotation an = ObjectExtensions.<Annotation>operator_doubleArrow(_createAnnotation, _function_4);
        EList<Annotation> _ownedAnnotations = owner.getOwnedAnnotations();
        _ownedAnnotations.add(an);
      }
    };
    strings.forEach(_function);
  }
}
