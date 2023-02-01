import comparison.CSVCreation;
import comparison.Comparison;
import comparison.FolderComparison;
import comparison.TypeComparison;
import detection.AvgPlagiarismDetection;
import detection.PlagiarismDetector;
import util.ArgumentParser;
import util.SubmissionUnpacking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class Application {
	
	public static void main(String[] args) throws IOException {
		ArgumentParser ap = new ArgumentParser();
		ap.addArgument("--submissionsZip", Path::of, null);
		ap.addListArgument("--folders", (List<String>) null);
		ap.addSetArgument("--excludedTypeNames", Set.of());
		ap.addArgument("--csvPath", Path::of, null);
		ap.addArgument("--avgThreshold", Double::parseDouble);
		ap.addArgument("--verbosity", Integer::parseInt, 0);
		ap.addMutuallyExclusiveArguments("--submissionsZip", "--folders");
		ap.parse(args);
		
		List<String> folders;
		Path submissionsZip = ap.get("--submissionsZip");
		if (submissionsZip != null) {
			folders = SubmissionUnpacking.unpackMoodleSubmissions(submissionsZip);
		} else {
			folders = ap.get("--folders");
		}
		Set<String> excludedTypeNames = ap.get("--excludedTypeNames");
		int verbosity = ap.get("--verbosity");
		List<FolderComparison> comparisons = Comparison.compare(folders, excludedTypeNames, verbosity);
		
		Path csvPath = ap.get("--csvPath");
		if (csvPath != null) {
			CSVCreation.createCSV(comparisons, csvPath); // not necessary but useful for external usage
		}
		
		double avgThreshold = ap.get("--avgThreshold");
		PlagiarismDetector pd = new PlagiarismDetector(new AvgPlagiarismDetection(avgThreshold));
		List<FolderComparison> detected = pd.detectPlagiarism(comparisons);
		for (FolderComparison fc : detected) {
			System.out.println("DETECTED FOLDERS:");
			System.out.println("|--- " + fc.getFolder1());
			System.out.println("|--- " + fc.getFolder2());
			for (TypeComparison tc : fc.getTypeComparisons()) {
				System.out.println("|--- DETECTED TYPES");
				System.out.println("|------- " + tc.getType1());
				System.out.println("|------- " + tc.getType2());
			}
			System.out.println();
		}
	}
	
}
