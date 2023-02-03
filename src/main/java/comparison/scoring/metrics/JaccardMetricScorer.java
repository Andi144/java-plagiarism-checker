package comparison.scoring.metrics;

import org.apache.commons.text.similarity.JaccardSimilarity;

public class JaccardMetricScorer extends SimilarityMetricScorer {
	
	public JaccardMetricScorer(boolean useRenamed) {
		super(useRenamed, new JaccardSimilarity());
	}
	
}

