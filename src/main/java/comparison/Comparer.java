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
	private final boolean parallel;
	private final List<MetricScorer> metricScorers;
	
	public Comparer(boolean parallel) {
		this(new TypeMatcher(), parallel, List.of(
				new ASTDiffMetricScorer(false),
				new ASTDiffMetricScorer(true),
				new ASTCountDiffMetricScorer(false),
				new JaccardMetricScorer(false),
				new JaccardMetricScorer(true),
				new JaroWinklerMetricScorer(false),
				new JaroWinklerMetricScorer(true)
		));
	}
	
	public Comparer(TypeMatcher typeMatcher, boolean parallel, List<MetricScorer> metricScorers) {
		this.typeMatcher = typeMatcher;
		this.parallel = parallel;
		this.metricScorers = metricScorers;
	}
	
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
			List<Pair<String, Double>> metrics = computeMetrics(type1, matchingType);
			folderComparison.addTypeComparison(new TypeComparison(type1.getOriginalName(), matchingType.getOriginalName(), metrics));
		}
		return folderComparison;
	}
	
	private List<Pair<String, Double>> computeMetrics(Type type1, Type type2) {
		return metricScorers.stream()
				.map(s -> Pair.of(s.getName(), s.computeComparisonScore(type1, type2)))
				.toList();
	}
	
}
