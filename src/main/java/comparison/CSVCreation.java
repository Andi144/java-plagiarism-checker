package comparison;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CSVCreation {
	
	private CSVCreation() {
	}
	
	public static void createCSV(List<FolderComparison> folderComparisons, Path path) throws IOException {
		try (BufferedWriter bw = Files.newBufferedWriter(path)) {
			boolean first = true;
			for (FolderComparison fc : folderComparisons) {
				if (first) {
					bw.write(fc.getCSVHeader());
					bw.newLine();
					first = false;
				}
				bw.write(fc.getCSVString());
				bw.newLine();
			}
		}
	}
	
}
