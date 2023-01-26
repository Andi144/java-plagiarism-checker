package util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

// TODO: currently static-only, which requires every method to fully parse the entire args-array (should replace with
//  object-oriented design to keep track of all arguments and only requiring one parsing pass)
public class ArgParsing {
	
	private ArgParsing() {
	}
	
	public static <E> E extractArg(String[] args, String arg, Function<String, E> transformer) {
		return extractArg(args, arg, transformer, null);
	}
	
	public static <E> E extractArg(String[] args, String arg, Function<String, E> transformer, E defaultVal) {
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
	
	public static Set<String> extractSetArg(String[] args, String arg) {
		return extractSetArg(args, arg, Function.identity());
	}
	
	public static <E> Set<E> extractSetArg(String[] args, String arg, Function<String, E> transformer) {
		return extractCollectionArg(args, arg, transformer, HashSet::new);
	}
	
	public static List<String> extractListArg(String[] args, String arg) {
		return extractListArg(args, arg, Function.identity());
	}
	
	public static <E> List<E> extractListArg(String[] args, String arg, Function<String, E> transformer) {
		return extractCollectionArg(args, arg, transformer, ArrayList::new);
	}
	
	public static <E, C extends Collection<E>> C extractCollectionArg(String[] args, String arg, Function<String, E> transformer, Supplier<C> collectionSupplier) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals(arg)) {
				// assume all following args are collection values until we encounter "-XYZ" or "--XYZ", i.e., a new argument
				C c = collectionSupplier.get();
				for (int j = i + 1; j < args.length && !args[j].startsWith("-") && !args[j].startsWith("--"); j++) {
					c.add(transformer.apply(args[j]));
				}
				return c;
			}
		}
		throw new IllegalArgumentException("Argument " + arg + " could not be found");
	}
	
}
