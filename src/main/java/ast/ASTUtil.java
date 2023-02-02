package ast;

import spoon.reflect.declaration.CtElement;

public class ASTUtil {
	
	public static int countElements(CtElement element) {
		int count = 1;
		for (CtElement e : element.getDirectChildren()) {
			count += countElements(e);
		}
		return count;
	}
	
}
