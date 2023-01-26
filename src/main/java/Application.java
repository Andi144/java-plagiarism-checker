import comparison.CSVCreation;
import comparison.Comparison;
import comparison.FolderComparison;
import util.ArgParsing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class Application {
	
	// TODO: should be an arg
	// if avg metric is < threshold --> issue plagiarism warning
	// TODO: very simple heuristic, maybe change into something more sophisticated (e.g., via exchangeable interface
	//  implementation (strategy pattern) "interface PlagDetection { boolean isPlag(Map<String, Double> metrics) }")
	private static final double THRESHOLD = 0.05;
	
	public static void main(String[] args) throws IOException {
		List<String> folders = ArgParsing.extractListArg(args, "--folders");
		Set<String> excludedTypeNames = ArgParsing.extractSetArg(args, "--excludedTypeNames");
		Path csvPath = ArgParsing.extractArg(args, "--csvPath", Path::of);
		int verbosity = ArgParsing.extractArg(args, "--verbosity", Integer::parseInt, 0);
		List<FolderComparison> comparisons = Comparison.compare(folders, excludedTypeNames, verbosity);
		CSVCreation.createCSV(comparisons, csvPath);
	}
	
}
