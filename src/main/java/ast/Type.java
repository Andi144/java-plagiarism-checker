package ast;

import spoon.reflect.declaration.CtType;

public record Type(CtType<?> original, CtType<?> renamed) {
	
	public String getOriginalName() {
		return original.getSimpleName();
	}
	
}
