package comparison;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import spoon.reflect.declaration.CtType;

import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class TypeComparison {
	
	private final CtType<?> type1;
	private final CtType<?> type2;
	private final List<Pair<String, Double>> metrics;
	
	public TypeComparison(CtType<?> type1, CtType<?> type2, List<Pair<String, Double>> metrics) {
		this.type1 = type1;
		this.type2 = type2;
		this.metrics = metrics;
	}
	
	public CtType<?> getType1() {
		return type1;
	}
	
	public CtType<?> getType2() {
		return type2;
	}
	
	public List<Pair<String, Double>> getMetrics() {
		return Collections.unmodifiableList(metrics);
	}
	
	String getCSVHeader() {
		StringBuilder sb = new StringBuilder("type1,type2");
		metrics.forEach(pair -> {
			sb.append(",");
			sb.append(StringEscapeUtils.escapeCsv(pair.getLeft()));
		});
		return sb.toString();
	}
	
	String getCSVString() {
		StringJoiner sj = new StringJoiner(",");
		sj.add(StringEscapeUtils.escapeCsv(type1.getSimpleName()));
		sj.add(StringEscapeUtils.escapeCsv(type2.getSimpleName()));
		metrics.forEach(pair -> sj.add(StringEscapeUtils.escapeCsv(pair.getRight().toString())));
		return sj.toString();
	}
	
}
