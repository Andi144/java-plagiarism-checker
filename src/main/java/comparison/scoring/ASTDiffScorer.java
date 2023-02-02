package comparison.scoring;

import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import spoon.reflect.declaration.CtType;

public class ASTDiffScorer extends OriginalAndRenamedScorer {
	
	private final AstComparator comparator;
	
	public ASTDiffScorer(boolean useRenamed) {
		this(useRenamed, new AstComparator());
	}
	
	public ASTDiffScorer(boolean useRenamed, AstComparator comparator) {
		super(useRenamed);
		this.comparator = comparator;
	}
	
	@Override
	protected double computeComparisonScore(CtType<?> type1, CtType<?> type2) {
		Diff typeDiff = comparator.compare(type1, type2);
		return typeDiff.getRootOperations().size();
	}
	
}
