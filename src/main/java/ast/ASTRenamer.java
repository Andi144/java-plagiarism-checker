package ast;

import spoon.Launcher;
import spoon.refactoring.CtRenameGenericVariableRefactoring;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
	
	private final Launcher launcher;
	private final CtModel model;
	private final RenamingData typeData;
	private final RenamingData fieldData;
	private final RenamingData methodData;
	private final RenamingData parameterData;
	private final RenamingData localVariableData;
	
	public ASTRenamer(String path) {
		this(path, true);
	}
	
	public ASTRenamer(String path, boolean keepComments) {
		this(path, keepComments, true, false);
	}
	
	public ASTRenamer(String path, boolean keepComments, boolean includeCount) {
		this(path, keepComments, includeCount, false);
	}
	
	public ASTRenamer(String path, boolean keepComments, boolean includeCount, boolean countGlobally) {
		this(path, keepComments,
				includeCount, includeCount, includeCount, includeCount, includeCount,
				countGlobally, countGlobally, countGlobally, countGlobally, countGlobally,
				"__type__", "__field__", "__method__", "__parameter__", "__localVariable__");
	}
	
	// TODO: maybe put "path" into "rename" method, so the same renaming settings can be used for multiple files, and
	//  then return the model in "rename"; also, this would mean that we lose the state, so methods like "toString" and
	//  "getTopLevelTypes" would not make sense afterwards anymore (ast.ASTRenamer is stateless w.r.t. to the renamed models)
	public ASTRenamer(
			String path, boolean keepComments,
			boolean includeTypeCount, boolean includeFieldCount, boolean includeMethodCount, boolean includeParameterCount, boolean includeLocalVariableCount,
			boolean countTypesGlobally, boolean countFieldsGlobally, boolean countMethodsGlobally, boolean countParametersGlobally, boolean countLocalVariablesGlobally,
			String typeTemplate, String fieldTemplate, String methodTemplate, String parameterTemplate, String localVariableTemplate
	) {
		this.launcher = new Launcher();
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
	}
	
	// TODO: temp!
	public CtType<?> renameType(CtType<?> type) {
		CtType<?> copiedType = type.clone(); //Refactoring.copyType(type);
		rename(List.of(copiedType), typeData, (ctType, newName) -> {
			Refactoring.changeTypeName(ctType, newName);
			renameTypeInternals(ctType);
		});
		return copiedType;
	}
	
	public void rename() {
		// "model.getAllTypes()" only identified top-level types (not nested types), so filter by type
		rename(model.getElements(new TypeFilter<>(CtType.class)), typeData, (ctType, newName) -> {
			Refactoring.changeTypeName(ctType, newName);
			renameTypeInternals(ctType);
		});
	}
	
	private void renameTypeInternals(CtType<?> ctType) {
		rename(ctType.getFields(), fieldData, (ctField, newName) -> new CtRenameGenericVariableRefactoring().setTarget(ctField).setNewName(newName).refactor());
		rename(ctType.getMethods(), methodData, (ctMethod, newName) -> {
			Refactoring.changeMethodName(ctMethod, newName);
			renameMethodInternals(ctMethod);
		});
	}
	
	private void renameMethodInternals(CtMethod<?> ctMethod) {
		rename(ctMethod.getParameters(), parameterData, (ctParameter, newName) -> new CtRenameGenericVariableRefactoring().setTarget(ctParameter).setNewName(newName).refactor());
		rename(ctMethod.getElements(new TypeFilter<>(CtLocalVariable.class)), localVariableData, (ctLocalVariable, newName) -> {
			// this performs a check for valid renaming
//			Refactoring.changeLocalVariableName(ctLocalVariable, newName);
			// this does not perform a check for valid renaming
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
	
	public List<CtType<?>> getTopLevelTypes() {
		// TODO: stream?? model.getAllTypes().stream().map(CtType::clone).toList() does not work
		List<CtType<?>> copies = new ArrayList<>();
		model.getAllTypes().forEach(c -> copies.add(c.clone()));
		return Collections.unmodifiableList(copies);
	}
	
	public void writeToFile() {
		// TODO: parameterize
		// TODO: maybe do not even use "launcher" but just do everything manually like:
		//   for (CtType<?> ctType : model.getAllTypes()) {
		//       write ctType.toString to file (with same name as this ctType)
		launcher.prettyprint();
	}
	
}
