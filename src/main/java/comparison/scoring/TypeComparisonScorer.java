package comparison.scoring;

import ast.Type;

public interface TypeComparisonScorer {
	
	double computeComparisonScore(Type type1, Type type2);
	
}
