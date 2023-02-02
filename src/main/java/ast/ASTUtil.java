package ast;

import spoon.reflect.declaration.CtElement;

public class ASTUtil {
	
	/**
	 * Returns the number of elements of <code>element</code> and all of its children (recursively). The returned value
	 * is at least 1 (<code>element</code> itself if it does not have any children).
	 *
	 * @param element The root element to start counting at
	 * @return The number of elements of <code>element</code> and all of its children
	 */
	public static int countElements(CtElement element) {
		int count = 1;
		for (CtElement e : element.getDirectChildren()) {
			count += countElements(e);
		}
		return count;
	}
	
}
