package comparison.scoring.metrics;

import ast.ASTUtil;
import ast.Type;
import comparison.scoring.ASTDiffScorer;
import spoon.reflect.declaration.CtType;

public class ASTDiffMetricScorer extends MetricScorer {
	
	private final ASTDiffScorer astDiffScorer;
	
	public ASTDiffMetricScorer(boolean useRenamed) {
		super(useRenamed, useRenamed ? "RenamedASTDiffMetric" : "ASTDiffMetric");
		astDiffScorer = new ASTDiffScorer(useRenamed);
	}
	
	@Override
	public double computeComparisonScore(Type type1, Type type2) {
		double astDiffScore = astDiffScorer.computeComparisonScore(type1, type2);
		return astDiffScore / super.computeComparisonScore(type1, type2);
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		int count1 = ASTUtil.countElements(type1);
		int count2 = ASTUtil.countElements(type2);
		return Math.max(count1, count2);
	}
	
}
