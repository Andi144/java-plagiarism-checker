package comparison;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FolderComparison {
	
	private final String folder1;
	private final String folder2;
	private final List<TypeComparison> typeComparisons;
	
	public FolderComparison(String folder1, String folder2) {
		this.folder1 = folder1;
		this.folder2 = folder2;
		this.typeComparisons = new ArrayList<>();
	}
	
	public String getFolder1() {
		return folder1;
	}
	
	public String getFolder2() {
		return folder2;
	}
	
	public List<TypeComparison> getTypeComparisons() {
		return Collections.unmodifiableList(typeComparisons);
	}
	
	public void addTypeComparison(TypeComparison typeComparison) {
		typeComparisons.add(typeComparison);
	}
	
	String getCSVHeader() {
		// TypeComparison header should be the same for every object
		return String.format("folder1,folder2,%s", typeComparisons.get(0).getCSVHeader());
	}
	
	String getCSVString() {
		StringBuilder sb = new StringBuilder();
		String escapedFolder1 = StringEscapeUtils.escapeCsv(folder1);
		String escapedFolder2 = StringEscapeUtils.escapeCsv(folder2);
		for (TypeComparison tc : typeComparisons) {
			sb.append(String.format("%s,%s,%s\n", escapedFolder1, escapedFolder2, tc.getCSVString()));
		}
		return sb.toString();
	}
	
}
