import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;

import java.util.*;
import java.util.function.Function;

public class Application {
	
	// TODO: should be an arg
	// if avg metric is < threshold --> issue plagiarism warning
	// TODO: very simple heuristic, maybe change into something more sophisticated (e.g., via exchangeable interface
	//  implementation (strategy pattern) "interface PlagDetection { boolean isPlag(Map<String, Double> metrics) }")
	private static final double THRESHOLD = 0.05;
	
	private static <E> E extractArg(String[] args, String arg, Function<String, E> transformer, E defaultVal) {
		// TODO: assumes arg-pairs: [arg_name_1, arg_value_1, ..., arg_name_n, arg_value_n]
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals(arg)) {
				return transformer.apply(args[i + 1]);
			}
		}
		if (defaultVal != null) {
			return defaultVal;
		}
		throw new IllegalArgumentException("Argument " + arg + " could not be found");
	}
	
	private static <E> List<E> extractListArg(String[] args, String arg, Function<String, E> transformer) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals(arg)) {
				// assume all following args are list values until we encounter "-XYZ" or "--XYZ", i.e., a new argument
				List<E> list = new ArrayList<>();
				for (int j = i + 1; j < args.length && !args[j].startsWith("-") && !args[j].startsWith("--"); j++) {
					list.add(transformer.apply(args[j]));
				}
				return Collections.unmodifiableList(list);
			}
		}
		throw new IllegalArgumentException("Argument " + arg + " could not be found");
	}
	
	public static void main(String[] args) {
		List<String> folders = extractListArg(args, "--folders", Function.identity());
		Set<String> excludedTypeNames = Set.of("In", "Out"); // TODO: hard-coded, solve via arg
		int verbosity = extractArg(args, "--verbosity", Integer::parseInt, 0);
		List<FolderComparison> comparisons = compare(folders, excludedTypeNames, verbosity);
		createCSV(comparisons);
	}
	
	private static List<FolderComparison> compare(List<String> folders, Set<String> excludedTypeNames, int verbosity) {
		List<FolderComparison> results = new ArrayList<>();
		// TODO: parallelize
		for (int i = 0; i < folders.size() - 1; i++) {
			for (int j = i + 1; j < folders.size(); j++) {
				results.add(compareFolders(folders.get(i), folders.get(j), excludedTypeNames, verbosity));
			}
		}
		return results;
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
		
		ASTRenamer renamer1 = new ASTRenamer(folder1, false, false);
		ASTRenamer renamer2 = new ASTRenamer(folder2, false, false);
//		renamer1.rename();
//		renamer2.rename();
		List<CtType<?>> types1 = renamer1.getTopLevelTypes().stream().filter(t -> !excludedTypeNames.contains(t.getSimpleName())).toList();
		List<CtType<?>> types2 = renamer2.getTopLevelTypes().stream().filter(t -> !excludedTypeNames.contains(t.getSimpleName())).toList();
		
		FolderComparison folderComparison = new FolderComparison(folder1, folder2);
		for (CtType<?> type1 : types1) {
			CtType<?> matchingType = findMatchingType(type1, types2, renamer1, renamer2, verbosity);
			List<Pair<String, Double>> metrics = computeMetrics(type1, matchingType, renamer1.renameType(type1), renamer2.renameType(matchingType));
			folderComparison.addTypeComparison(new TypeComparison(type1, matchingType, metrics));
		}
		return folderComparison;
	}
	
	private static CtType<?> findMatchingType(CtType<?> type1, Iterable<CtType<?>> types2, ASTRenamer renamer1, ASTRenamer renamer2, int verbosity) {
		AstComparator comparator = new AstComparator();
		int count1 = countElements(type1);
		CtType<?> renamedType1 = renamer1.renameType(type1);
		
		Map<CtType<?>, List<Integer>> comparison = new HashMap<>();
		// TODO: currently, these 4 are hard-coded; could make this more generic to allow arbitrary diffs
		ArrayList<Pair<CtType<?>, Integer>> typeDiffSizes = new ArrayList<>();
		ArrayList<Pair<CtType<?>, Integer>> renamedTypeDiffSizes = new ArrayList<>();
		ArrayList<Pair<CtType<?>, Integer>> countDiffs = new ArrayList<>();
		ArrayList<Pair<CtType<?>, Integer>> levenDiffs = new ArrayList<>();
		
		if (verbosity >= 2) {
			System.out.println("Comparing '" + type1.getSimpleName() + "'");
		}
		for (CtType<?> type2 : types2) {
			comparison.put(type2, new ArrayList<>());
			
			Diff typeDiff = comparator.compare(type1, type2);
			int typeDiffSize = typeDiff.getRootOperations().size();
			typeDiffSizes.add(Pair.of(type2, typeDiffSize));
			
			CtType<?> renamedType2 = renamer2.renameType(type2);
			Diff renamedTypeDiff = comparator.compare(renamedType1, renamedType2);
			int renamedTypeDiffSize = renamedTypeDiff.getRootOperations().size();
			renamedTypeDiffSizes.add(Pair.of(type2, renamedTypeDiffSize));
			
			int count2 = countElements(type2);
			int countDiff = Math.abs(count1 - count2);
			countDiffs.add(Pair.of(type2, countDiff));
			
			int levenDiff = new LevenshteinDistance().apply(type1.getSimpleName(), type2.getSimpleName());
			levenDiffs.add(Pair.of(type2, levenDiff));
			
			if (verbosity >= 2) {
				System.out.format("  to %25s: typeDiffSize = %3d, renamedTypeDiffSize = %3d, countDiff = %4d, levenDiff = %3d\n", "'" + type2.getSimpleName() + "'", typeDiffSize, renamedTypeDiffSize, countDiff, levenDiff);
			}
		}
		
		// TODO: comments do not include levenDiff
		// For each diff list, sort according to the minimum diff and then assign values 1, 2, 3, ... to the
		// respective AST elements (the various "type2" from above). Afterwards, for each AST element (for each
		// "type2" from above), we have a normalized sorting. Example:
		// Comparing 'Exercise1' ("type1")
		//   to OneDimensionalArray ("type2"), typeDiffSize = 55, renamedTypeDiffSize = 52, countDiff =  11
		//   to      StoryGenerator ("type2"), typeDiffSize = 70, renamedTypeDiffSize = 63, countDiff =  36
		//   to      TextConversion ("type2"), typeDiffSize = 58, renamedTypeDiffSize = 59, countDiff =  46
		//   to       TwoDimensions ("type2"), typeDiffSize = 93, renamedTypeDiffSize = 97, countDiff = 284
		// This will be mapped to:
		// Comparing 'Exercise1' ("type1")
		//   to OneDimensionalArray ("type2"), typeDiffSize = 1, renamedTypeDiffSize = 1, countDiff = 1
		//   to      StoryGenerator ("type2"), typeDiffSize = 3, renamedTypeDiffSize = 3, countDiff = 2
		//   to      TextConversion ("type2"), typeDiffSize = 2, renamedTypeDiffSize = 2, countDiff = 3
		//   to       TwoDimensions ("type2"), typeDiffSize = 4, renamedTypeDiffSize = 4, countDiff = 4
		sortAndAddNormalized(typeDiffSizes, comparison);
		sortAndAddNormalized(renamedTypeDiffSizes, comparison);
		sortAndAddNormalized(countDiffs, comparison);
		sortAndAddNormalized(levenDiffs, comparison);
		
		// With the new, normalized mapping from above, we can now look for the "best" match, where "best" is simply
		// defined as the minimum product of an AST element's (a "type2"'s) differences. The product (compared to
		// summing the differences) has the advantage that multiple low values are more heavily rewarded, e.g., if
		// there are the diffs (1, 1, 4) and (2, 2, 1), the product (4 vs. 9) would still favor the first diff,
		// whereas the sum (8 vs. 7) would favor the second diff, which probably follows the intuition that the
		// first diff is likely the matching one ("two best scores vs. probably one outlier"). Of course, this is
		// just a heuristic and can lead to false results. Example from above:
		// Comparing 'Exercise1' ("type1")
		//   to OneDimensionalArray ("type2"), typeDiffSize = 1, renamedTypeDiffSize = 1, countDiff = 1 --> product =  1
		//   to      StoryGenerator ("type2"), typeDiffSize = 3, renamedTypeDiffSize = 3, countDiff = 2 --> product = 18
		//   to      TextConversion ("type2"), typeDiffSize = 2, renamedTypeDiffSize = 2, countDiff = 3 --> product = 12
		//   to       TwoDimensions ("type2"), typeDiffSize = 4, renamedTypeDiffSize = 4, countDiff = 4 --> product = 64
		// In this case, the 'OneDimensionalArray' AST element ("type2") will be selected as the best match for the
		// original AST element 'Exercise1' ("type1").
		return comparison.entrySet().stream()
				.map(entry -> Pair.of(entry.getKey(), entry.getValue().stream()
						.mapToInt(x -> x)
						.reduce(1, (a, x) -> a * x)))
				.min(Comparator.comparingInt(Pair::getRight))
				.map(Pair::getLeft)
				.orElseThrow();
	}
	
	private static void sortAndAddNormalized(ArrayList<Pair<CtType<?>, Integer>> diffs, Map<CtType<?>, List<Integer>> comparison) {
		diffs.sort(Comparator.comparingInt(Pair::getRight));
		int i = 1;
		for (Pair<CtType<?>, Integer> pair : diffs) {
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
	
	// TODO: currently just printing without verbosity check
	private static List<Pair<String, Double>> computeMetrics(CtType<?> type1, CtType<?> type2, CtType<?> renamedType1, CtType<?> renamedType2) {
		AstComparator comparator = new AstComparator();
		System.out.println("Comparing '" + type1.getSimpleName() + "' to " + "'" + type2.getSimpleName() + "'");
		Diff typeDiff = comparator.compare(type1, type2);
		int typeDiffSize = typeDiff.getRootOperations().size();
		System.out.println("AST changes (before renaming): " + typeDiffSize);
		Diff renamedTypeDiff = comparator.compare(renamedType1, renamedType2);
		int renamedTypeDiffSize = renamedTypeDiff.getRootOperations().size();
		System.out.println("AST changes  (after renaming): " + renamedTypeDiffSize);
		int count1 = countElements(type1);
		int count2 = countElements(type2);
		int countDiff = Math.abs(count1 - count2);
		System.out.println("AST element count difference: " + countDiff);
		System.out.println();
		
		double typeDiffSizeMetric = (double) typeDiffSize / Math.max(count1, count2);
		double renamedDiffSizeMetric = (double) renamedTypeDiffSize / Math.max(count1, count2);
		double countDiffMetric = (double) countDiff / Math.max(count1, count2);
		double jaccard = 1 - new JaccardSimilarity().apply(type1.toString(), type2.toString());
		double renamedJaccard = 1 - new JaccardSimilarity().apply(renamedType1.toString(), renamedType2.toString());
		double jaro = 1 - new JaroWinklerSimilarity().apply(type1.toString(), type2.toString());
		double renamedJaro = 1 - new JaroWinklerSimilarity().apply(renamedType1.toString(), renamedType2.toString());
		// TODO: include average here as metric?
		
		return List.of(
				Pair.of("typeDiffSizeMetric", typeDiffSizeMetric),
				Pair.of("renamedDiffSizeMetric", renamedDiffSizeMetric),
				Pair.of("countDiffMetric", countDiffMetric),
				Pair.of("jaccard", jaccard),
				Pair.of("renamedJaccard", renamedJaccard),
				Pair.of("jaro", jaro),
				Pair.of("renamedJaro", renamedJaro));
	}
	
	private static void createCSV(List<FolderComparison> folderComparisons) {
		// TODO: replace standard output printing with writing a CSV
		boolean first = true;
		for (FolderComparison fc : folderComparisons) {
			if (first) {
				System.out.println(fc.getCSVHeader());
				first = false;
			}
			System.out.println(fc.toCSVString());
			for (TypeComparison tc : fc.getTypeComparisons()) {
				double avg = tc.getMetrics().stream().mapToDouble(Pair::getRight).average().orElseThrow();
//				System.out.printf("average = %.3f\n", avg);
//				if (avg < THRESHOLD) {
//					System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//					System.out.printf("average (%.3f) is < threshold (%.3f)\n", avg, THRESHOLD);
//					System.out.println("folder1: " + folder1);
//					System.out.println("folder2: " + folder2);
//					System.out.println("type1: " + type1.getSimpleName());
//					System.out.println("type2: " + matchingType.getSimpleName());
//					System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//					System.out.println();
//				}
			}
		}
	}
	
}
