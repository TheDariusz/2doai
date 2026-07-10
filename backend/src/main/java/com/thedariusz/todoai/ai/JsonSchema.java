package com.thedariusz.todoai.ai;

import java.util.Map;

/**
 * A named JSON Schema used to constrain a structured completion. Sent in the
 * OpenAI-compatible {@code response_format: {type: "json_schema", json_schema: {name,
 * strict: true, schema}}} block so the provider returns one valid JSON object.
 *
 * @param name   schema name (provider requires a name alongside the schema)
 * @param schema the JSON Schema as a nested map (e.g. {@code {"type":"object", ...}})
 */
public record JsonSchema(String name, Map<String, Object> schema) {

	public JsonSchema {
		schema = Map.copyOf(schema);
	}
}
