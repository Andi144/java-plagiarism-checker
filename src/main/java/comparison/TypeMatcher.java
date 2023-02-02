package comparison;

import ast.Type;
import comparison.scoring.ASTCountDiffScorer;
import comparison.scoring.ASTDiffScorer;
import comparison.scoring.LevenshteinNameScorer;
import comparison.scoring.TypeComparisonScorer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class TypeMatcher {
	
	private final List<TypeComparisonScorer> typeComparisonScorers;
	
	/**
	 * Creates a new instance with the following default list of {@link TypeComparisonScorer}s:
	 * <ul>
	 *     <li>{@link ASTDiffScorer#ASTDiffScorer(boolean) ASTDiffScorer(false)}</li>
	 *     <li>{@link ASTDiffScorer#ASTDiffScorer(boolean) ASTDiffScorer(true)}</li>
	 *     <li>{@link ASTCountDiffScorer#ASTCountDiffScorer(boolean) ASTCountDiffScorer(false)}</li>
	 *     <li>{@link LevenshteinNameScorer}</li>
	 * </ul>
	 *
	 * @see #TypeMatcher(List)
	 */
	public TypeMatcher() {
		this(List.of(
				new ASTDiffScorer(false),
				new ASTDiffScorer(true),
				new ASTCountDiffScorer(false),
				new LevenshteinNameScorer()
		));
	}
	
	/**
	 * Creates a new instance with the specified <code>typeComparisonScorers</code>.
	 *
	 * @param typeComparisonScorers The list of {@link TypeComparisonScorer}s to compute scores for all possible pairs
	 *                              of types in {@link #findMatchingType(Type, List)}
	 */
	public TypeMatcher(List<TypeComparisonScorer> typeComparisonScorers) {
		this.typeComparisonScorers = typeComparisonScorers;
	}
	
	/**
	 * Using the {@link TypeComparisonScorer}s specified in the constructor ({@link #TypeMatcher(List)}), returns one
	 * type out of <code>candidates</code> which is identified as the best match for <code>type</code>.
	 *
	 * @param type       The type to search a matching candidate for
	 * @param candidates The list of possible candidates, out of which a single one will be selected as the one that
	 *                   matches <code>type</code> best
	 * @return A single type out of <code>candidates</code> that best matches <code>type</code>
	 */
	public Type findMatchingType(Type type, List<Type> candidates) {
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("candidates must not be empty");
		}
		
		// Maps each candidate type to a list of its scores that were computed with all "typeComparisonScorers"
		Map<Type, List<Integer>> comparison = new HashMap<>();
		
		for (TypeComparisonScorer scorer : typeComparisonScorers) {
			List<Pair<Type, Double>> scores = new ArrayList<>(candidates.size());
			for (Type candidate : candidates) {
				double score = scorer.computeComparisonScore(type, candidate);
				scores.add(Pair.of(candidate, score));
			}
			// For each score list, sort according to the minimum diff and then assign values 1, 2, 3, ... to the
			// respective types (the various "candidates" from above). Afterwards, for each type (for each "candidate"
			// from above), we have a normalized scores. Example for three scores scoreA, scoreB and scoreC:
			// Comparing 'Exercise1' ("type")
			//   to OneDimensionalArray ("candidate"), scoreA = 55, scoreB = 52, scoreC =  11
			//   to      StoryGenerator ("candidate"), scoreA = 70, scoreB = 63, scoreC =  36
			//   to      TextConversion ("candidate"), scoreA = 58, scoreB = 59, scoreC =  46
			//   to       TwoDimensions ("candidate"), scoreA = 93, scoreB = 97, scoreC = 284
			// This will be mapped to:
			// Comparing 'Exercise1' ("type")
			//   to OneDimensionalArray ("candidate"), scoreA = 1, scoreB = 1, scoreC = 1
			//   to      StoryGenerator ("candidate"), scoreA = 3, scoreB = 3, scoreC = 2
			//   to      TextConversion ("candidate"), scoreA = 2, scoreB = 2, scoreC = 3
			//   to       TwoDimensions ("candidate"), scoreA = 4, scoreB = 4, scoreC = 4
			sortAndAddNormalized(scores, comparison);
		}
		
		// With the new, normalized mapping from above, we can now look for the "best" match, where "best" is simply
		// defined as the minimum product of a types (a "candidate"'s) differences. The product (compared to summing the
		// differences) has the advantage that multiple low values are more heavily rewarded, e.g., if there are the
		// scores (1, 1, 4) and (2, 2, 1), the product (4 vs. 9) would still favor the first type, whereas the sum
		// (8 vs. 7) would favor the second type, which probably follows the intuition that the first type is likely the
		// matching one ("two best scores vs. probably one outlier"). Of course, this is just a heuristic and can lead
		// to false results. Example from above:
		// Comparing 'Exercise1' ("type")
		//   to OneDimensionalArray ("candidate"), scoreA = 1, scoreB = 1, scoreC = 1 --> product =  1
		//   to      StoryGenerator ("candidate"), scoreA = 3, scoreB = 3, scoreC = 2 --> product = 18
		//   to      TextConversion ("candidate"), scoreA = 2, scoreB = 2, scoreC = 3 --> product = 12
		//   to       TwoDimensions ("candidate"), scoreA = 4, scoreB = 4, scoreC = 4 --> product = 64
		// In this case, the 'OneDimensionalArray' type ("candidate") will be selected as the best match for the
		// original type 'Exercise1' ("type")
		return comparison.entrySet().stream()
				.map(entry -> Pair.of(entry.getKey(), entry.getValue().stream()
						.mapToInt(x -> x)
						.reduce(1, (a, x) -> a * x)))
				.min(Comparator.comparingInt(Pair::getRight))
				.map(Pair::getLeft)
				.orElseThrow();
	}
	
	private static void sortAndAddNormalized(List<Pair<Type, Double>> diffs, Map<Type, List<Integer>> comparison) {
		diffs.sort(Comparator.comparingDouble(Pair::getRight));
		int i = 1;
		for (Pair<Type, Double> pair : diffs) {
			comparison.computeIfAbsent(pair.getLeft(), k -> new ArrayList<>()).add(i);
			i++;
		}
	}
	
}
