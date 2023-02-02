package comparison.scoring.metrics;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

public class JaroWinklerMetricScorer extends SimilarityMetricScorer {
	
	public JaroWinklerMetricScorer(boolean useRenamed) {
		super(useRenamed, useRenamed ? "RenamedJaroWinklerMetric" : "JaroWinklerMetric", new JaroWinklerSimilarity());
	}
	
}