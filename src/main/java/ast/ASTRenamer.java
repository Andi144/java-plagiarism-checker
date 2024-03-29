package ast;

import org.apache.commons.lang3.tuple.Pair;
import spoon.Launcher;
import spoon.refactoring.CtRenameGenericVariableRefactoring;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.function.BiConsumer;

public class ASTRenamer {
	
	private static class RenamingData {
		
		final boolean includeCount;
		final boolean countGlobally;
		private final String template;
		private int count;
		
		RenamingData(boolean includeCount, boolean countGlobally, String template) {
			this.includeCount = includeCount;
			this.countGlobally = countGlobally;
			this.template = template;
			count = 0;
		}
		
		String createName() {
			String name = template;
			if (includeCount) {
				name += count;
				count++;
			}
			return name;
		}
		
		void resetCount() {
			count = 0;
		}
		
	}
	
	private final CtModel model;
	private final RenamingData typeData;
	private final RenamingData fieldData;
	private final RenamingData methodData;
	private final RenamingData parameterData;
	private final RenamingData localVariableData;
	private final List<Type> types;
	
	public ASTRenamer(String path, Set<String> excludedTypeNames, boolean keepComments, boolean includeCount) {
		this(path, excludedTypeNames, keepComments, includeCount, false);
	}
	
	public ASTRenamer(String path, Set<String> excludedTypeNames, boolean keepComments, boolean includeCount, boolean countGlobally) {
		this(path, excludedTypeNames, keepComments,
				includeCount, includeCount, includeCount, includeCount, includeCount,
				countGlobally, countGlobally, countGlobally, countGlobally, countGlobally,
				"__type__", "__field__", "__method__", "__parameter__", "__localVariable__");
	}
	
	// TODO: maybe put "path" into "rename" method, so the same renaming settings can be used for multiple files, and
	//  then return the model in "rename"; also, this would mean that we lose the state, so methods like "toString" and
	//  "getTopLevelTypes" would not make sense afterwards anymore (ast.ASTRenamer is stateless w.r.t. to the renamed models)
	//
	// TODO: There is a spoon problem when includeTypeCount is false: If so, then renaming types naturally leads to the same
	//  template name T. If there are types A, B and C, and A uses B (e.g., as a field), then this only works if the order
	//  of renaming is A and then directly followed by B, i.e., there must not be any type in between the two. If there
	//  is, the following happens: A is renamed to T, and then let's say type C is next in the processing order. C is now
	//  also renamed to T. With this, the unique connection to B (e.g., the field) is apparently lost in spoon, and then
	//  renaming B (to also T) has no effect on A, i.e., the field B remains in (the renamed) type A. This can easily be
	//  resolved by setting includeTypeCount to True (and countTypesGlobally also set to True) , but then, the new names will
	//  (of course) be different and once again  dependent on the processing order (e.g., for A, B, C it would be T1, T2, T3,
	//  but for A, C, B it would also be T1, T2, T3, i.e., B and C will be named differently albeit the actual same types).
	//  Both settings are thus not ideal  when calculating the renaming-based metrics afterward, so one has to choose the
	//  lesser of two evils (whatever that is). Another suboptimal solution would be to make copies of all types before
	//  the renaming process. This way, all types will be decoupled, which, however, means that, e.g., A's field referencing
	//  B would stay the same, i.e., it would not be renamed at all anymore (regardless of the type processing order)
	// String path should be Path path (consistency)
	public ASTRenamer(
			String path, Set<String> excludedTypeNames, boolean keepComments,
			boolean includeTypeCount, boolean includeFieldCount, boolean includeMethodCount, boolean includeParameterCount, boolean includeLocalVariableCount,
			boolean countTypesGlobally, boolean countFieldsGlobally, boolean countMethodsGlobally, boolean countParametersGlobally, boolean countLocalVariablesGlobally,
			String typeTemplate, String fieldTemplate, String methodTemplate, String parameterTemplate, String localVariableTemplate
	) {
		Launcher launcher = new Launcher();
		// path can be a folder or a file
		// addInputResource can be called several times
		launcher.addInputResource(path);
		// the compliance level should be set to the java version targeted by the input resources, e.g. Java 17
		launcher.getEnvironment().setComplianceLevel(17);
		launcher.getEnvironment().setCommentEnabled(keepComments);
		launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
		launcher.buildModel();
		model = launcher.getModel();
		
		typeData = new RenamingData(includeTypeCount, countTypesGlobally, typeTemplate);
		fieldData = new RenamingData(includeFieldCount, countFieldsGlobally, fieldTemplate);
		methodData = new RenamingData(includeMethodCount, countMethodsGlobally, methodTemplate);
		parameterData = new RenamingData(includeParameterCount, countParametersGlobally, parameterTemplate);
		localVariableData = new RenamingData(includeLocalVariableCount, countLocalVariablesGlobally, localVariableTemplate);
		
		// Copying decouples the type from the original types (which must also have been copied, there is an effect otherwise!)
		boolean decouple = true; // TODO: parameterize
		List<Pair<CtType<?>, CtType<?>>> originalAndToBeRenamed = new ArrayList<>();
		for (CtType<?> ctType : model.getAllTypes()) {
			if (!excludedTypeNames.contains(ctType.getSimpleName())) {
				// Make sure to copy all original types before to avoid any side effect when changing the toBeRenamed type
				originalAndToBeRenamed.add(Pair.of(ctType.clone(), decouple ? ctType.clone() : ctType));
			}
		}
		List<Type> originalAndRenamed = new ArrayList<>();
		originalAndToBeRenamed.forEach(pair -> {
			CtType<?> original = pair.getLeft();
			CtType<?> toBeRenamed = pair.getRight();
			renameType(toBeRenamed);
			originalAndRenamed.add(new Type(original, toBeRenamed));
		});
		types = Collections.unmodifiableList(originalAndRenamed);
	}
	
	public List<Type> getTypes() {
		return types;
	}
	
	private void renameType(CtType<?> type) {
		rename(List.of(type), typeData, (ctType, newName) -> {
			Refactoring.changeTypeName(ctType, newName);
			renameTypeInternals(ctType);
		});
	}
	
	private void renameTypeInternals(CtType<?> ctType) {
		rename(ctType.getFields().stream()
				.filter(ctField -> !(ctField instanceof CtEnumValue)) // Renaming CtEnumValue fields is not supported by spoon
				.toList(), fieldData, (ctField, newName) -> new CtRenameGenericVariableRefactoring().setTarget(ctField).setNewName(newName).refactor());
		rename(ctType.getMethods(), methodData, (ctMethod, newName) -> {
			Refactoring.changeMethodName(ctMethod, newName);
			renameMethodOrConstructorInternals(ctMethod);
		});
		// Just reuse methodData (we actually do not need to create a constructor/type new name, since this  was already
		// done in renameType(type) at Refactoring.changeTypeName(ctType, newName))
		rename(getConstructors(ctType), methodData, (ctConstructor, newName) -> renameMethodOrConstructorInternals(ctConstructor));
		ctType.getNestedTypes().forEach(this::renameType);
	}
	
	private static List<CtConstructor<?>> getConstructors(CtType<?> ctType) {
		List<CtConstructor<?>> constructors = new ArrayList<>();
		for (CtTypeMember typeMember : ctType.getTypeMembers()) {
			if (typeMember instanceof CtConstructor) {
				constructors.add((CtConstructor<?>) typeMember);
			}
		}
		return constructors;
	}
	
	private void renameMethodOrConstructorInternals(CtExecutable<?> ctExecutable) {
		rename(ctExecutable.getParameters(), parameterData, (ctParameter, newName) -> new CtRenameGenericVariableRefactoring().setTarget(ctParameter).setNewName(newName).refactor());
		rename(ctExecutable.getElements(new TypeFilter<>(CtLocalVariable.class)), localVariableData, (ctLocalVariable, newName) -> {
			// Compared to Refactoring.changeLocalVariableName(ctLocalVariable, newName), this does not perform a check
			// for valid renaming, which is fine since this can happen with according settings (e.g., no counts)
			new CtRenameGenericVariableRefactoring().setTarget(ctLocalVariable).setNewName(newName).refactor();
		});
	}
	
	private <E> void rename(Collection<E> elements, RenamingData data, BiConsumer<E, String> renamer) {
		for (E e : elements) {
			renamer.accept(e, data.createName());
		}
		if (!data.countGlobally) {
			data.resetCount();
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (CtType<?> ctType : model.getAllTypes()) {
			sb.append(ctType.toString());
			sb.append("\n\n");
		}
		return sb.toString();
	}
	
}
