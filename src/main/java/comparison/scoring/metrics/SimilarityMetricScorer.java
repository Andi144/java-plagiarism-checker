package comparison.scoring.metrics;

import org.apache.commons.text.similarity.SimilarityScore;
import spoon.reflect.declaration.CtType;

public abstract class SimilarityMetricScorer extends MetricScorer {
	
	private final SimilarityScore<Double> similarityScore;
	
	public SimilarityMetricScorer(boolean useRenamed, SimilarityScore<Double> similarityScore) {
		super(useRenamed);
		this.similarityScore = similarityScore;
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		// TODO: SimilarityScore<Double> might have values > 1 (see interface documentation)
		return 1 - similarityScore.apply(type1.toString(), type2.toString());
	}
	
}
