package comparison.scoring;

import ast.Type;
import spoon.reflect.declaration.CtType;

/**
 * Scorer that supports computing a score for both the original type ({@link Type#original()}) and the renamed type
 * ({@link Type#renamed()}).
 */
public abstract class OriginalAndRenamedScorer implements TypeComparisonScorer {
	
	private final boolean useRenamed;
	
	public OriginalAndRenamedScorer(boolean useRenamed) {
		this.useRenamed = useRenamed;
	}
	
	@Override
	public double computeComparisonScore(Type type1, Type type2) {
		if (useRenamed) {
			return computeComparisonScore(type1.renamed(), type2.renamed());
		}
		return computeComparisonScore(type1.original(), type2.original());
	}
	
	protected abstract double computeComparisonScore(CtType<?> type1, CtType<?> type2);
	
}
