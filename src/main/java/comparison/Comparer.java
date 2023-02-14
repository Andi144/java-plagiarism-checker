package comparison;

import ast.ASTRenamer;
import ast.Type;
import comparison.scoring.metrics.*;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class Comparer {
	
	private final TypeMatcher typeMatcher;
	private final List<MetricScorer> metricScorers;
	private final boolean parallel;
	
	/**
	 * Creates a new instance with a default {@link TypeMatcher#TypeMatcher() TypeMatcher} and the following default
	 * list of {@link MetricScorer}s:
	 * <ul>
	 *     <li>{@link ASTDiffMetricScorer#ASTDiffMetricScorer(boolean) ASTDiffMetricScorer(false)}</li>
	 *     <li>{@link ASTDiffMetricScorer#ASTDiffMetricScorer(boolean) ASTDiffMetricScorer(true)}</li>
	 *     <li>{@link ASTCountDiffMetricScorer#ASTCountDiffMetricScorer(boolean) ASTDiffMetricScorer(false)}</li>
	 *     <li>{@link JaccardMetricScorer#JaccardMetricScorer(boolean) JaccardMetricScorer(false)}</li>
	 *     <li>{@link JaccardMetricScorer#JaccardMetricScorer(boolean) JaccardMetricScorer(true)}</li>
	 *     <li>{@link JaroWinklerMetricScorer#JaroWinklerMetricScorer(boolean) JaroWinklerMetricScorer(false)}</li>
	 *     <li>{@link JaroWinklerMetricScorer#JaroWinklerMetricScorer(boolean) JaroWinklerMetricScorer(true)}</li>
	 * </ul>
	 *
	 * @param parallel Whether to use parallelism
	 * @see #Comparer(TypeMatcher, List, boolean)
	 */
	public Comparer(boolean parallel) {
		this(new TypeMatcher(), List.of(
				new ASTDiffMetricScorer(false),
				new ASTDiffMetricScorer(true),
				new ASTCountDiffMetricScorer(false),
				new JaccardMetricScorer(false),
				new JaccardMetricScorer(true),
				new JaroWinklerMetricScorer(false),
				new JaroWinklerMetricScorer(true)
		), parallel);
	}
	
	/**
	 * Creates a new instance using the specified arguments.
	 *
	 * @param typeMatcher   The type matcher to use when comparing the individual types of two folders in
	 *                      {@link #compare(List, Set)}. For any two folders, this will result in pairs of matching
	 *                      types
	 * @param metricScorers The list of {@link MetricScorer}s to compute metrics for each identified pair of matching
	 *                      types (see <code>typeMatcher</code>)
	 * @param parallel      Whether to use parallelism
	 */
	public Comparer(TypeMatcher typeMatcher, List<MetricScorer> metricScorers, boolean parallel) {
		this.typeMatcher = typeMatcher;
		this.metricScorers = metricScorers;
		this.parallel = parallel;
	}
	
	/**
	 * Using the {@link TypeMatcher} and {@link MetricScorer}s specified in the constructor
	 * ({@link #Comparer(TypeMatcher, List, boolean)}), creates all possible combinations of two folders taken from the
	 * list <code>folders</code> and computes a {@link FolderComparison} for each of those folder pairs. In case any of
	 * the two folders within a folder pair does not contain any types that could be compared, this folder pair is
	 * dropped and no comparison is computed.
	 *
	 * @param folders           The list of folders for which all possible pairs will be created and used for comparison
	 * @param excludedTypeNames The set of type names that should be excluded from any comparison within two folders
	 * @return A list of {@link FolderComparison}s for each folder pair
	 */
	public List<FolderComparison> compare(List<String> folders, Set<String> excludedTypeNames) {
		List<Pair<String, String>> folderPairs = new ArrayList<>();
		for (int i = 0; i < folders.size() - 1; i++) {
			for (int j = i + 1; j < folders.size(); j++) {
				folderPairs.add(Pair.of(folders.get(i), folders.get(j)));
			}
		}
		try (ProgressBar pb = new ProgressBar("Comparing folders", folderPairs.size())) {
			Stream<Pair<String, String>> folderPairsStream = parallel ? folderPairs.parallelStream() : folderPairs.stream();
			return folderPairsStream.map(p -> {
				FolderComparison comparison = compareFolders(p.getLeft(), p.getRight(), excludedTypeNames);
				pb.step();
				return comparison;
			}).filter(Objects::nonNull).toList();
		}
	}
	
	private FolderComparison compareFolders(String folder1, String folder2, Set<String> excludedTypeNames) {
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
			Type matchingType = typeMatcher.findMatchingType(type1, types2);
			List<Pair<MetricScorer, Double>> metrics = computeMetrics(type1, matchingType);
			folderComparison.addTypeComparison(new TypeComparison(
					type1.original().getPosition().getCompilationUnit().getFile().toPath(),
					matchingType.original().getPosition().getCompilationUnit().getFile().toPath(),
					type1.getOriginalName(),
					matchingType.getOriginalName(),
					metrics
			));
		}
		return folderComparison;
	}
	
	private List<Pair<MetricScorer, Double>> computeMetrics(Type type1, Type type2) {
		return metricScorers.stream()
				.map(s -> Pair.of(s, s.computeComparisonScore(type1, type2)))
				.toList();
	}
	
}
