package comparison.scoring;

import ast.ASTUtil;
import spoon.reflect.declaration.CtType;

public class ASTCountDiffScorer extends OriginalAndRenamedScorer {
	
	public ASTCountDiffScorer(boolean useRenamed) {
		super(useRenamed);
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		int countType1 = ASTUtil.countElements(type1);
		int countType2 = ASTUtil.countElements(type2);
		return Math.abs(countType1 - countType2);
	}
	
}
