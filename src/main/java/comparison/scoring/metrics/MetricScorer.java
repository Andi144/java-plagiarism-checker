package comparison.scoring.metrics;

import comparison.scoring.OriginalAndRenamedScorer;

/**
 * Abstract metric scorer that simply creates a default name (see {@link #getBaseName()}) which is based on the class
 * name (see {@link Class#getSimpleName()}) as follows: If a concrete subclass name ends with the string
 * <code>"Scorer"</code>, then this suffix will be removed. Otherwise, the entire class name will be used. Subclasses
 * can change this behavior by overriding {@link #getBaseName()}. The method {@link #getName()} will use this specified
 * base name: Depending on whether the renamed type should be used by this metric scorer (see {@link
 * #MetricScorer(boolean useRenamed)}), an additional string <code>"Renamed"</code> will be added as prefix to
 * {@link #getBaseName()}. Example with default class-based base name:
 * <pre>{@code
 *     class MyCustomMetricScorer extends MetricScorer { ... }
 *     myCustomMetricScorer.getName() -> ["Renamed"]"MyCustomMetric"
 * }</pre>
 * Example with custom base name:
 * <pre>{@code
 *     class MyCustomMetricScorer extends MetricScorer {
 *         ...
 *         @Override
 *         protected String getBaseName() {
 *             return "MyCustomMetricScorer";
 *         }
 *     }
 *     myCustomMetricScorer.getName() -> ["Renamed"]"MyCustomMetricScorer"
 * }</pre>
 * <p>
 * Since the class name is used as default, there are a few special cases which will result in a useless default name.
 * For example, anonymous classes will simply have an empty string as name, so the creation of a {@link MetricScorer}
 * using an anonymous class is discouraged (see {@link Class#getSimpleName()} for details and other special cases).
 */
public abstract class MetricScorer extends OriginalAndRenamedScorer {
	
	private String defaultName;
	
	public MetricScorer(boolean useRenamed) {
		super(useRenamed);
		defaultName = null;
	}
	
	protected String getBaseName() {
		if (defaultName == null) {
			defaultName = getClass().getSimpleName();
			if (defaultName.endsWith("Scorer")) {
				defaultName = defaultName.substring(0, defaultName.length() - 6); // 6 = length of "Scorer"
			}
		}
		return defaultName;
	}
	
	public final String getName() {
		return (useRenamed ? "Renamed" : "") + getBaseName();
	}
	
}
