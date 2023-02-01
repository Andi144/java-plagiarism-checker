package comparison;

import ast.ASTRenamer;
import ast.Type;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import spoon.reflect.declaration.CtElement;

import java.util.*;

// TODO: currently static-only; might change to object-oriented design which would also get rid of the many "carry-over"
//  parameters of all the different methods
// TODO: also, naming is suboptimal (indicates that FolderComparison and TypeComparison could be subclasses, which they
//  are not. this might be solved with the above comment by replacing the code with OO design and a new class name)
public class Comparison {
	
	private Comparison() {
	}
	
	public static List<FolderComparison> compare(List<String> folders, Set<String> excludedTypeNames, int verbosity) {
		List<Pair<String, String>> folderPairs = new ArrayList<>();
		for (int i = 0; i < folders.size() - 1; i++) {
			for (int j = i + 1; j < folders.size(); j++) {
				folderPairs.add(Pair.of(folders.get(i), folders.get(j)));
			}
		}
		try (ProgressBar pb = new ProgressBar("Comparing folders", folderPairs.size())) {
			return folderPairs.parallelStream().map(p -> {
				FolderComparison comparison = compareFolders(p.getLeft(), p.getRight(), excludedTypeNames, verbosity);
				pb.step();
				return comparison;
			}).filter(Objects::nonNull).toList();
		}
	}
	
	// TODO: replace verbosity and standard output printing with logging
	private static FolderComparison compareFolders(String folder1, String folder2, Set<String> excludedTypeNames, int verbosity) {
		if (verbosity >= 1) {
			System.out.println("##########################################################");
			System.out.println("Comparing folder");
			System.out.println(folder1);
			System.out.println("to");
			System.out.println(folder2);
			System.out.println("##########################################################");
		}
		
		ASTRenamer renamer1 = new ASTRenamer(folder1, excludedTypeNames, false, false);
		ASTRenamer renamer2 = new ASTRenamer(folder2, excludedTypeNames, false, false);
		List<Type> types1 = renamer1.getTypes();
		List<Type> types2 = renamer2.getTypes();
		
		// Cannot make a comparison without having at least one type in each folder
		if (types1.isEmpty() || types2.isEmpty()) {
			return null;
		}
		
		FolderComparison folderComparison = new FolderComparison(folder1, folder2);
		for (Type type1 : types1) {
			Type matchingType = findMatchingType(type1, types2, verbosity);
			List<Pair<String, Double>> metrics = computeMetrics(type1, matchingType, verbosity);
			folderComparison.addTypeComparison(new TypeComparison(type1.getOriginalName(), matchingType.getOriginalName(), metrics));
		}
		return folderComparison;
	}
	
	private static Type findMatchingType(Type type, List<Type> candidates, int verbosity) {
		if (candidates.isEmpty()) {
			throw new IllegalStateException("candidates must not be empty");
		}
		AstComparator comparator = new AstComparator();
		int countType = countElements(type.original());
		
		Map<Type, List<Integer>> comparison = new HashMap<>();
		// TODO: currently, these 4 are hard-coded; could make this more generic to allow arbitrary diffs
		ArrayList<Pair<Type, Integer>> typeDiffSizes = new ArrayList<>();
		ArrayList<Pair<Type, Integer>> renamedTypeDiffSizes = new ArrayList<>();
		ArrayList<Pair<Type, Integer>> countDiffs = new ArrayList<>();
		ArrayList<Pair<Type, Integer>> levenDiffs = new ArrayList<>();
		
		if (verbosity >= 2) {
			System.out.println("Comparing '" + type.getOriginalName() + "'");
		}
		for (Type candidate : candidates) {
			comparison.put(candidate, new ArrayList<>());
			
			Diff typeDiff = comparator.compare(type.original(), candidate.original());
			int typeDiffSize = typeDiff.getRootOperations().size();
			typeDiffSizes.add(Pair.of(candidate, typeDiffSize));
			
			Diff renamedTypeDiff = comparator.compare(type.renamed(), candidate.renamed());
			int renamedTypeDiffSize = renamedTypeDiff.getRootOperations().size();
			renamedTypeDiffSizes.add(Pair.of(candidate, renamedTypeDiffSize));
			
			int countCandidate = countElements(candidate.original());
			int countDiff = Math.abs(countType - countCandidate);
			countDiffs.add(Pair.of(candidate, countDiff));
			
			int levenDiff = new LevenshteinDistance().apply(type.getOriginalName(), candidate.getOriginalName());
			levenDiffs.add(Pair.of(candidate, levenDiff));
			
			if (verbosity >= 2) {
				System.out.format("  to %25s: typeDiffSize = %3d, renamedTypeDiffSize = %3d, countDiff = %4d, levenDiff = %3d\n", "'" + candidate.getOriginalName() + "'", typeDiffSize, renamedTypeDiffSize, countDiff, levenDiff);
			}
		}
		
		// TODO: comments do not include levenDiff
		// For each diff list, sort according to the minimum diff and then assign values 1, 2, 3, ... to the
		// respective AST elements (the various "candidate" from above). Afterwards, for each AST element (for each
		// "candidate" from above), we have a normalized sorting. Example:
		// Comparing 'Exercise1' ("type")
		//   to OneDimensionalArray ("candidate"), typeDiffSize = 55, renamedTypeDiffSize = 52, countDiff =  11
		//   to      StoryGenerator ("candidate"), typeDiffSize = 70, renamedTypeDiffSize = 63, countDiff =  36
		//   to      TextConversion ("candidate"), typeDiffSize = 58, renamedTypeDiffSize = 59, countDiff =  46
		//   to       TwoDimensions ("candidate"), typeDiffSize = 93, renamedTypeDiffSize = 97, countDiff = 284
		// This will be mapped to:
		// Comparing 'Exercise1' ("type")
		//   to OneDimensionalArray ("candidate"), typeDiffSize = 1, renamedTypeDiffSize = 1, countDiff = 1
		//   to      StoryGenerator ("candidate"), typeDiffSize = 3, renamedTypeDiffSize = 3, countDiff = 2
		//   to      TextConversion ("candidate"), typeDiffSize = 2, renamedTypeDiffSize = 2, countDiff = 3
		//   to       TwoDimensions ("candidate"), typeDiffSize = 4, renamedTypeDiffSize = 4, countDiff = 4
		sortAndAddNormalized(typeDiffSizes, comparison);
		sortAndAddNormalized(renamedTypeDiffSizes, comparison);
		sortAndAddNormalized(countDiffs, comparison);
		sortAndAddNormalized(levenDiffs, comparison);
		
		// With the new, normalized mapping from above, we can now look for the "best" match, where "best" is simply
		// defined as the minimum product of an AST element's (a "candidate"'s) differences. The product (compared to
		// summing the differences) has the advantage that multiple low values are more heavily rewarded, e.g., if
		// there are the diffs (1, 1, 4) and (2, 2, 1), the product (4 vs. 9) would still favor the first diff,
		// whereas the sum (8 vs. 7) would favor the second diff, which probably follows the intuition that the
		// first diff is likely the matching one ("two best scores vs. probably one outlier"). Of course, this is
		// just a heuristic and can lead to false results. Example from above:
		// Comparing 'Exercise1' ("type")
		//   to OneDimensionalArray ("candidate"), typeDiffSize = 1, renamedTypeDiffSize = 1, countDiff = 1 --> product =  1
		//   to      StoryGenerator ("candidate"), typeDiffSize = 3, renamedTypeDiffSize = 3, countDiff = 2 --> product = 18
		//   to      TextConversion ("candidate"), typeDiffSize = 2, renamedTypeDiffSize = 2, countDiff = 3 --> product = 12
		//   to       TwoDimensions ("candidate"), typeDiffSize = 4, renamedTypeDiffSize = 4, countDiff = 4 --> product = 64
		// In this case, the 'OneDimensionalArray' AST element ("candidate") will be selected as the best match for the
		// original AST element 'Exercise1' ("type").
		return comparison.entrySet().stream()
				.map(entry -> Pair.of(entry.getKey(), entry.getValue().stream()
						.mapToInt(x -> x)
						.reduce(1, (a, x) -> a * x)))
				.min(Comparator.comparingInt(Pair::getRight))
				.map(Pair::getLeft)
				.orElseThrow();
	}
	
	private static void sortAndAddNormalized(ArrayList<Pair<Type, Integer>> diffs, Map<Type, List<Integer>> comparison) {
		diffs.sort(Comparator.comparingInt(Pair::getRight));
		int i = 1;
		for (Pair<Type, Integer> pair : diffs) {
			comparison.get(pair.getLeft()).add(i);
			i++;
		}
	}
	
	private static int countElements(CtElement element) {
		int count = 1;
		for (CtElement e : element.getDirectChildren()) {
			count += countElements(e);
		}
		return count;
	}
	
	private static List<Pair<String, Double>> computeMetrics(Type type1, Type type2, int verbosity) {
		AstComparator comparator = new AstComparator();
		Diff typeDiff = comparator.compare(type1.original(), type2.original());
		int typeDiffSize = typeDiff.getRootOperations().size();
		Diff renamedTypeDiff = comparator.compare(type1.renamed(), type2.renamed());
		int renamedTypeDiffSize = renamedTypeDiff.getRootOperations().size();
		int count1 = countElements(type1.original());
		int count2 = countElements(type2.original());
		int countDiff = Math.abs(count1 - count2);
		
		if (verbosity >= 1) {
			System.out.println("Comparing '" + type1.getOriginalName() + "' to " + "'" + type2.getOriginalName() + "'");
			System.out.println("AST changes (before renaming): " + typeDiffSize);
			System.out.println("AST changes  (after renaming): " + renamedTypeDiffSize);
			System.out.println("AST element count difference: " + countDiff);
			System.out.println();
		}
		
		// TODO: currently, all these are hard-coded; could make this more generic to allow arbitrary metrics
		double typeDiffSizeMetric = (double) typeDiffSize / Math.max(count1, count2);
		double renamedDiffSizeMetric = (double) renamedTypeDiffSize / Math.max(count1, count2);
		double countDiffMetric = (double) countDiff / Math.max(count1, count2);
		double jaccard = 1 - new JaccardSimilarity().apply(type1.original().toString(), type2.original().toString());
		double renamedJaccard = 1 - new JaccardSimilarity().apply(type1.renamed().toString(), type2.renamed().toString());
		double jaro = 1 - new JaroWinklerSimilarity().apply(type1.original().toString(), type2.original().toString());
		double renamedJaro = 1 - new JaroWinklerSimilarity().apply(type1.renamed().toString(), type2.renamed().toString());
		
		// TODO: maybe create own Metrics object
		return List.of(
				Pair.of("typeDiffSizeMetric", typeDiffSizeMetric),
				Pair.of("renamedDiffSizeMetric", renamedDiffSizeMetric),
				Pair.of("countDiffMetric", countDiffMetric),
				Pair.of("jaccard", jaccard),
				Pair.of("renamedJaccard", renamedJaccard),
				Pair.of("jaro", jaro),
				Pair.of("renamedJaro", renamedJaro));
	}
	
}
