package com.nisovin.magicspells.util;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import static org.bukkit.attribute.AttributeModifier.Operation;

public class AttributeUtil {

	private static final Map<String, Attribute> attributeNameMap = new HashMap<>();
	private static final Map<String, Operation> operationNameMap = new HashMap<>();

	static {
		operationNameMap.put(AttributeOperationNames.ADD_NUMBER, Operation.ADD_NUMBER);
		operationNameMap.put(AttributeOperationNames.ADD_SCALAR, Operation.ADD_SCALAR);
		operationNameMap.put(AttributeOperationNames.MULTIPLY_SCALAR_1, Operation.MULTIPLY_SCALAR_1);
		for (String alias : new String[]{
			"addnumber", "add_scalar", "addscalar",
			"multiply_scalar_1", "multiplyscalar1", "multiply_scalar", "multiplyscalar"
		}) {
			String canonical = AttributeOperationNames.canonicalize(alias);
			if (canonical != null) operationNameMap.put(alias, operationNameMap.get(canonical));
		}

		for (Attribute attribute : Registry.ATTRIBUTE) {
			String key = attribute.getKey().getKey();
			for (String alias : AttributeNameAliases.candidates(key)) {
				attributeNameMap.putIfAbsent(alias, attribute);
			}
		}
	}

	public static Attribute getAttribute(String attribute) {
		if (attribute == null || attribute.isBlank()) return null;
		for (String candidate : AttributeNameAliases.candidates(attribute)) {
			Attribute resolved = attributeNameMap.get(candidate);
			if (resolved != null) return resolved;
		}
		return null;
	}

	public static Operation getOperation(String operation) {
		String canonical = AttributeOperationNames.canonicalize(operation);
		if (canonical != null) return operationNameMap.get(canonical);
		return operationNameMap.get(operation.toLowerCase());
	}

}
