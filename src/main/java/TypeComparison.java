import org.apache.commons.lang3.tuple.Pair;
import spoon.reflect.declaration.CtType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

// TODO: maybe include folders here as well and drop FolderComparison (so that this here becomes a complete "CSV row")
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
		return metrics;
	}
	
	public String getCSVHeader() {
		StringBuilder sb = new StringBuilder("type1,type2");
		metrics.forEach(pair -> {
			sb.append(",");
			sb.append(pair.getLeft());
		});
		return sb.toString();
	}
	
	public String toCSVString() {
		StringJoiner sj = new StringJoiner(",");
		sj.add(type1.getSimpleName()).add(type2.getSimpleName());
		metrics.forEach(pair -> sj.add(pair.getRight().toString()));
		return sj.toString();
	}
	
}
