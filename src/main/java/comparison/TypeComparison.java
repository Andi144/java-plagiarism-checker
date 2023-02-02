package comparison;

import comparison.scoring.metrics.MetricScorer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class TypeComparison {
	
	private final String type1;
	private final String type2;
	private final List<Pair<MetricScorer, Double>> metrics;
	
	public TypeComparison(String type1, String type2, List<Pair<MetricScorer, Double>> metrics) {
		this.type1 = type1;
		this.type2 = type2;
		this.metrics = metrics;
	}
	
	public String getType1() {
		return type1;
	}
	
	public String getType2() {
		return type2;
	}
	
	public List<Pair<MetricScorer, Double>> getMetrics() {
		return Collections.unmodifiableList(metrics);
	}
	
	String getCSVHeader() {
		StringBuilder sb = new StringBuilder("type1,type2");
		metrics.forEach(pair -> {
			sb.append(",");
			sb.append(StringEscapeUtils.escapeCsv(pair.getLeft().getName()));
		});
		return sb.toString();
	}
	
	String getCSVString() {
		StringJoiner sj = new StringJoiner(",");
		sj.add(StringEscapeUtils.escapeCsv(type1));
		sj.add(StringEscapeUtils.escapeCsv(type2));
		metrics.forEach(pair -> sj.add(StringEscapeUtils.escapeCsv(pair.getRight().toString())));
		return sj.toString();
	}
	
}
