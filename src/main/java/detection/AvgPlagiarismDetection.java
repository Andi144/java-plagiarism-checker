package detection;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class AvgPlagiarismDetection implements PlagiarismDetection {
	
	private final double threshold;
	
	public AvgPlagiarismDetection(double threshold) {
		this.threshold = threshold;
	}
	
	@Override
	public boolean isPlagiarism(List<Pair<String, Double>> metrics) {
		double avg = metrics.stream().mapToDouble(Pair::getRight).average().orElseThrow();
		return avg < threshold;
	}
	
}
