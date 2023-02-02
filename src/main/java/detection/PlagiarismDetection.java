package detection;

import comparison.scoring.metrics.MetricScorer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface PlagiarismDetection {
	
	boolean isPlagiarism(List<Pair<MetricScorer, Double>> metrics);
	
}
