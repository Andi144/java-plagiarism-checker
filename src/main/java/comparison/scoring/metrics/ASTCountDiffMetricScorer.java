package comparison.scoring.metrics;

import ast.ASTUtil;
import spoon.reflect.declaration.CtType;

public class ASTCountDiffMetricScorer extends MetricScorer {
	
	public ASTCountDiffMetricScorer(boolean useRenamed) {
		super(useRenamed, useRenamed ? "RenamedASTCountDiffMetric" : "ASTCountDiffMetric");
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		int count1 = ASTUtil.countElements(type1);
		int count2 = ASTUtil.countElements(type2);
		int countDiff = Math.abs(count1 - count2);
		return (double) countDiff / Math.max(count1, count2);
	}
	
}
