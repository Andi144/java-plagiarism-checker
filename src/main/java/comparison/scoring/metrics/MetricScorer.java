package comparison.scoring.metrics;

import comparison.scoring.OriginalAndRenamedScorer;

public abstract class MetricScorer extends OriginalAndRenamedScorer {
	
	private final String name;
	
	public MetricScorer(boolean useRenamed, String name) {
		super(useRenamed);
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
}
