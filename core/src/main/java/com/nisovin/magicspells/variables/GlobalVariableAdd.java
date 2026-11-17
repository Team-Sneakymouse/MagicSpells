package com.nisovin.magicspells.variables;

/**
 * Whether a numeric global variable mod should use SneakyVariables {@code add}
 * (concurrent-safe across servers) instead of a computed set.
 */
public final class GlobalVariableAdd {

	private GlobalVariableAdd() {
	}

	/** True for VariableMod.Operation.ADD ({@code +}/{@code -} prefixes). */
	public static boolean shouldUseAdd(String operationName) {
		return "ADD".equals(operationName);
	}

}
