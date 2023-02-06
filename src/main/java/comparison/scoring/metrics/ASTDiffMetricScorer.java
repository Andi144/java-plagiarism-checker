package comparison.scoring.metrics;

import ast.ASTUtil;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import spoon.reflect.declaration.CtType;

public class ASTDiffMetricScorer extends MetricScorer {
	
	private final AstComparator comparator;
	
	public ASTDiffMetricScorer(boolean useRenamed) {
		this(useRenamed, new AstComparator());
	}
	
	public ASTDiffMetricScorer(boolean useRenamed, AstComparator comparator) {
		super(useRenamed);
		this.comparator = comparator;
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		Diff typeDiff = comparator.compare(type1, type2);
		int diff = typeDiff.getRootOperations().size();
		int count1 = ASTUtil.countElements(type1);
		int count2 = ASTUtil.countElements(type2);
		return (double) diff / Math.max(count1, count2);
	}
	
}
