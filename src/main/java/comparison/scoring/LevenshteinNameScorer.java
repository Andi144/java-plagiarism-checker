package comparison.scoring;

import ast.Type;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class LevenshteinNameScorer implements TypeComparisonScorer {
	
	@Override
	public double computeComparisonScore(Type type1, Type type2) {
		return new LevenshteinDistance().apply(type1.getOriginalName(), type2.getOriginalName());
	}
	
}
