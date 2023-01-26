import comparison.CSVCreation;
import comparison.Comparison;
import comparison.FolderComparison;
import comparison.TypeComparison;
import detection.AvgPlagiarismDetection;
import detection.PlagiarismDetector;
import util.ArgParsing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class Application {
	
	public static void main(String[] args) throws IOException {
		List<String> folders = ArgParsing.extractListArg(args, "--folders");
		Set<String> excludedTypeNames = ArgParsing.extractSetArg(args, "--excludedTypeNames");
		Path csvPath = ArgParsing.extractArg(args, "--csvPath", Path::of);
		double avgThreshold = ArgParsing.extractArg(args, "--avgThreshold", Double::parseDouble);
		int verbosity = ArgParsing.extractArg(args, "--verbosity", Integer::parseInt, 0);
		List<FolderComparison> comparisons = Comparison.compare(folders, excludedTypeNames, verbosity);
		CSVCreation.createCSV(comparisons, csvPath); // not necessary but useful for external usage
		PlagiarismDetector pd = new PlagiarismDetector(new AvgPlagiarismDetection(avgThreshold));
		List<FolderComparison> detected = pd.detectPlagiarism(comparisons);
		for (FolderComparison fc : detected) {
			System.out.println("DETECTED FOLDERS:");
			System.out.println("|--- " + fc.getFolder1());
			System.out.println("|--- " + fc.getFolder2());
			for (TypeComparison tc : fc.getTypeComparisons()) {
				System.out.println("|--- DETECTED TYPES");
				System.out.println("|------- " + tc.getType1().getSimpleName());
				System.out.println("|------- " + tc.getType2().getSimpleName());
			}
		}
	}
	
}
