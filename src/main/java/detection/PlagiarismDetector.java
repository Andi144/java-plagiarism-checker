package detection;

import comparison.FolderComparison;
import comparison.TypeComparison;

import java.util.ArrayList;
import java.util.List;

public class PlagiarismDetector {
	
	private PlagiarismDetection plagiarismDetection;
	
	public PlagiarismDetector(PlagiarismDetection plagiarismDetection) {
		this.plagiarismDetection = plagiarismDetection;
	}
	
	public PlagiarismDetection getPlagiarismDetection() {
		return plagiarismDetection;
	}
	
	public void setPlagiarismDetection(PlagiarismDetection plagiarismDetection) {
		this.plagiarismDetection = plagiarismDetection;
	}
	
	public List<FolderComparison> detectPlagiarism(List<FolderComparison> comparisons) {
		List<FolderComparison> detected = new ArrayList<>();
		for (FolderComparison fc : comparisons) {
			// do not change the passed FolderComparison objects, so create a copy and only add those
			// TypeComparisons that were identified as plagiarism (and only collect a FolderComparison
			// if there is at least one such identification of plagiarism)
			boolean found = false;
			FolderComparison fcCopy = new FolderComparison(fc.getFolder1(), fc.getFolder2());
			for (TypeComparison tc : fc.getTypeComparisons()) {
				if (plagiarismDetection.isPlagiarism(tc.getMetrics())) {
					found = true;
					fcCopy.addTypeComparison(tc);
				}
			}
			if (found) {
				detected.add(fcCopy);
			}
		}
		return detected;
	}
	
}
