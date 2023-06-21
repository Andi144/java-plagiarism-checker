package util;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class ArgumentParser {
	
	/**
	 * Represents an argument and methods for extracting its value ({@link #extract(int, String[])}) and retrieving its
	 * (optional) default value ({@link #getDefault()}).
	 *
	 * @param <E> The type of the argument value
	 */
	private abstract static class Argument<E> {
		
		final String name;
		
		Argument(String name) {
			this.name = name;
		}
		
		/**
		 * Extracts the argument value. The index <code>i</code> specifies the position in <code>args</code> where this
		 * argument is located, i.e., {@link #name} is equal to <code>args[i]</code>. This method must then extract the
		 * argument's value and return it together with the next index to parse, which is the index position of the next
		 * argument in <code>args</code> (or the end of the <code>args</code> array if no other argument follows). In
		 * other words, all indices starting from <code>i</code> that are part of this argument (the name and its value)
		 * are consumed by this method.
		 *
		 * @param i    The index of this argument in <code>args</code>
		 * @param args The entire array of specified command line arguments.
		 * @return Pair where the first entry is the next index to parse in <code>args</code>, and the second entry is
		 * the extracted argument value
		 */
		abstract Pair<Integer, E> extract(int i, String[] args);
		
		/**
		 * Returns the default argument value, or throws an exception in case such a default argument value is not
		 * supported.
		 *
		 * @return The default argument value
		 */
		E getDefault() {
			throw new IllegalArgumentException("argument was not specified but does not have a default value: " + name);
		}
		
	}
	
	private class ValueArgument<E> extends Argument<E> {
		
		final Function<String, E> typeConverter;
		
		ValueArgument(String name, Function<String, E> typeConverter) {
			super(name);
			this.typeConverter = typeConverter;
		}
		
		@Override
		public Pair<Integer, E> extract(int i, String[] args) {
			// Expectation:
			// [i] = arg name
			// [i + 1] = arg value
			// [i + 2] = next arg name
			// Disallow that the value of this argument here is equal to another, already specified argument name. This
			// could cause tricky errors, e.g., if we allowed another argument name "x" as value here, then it would be
			// consumed. Afterward, an exception would be raised because "x" could not be found, which might cause
			// confusion to users, since they did indeed specify "x", just that it was done incorrectly, so better not
			// allow it in the first place
			if (i + 1 >= args.length || arguments.containsKey(args[i + 1])) {
				throw new IllegalArgumentException("no value specified for argument: " + args[i]);
			}
			return Pair.of(i + 2, typeConverter.apply(args[i + 1]));
		}
		
	}
	
	private class DefaultValueArgument<E> extends ValueArgument<E> {
		
		final E defaultValue;
		
		DefaultValueArgument(String name, Function<String, E> typeConverter, E defaultValue) {
			super(name, typeConverter);
			this.defaultValue = defaultValue;
		}
		
		@Override
		E getDefault() {
			return defaultValue;
		}
		
	}
	
	private class CollectionArgument<E, C extends Collection<E>> extends Argument<C> {
		
		final Function<String, E> typeConverter;
		final Supplier<C> collectionSupplier;
		
		CollectionArgument(String name, Function<String, E> typeConverter, Supplier<C> collectionSupplier) {
			super(name);
			this.typeConverter = typeConverter;
			this.collectionSupplier = collectionSupplier;
		}
		
		@Override
		public Pair<Integer, C> extract(int i, String[] args) {
			C collection = collectionSupplier.get();
			i++;
			// Collect elements until we encounter another, already specified argument name. This also means that such a
			// collection cannot contain elements that are equally named as existing argument names, which is fine,
			// however, as in such a special case, the users would then just have to figure out unique names
			while (i < args.length && !arguments.containsKey(args[i])) {
				collection.add(typeConverter.apply(args[i]));
				i++;
			}
			return Pair.of(i, collection);
		}
		
	}
	
	private class DefaultCollectionArgument<E, C extends Collection<E>> extends CollectionArgument<E, C> {
		
		final C defaultValue;
		
		DefaultCollectionArgument(String name, Function<String, E> typeConverter, Supplier<C> collectionSupplier, C defaultValue) {
			super(name, typeConverter, collectionSupplier);
			this.defaultValue = defaultValue;
		}
		
		@Override
		C getDefault() {
			return defaultValue;
		}
		
	}
	
	private static class BooleanArgument extends Argument<Boolean> {
		
		BooleanArgument(String name) {
			super(name);
		}
		
		@Override
		Pair<Integer, Boolean> extract(int i, String[] args) {
			// If the argument is specified, return true
			return Pair.of(i + 1, true);
		}
		
		@Override
		Boolean getDefault() {
			// If the argument is not specified, return false
			return false;
		}
		
	}
	
	/**
	 * The argument names mapped to their corresponding {@link Argument} objects.
	 */
	private final Map<String, Argument<?>> arguments;
	/**
	 * The argument names mapped to their parsed values. This map is cleared and updated with each invocation of
	 * {@link #parse(String[])}.
	 */
	private final Map<String, Object> parsedArguments;
	/**
	 * A list of argument name groups, where each such group defines a set of argument names that must not occur
	 * together, i.e., a set of mutually exclusive arguments.
	 */
	private final List<Set<String>> mutuallyExclusiveGroups;
	
	public ArgumentParser() {
		arguments = new HashMap<>();
		parsedArguments = new HashMap<>();
		mutuallyExclusiveGroups = new ArrayList<>();
	}
	
	/**
	 * Adds a required string argument.
	 *
	 * @param name The name of the argument
	 */
	public void addArgument(String name) {
		addArgument(name, Function.identity());
	}
	
	/**
	 * Adds a required argument.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the argument's value
	 * @param <E>           The type of the argument value
	 */
	public <E> void addArgument(String name, Function<String, E> typeConverter) {
		ensureNewArgumentName(name);
		arguments.put(name, new ValueArgument<>(name, typeConverter));
	}
	
	/**
	 * Adds an optional string argument.
	 *
	 * @param name         The name of the argument
	 * @param defaultValue The default string value if the argument is not specified
	 */
	public void addArgument(String name, String defaultValue) {
		addArgument(name, Function.identity(), defaultValue);
	}
	
	/**
	 * Adds an optional argument.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the argument's value
	 * @param defaultValue  The default value if the argument is not specified
	 * @param <E>           The type of the argument value
	 */
	public <E> void addArgument(String name, Function<String, E> typeConverter, E defaultValue) {
		ensureNewArgumentName(name);
		arguments.put(name, new DefaultValueArgument<>(name, typeConverter, defaultValue));
	}
	
	/**
	 * Adds a required string list argument, i.e., an argument which contains multiple string values that are stored in
	 * a list.
	 *
	 * @param name The name of the argument
	 */
	public void addListArgument(String name) {
		addListArgument(name, Function.identity());
	}
	
	/**
	 * Adds a required list argument, i.e., an argument which contains multiple values that are stored in a list.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the individual list values
	 * @param <E>           The type of the individual list values
	 */
	public <E> void addListArgument(String name, Function<String, E> typeConverter) {
		addCollectionArgument(name, typeConverter, ArrayList::new);
	}
	
	/**
	 * Adds an optional string list argument, i.e., an argument which contains multiple string values that are stored in
	 * a list.
	 *
	 * @param name         The name of the argument
	 * @param defaultValue The default string list if the argument is not specified
	 */
	public void addListArgument(String name, List<String> defaultValue) {
		addListArgument(name, Function.identity(), defaultValue);
	}
	
	/**
	 * Adds an optional list argument, i.e., an argument which contains multiple values that are stored in a list.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the individual list values
	 * @param <E>           The type of the individual list values
	 * @param defaultValue  The default list if the argument is not specified
	 */
	public <E> void addListArgument(String name, Function<String, E> typeConverter, List<E> defaultValue) {
		addCollectionArgument(name, typeConverter, ArrayList::new, defaultValue);
	}
	
	/**
	 * Adds a required string set argument, i.e., an argument which contains multiple string values that are stored in a
	 * set.
	 *
	 * @param name The name of the argument
	 */
	public void addSetArgument(String name) {
		addSetArgument(name, Function.identity());
	}
	
	/**
	 * Adds a required set argument, i.e., an argument which contains multiple values that are stored in a set.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the individual set values
	 * @param <E>           The type of the individual set values
	 */
	public <E> void addSetArgument(String name, Function<String, E> typeConverter) {
		addCollectionArgument(name, typeConverter, HashSet::new);
	}
	
	/**
	 * Adds an optional string set argument, i.e., an argument which contains multiple string values that are stored in
	 * a set.
	 *
	 * @param name         The name of the argument
	 * @param defaultValue The default string set if the argument is not specified
	 */
	public void addSetArgument(String name, Set<String> defaultValue) {
		addSetArgument(name, Function.identity(), defaultValue);
	}
	
	/**
	 * Adds an optional set argument, i.e., an argument which contains multiple values that are stored in a set.
	 *
	 * @param name          The name of the argument
	 * @param typeConverter The function to parse the individual set values
	 * @param defaultValue  The default set if the argument is not specified
	 * @param <E>           The type of the individual set values
	 */
	public <E> void addSetArgument(String name, Function<String, E> typeConverter, Set<E> defaultValue) {
		addCollectionArgument(name, typeConverter, HashSet::new, defaultValue);
	}
	
	/**
	 * Adds a required string collection argument, i.e., an argument which contains multiple string values that are
	 * stored in a specified collection.
	 *
	 * @param name               The name of the argument
	 * @param collectionSupplier The function to provide a string collection container
	 */
	public void addCollectionArgument(String name, Supplier<? extends Collection<String>> collectionSupplier) {
		addCollectionArgument(name, Function.identity(), collectionSupplier);
	}
	
	/**
	 * Adds a required collection argument, i.e., an argument which contains multiple values that are stored in a
	 * specified collection.
	 *
	 * @param name               The name of the argument
	 * @param typeConverter      The function to parse the individual collection values
	 * @param collectionSupplier The function to provide a collection container
	 * @param <E>                The type of the individual collection values
	 * @param <C>                The type of the collection
	 */
	public <E, C extends Collection<E>> void addCollectionArgument(String name, Function<String, E> typeConverter, Supplier<C> collectionSupplier) {
		ensureNewArgumentName(name);
		arguments.put(name, new CollectionArgument<>(name, typeConverter, collectionSupplier));
	}
	
	/**
	 * Adds an optional collection argument, i.e., an argument which contains multiple values that are stored in a
	 * specified collection.
	 *
	 * @param name               The name of the argument
	 * @param typeConverter      The function to parse the individual collection values
	 * @param collectionSupplier The function to provide a collection container
	 * @param defaultValue       The default collection if the argument is not specified
	 * @param <E>                The type of the individual collection values
	 * @param <C>                The type of the collection
	 */
	public <E, C extends Collection<E>> void addCollectionArgument(String name, Function<String, E> typeConverter, Supplier<C> collectionSupplier, C defaultValue) {
		ensureNewArgumentName(name);
		arguments.put(name, new DefaultCollectionArgument<>(name, typeConverter, collectionSupplier, defaultValue));
	}
	
	/**
	 * Adds an optional boolean argument, i.e., a flag/toggle. If it is specified, its value will be <code>true</code>
	 * and <code>false</code> otherwise (i.e., if it is not specified).
	 *
	 * @param name The name of the argument
	 */
	public void addBooleanArgument(String name) {
		ensureNewArgumentName(name);
		arguments.put(name, new BooleanArgument(name));
	}
	
	private void ensureNewArgumentName(String name) {
		if (arguments.containsKey(name)) {
			throw new IllegalArgumentException("argument was already added: " + name);
		}
	}
	
	/**
	 * Adds a mutually exclusive group of arguments. Argument names specified here must not occur together.
	 *
	 * @param names The argument names that should form a mutually exclusive group
	 */
	public void addMutuallyExclusiveArguments(String... names) {
		if (names.length < 2) {
			throw new IllegalArgumentException("mutual exclusivity can only be specified for at least two arguments");
		}
		mutuallyExclusiveGroups.add(Set.of(names));
	}
	
	/**
	 * Parses the specified command line arguments <code>args</code> using all the arguments that were added before (see
	 * {@link #addArgument(String, Function)} and all other similar methods that add arguments). If mutually exclusive
	 * arguments have been specified with {@link #addMutuallyExclusiveArguments(String...)}, then this is also checked.
	 * After having invoked this method, the parsed argument values can be retrieved with {@link #get(String)}.
	 *
	 * @param args The command line arguments to parse
	 */
	public void parse(String[] args) {
		// Keep track of all the arguments we use explicitly (those are the ones where the user specified command line
		// arguments), and for the remaining ones (those are the ones where the user did not specify command line
		// arguments), use default values as per Argument.getDefault()
		Map<String, Argument<?>> remainingArguments = new HashMap<>(arguments);
		parsedArguments.clear();
		// Only a single argument name (value) of a mutually exclusive group (key) can be specified
		Map<Set<String>, String> nameFromMutuallyExclusiveGroup = new HashMap<>();
		int i = 0;
		while (i < args.length) {
			String name = args[i];
			if (!arguments.containsKey(name)) {
				throw new IllegalArgumentException("specification of unrecognized argument: " + name);
			}
			if (parsedArguments.containsKey(name)) {
				throw new IllegalArgumentException("duplicate argument specification: " + name);
			}
			for (Set<String> mutuallyExclusiveGroup : mutuallyExclusiveGroups) {
				if (mutuallyExclusiveGroup.contains(name)) {
					if (nameFromMutuallyExclusiveGroup.containsKey(mutuallyExclusiveGroup)) {
						String alreadyDefinedName = nameFromMutuallyExclusiveGroup.get(mutuallyExclusiveGroup);
						throw new IllegalArgumentException("specification of mutually exclusive arguments: " + name +
								", " + alreadyDefinedName + " (part of group: " + mutuallyExclusiveGroup + ")");
					}
					nameFromMutuallyExclusiveGroup.put(mutuallyExclusiveGroup, name);
				}
			}
			remainingArguments.remove(name);
			Pair<Integer, ?> transformed = arguments.get(name).extract(i, args);
			i = transformed.getLeft();
			parsedArguments.put(name, transformed.getRight());
		}
		remainingArguments.forEach((name, argument) -> parsedArguments.put(name, argument.getDefault()));
	}
	
	/**
	 * After having called {@link #parse(String[])}, returns the parsed argument value for the specified argument
	 * <code>name</code>.
	 * <p>
	 * The type of the value is an unchecked cast, so care must be taken to retrieve the same type this argument was
	 * initially added with (see {@link #addArgument(String, Function)} and all other similar methods that add arguments
	 * including a specified type). For example:
	 * <pre>{@code
	 *     argumentParser.addArgument("--x", Integer::parseInt);
	 *     argumentParser.parse(args);
	 *     String x = argumentParser.get("--x"); // ClassCastException
	 * }</pre>
	 * will fail at run time. Another example with collections, where this error occurs even later:
	 * <pre>{@code
	 *     argumentParser.addCollectionArgument("--numbers", Integer::parseInt, ArrayList::new);
	 *     argumentParser.parse(args);
	 *     List<String> numbers = argumentParser.get("--numbers"); // no exception yet
	 *     String firstNumber = numbers.get(i); // ClassCastException
	 * }</pre>
	 *
	 * @param name The name of the argument whose parsed value should be returned
	 * @param <E>  The type of the parsed argument value
	 * @return The parsed argument value for argument <code>name</code>
	 */
	@SuppressWarnings("unchecked")
	public <E> E get(String name) {
		if (!parsedArguments.containsKey(name)) {
			String message = "parsed arguments do not contain: " + name;
			if (parsedArguments.isEmpty() && !arguments.isEmpty()) {
				message += " (did you call ArgumentParser.parse(String[] args) ?)";
			}
			throw new IllegalArgumentException(message);
		}
		return (E) parsedArguments.get(name);
	}
	
}
