package io.nncdevel.codesearch.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** JSON input schemas advertised for each tool (spec §4). */
final class ToolSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@code search_code} input schema. */
    static final Map<String, Object> SEARCH_CODE = parse("""
          {
            "type": "object",
            "properties": {
              "pattern": {"type": "string", "minLength": 1, "maxLength": 1024},
              "repository": {"type": "string", "pattern": "^[a-zA-Z0-9._-]+$", "minLength": 1, "maxLength": 64},
              "path": {"type": "string", "maxLength": 4096},
              "glob": {"type": "string", "maxLength": 256},
              "type": {"type": "string"},
              "case_sensitive": {"type": "boolean", "default": false},
              "output_mode": {"type": "string", "enum": ["content", "files_with_matches", "count"], "default": "content"},
              "context_before": {"type": "integer", "minimum": 0, "maximum": 20, "default": 0},
              "context_after": {"type": "integer", "minimum": 0, "maximum": 20, "default": 0},
              "max_results": {"type": "integer", "minimum": 1, "maximum": 500, "default": 50}
            },
            "required": ["pattern", "repository"],
            "additionalProperties": false
          }
          """);

    /** {@code list_files} input schema. */
    static final Map<String, Object> LIST_FILES = parse("""
          {
            "type": "object",
            "properties": {
              "repository": {"type": "string", "pattern": "^[a-zA-Z0-9._-]+$", "minLength": 1, "maxLength": 64},
              "pattern": {"type": "string", "minLength": 1, "maxLength": 256},
              "path": {"type": "string", "maxLength": 4096},
              "max_results": {"type": "integer", "minimum": 1, "maximum": 500, "default": 100}
            },
            "required": ["repository", "pattern"],
            "additionalProperties": false
          }
          """);

    /** {@code list_tree} input schema. */
    static final Map<String, Object> LIST_TREE = parse("""
          {
            "type": "object",
            "properties": {
              "repository": {"type": "string", "pattern": "^[a-zA-Z0-9._-]+$", "minLength": 1, "maxLength": 64},
              "path": {"type": "string", "maxLength": 4096},
              "max_depth": {"type": "integer", "minimum": 1, "maximum": 5, "default": 2},
              "show_files": {"type": "boolean", "default": true},
              "max_entries": {"type": "integer", "minimum": 1, "maximum": 1000, "default": 200}
            },
            "required": ["repository"],
            "additionalProperties": false
          }
          """);

    /** {@code read_file} input schema. */
    static final Map<String, Object> READ_FILE = parse("""
          {
            "type": "object",
            "properties": {
              "repository": {"type": "string", "pattern": "^[a-zA-Z0-9._-]+$", "minLength": 1, "maxLength": 64},
              "file_path": {"type": "string", "maxLength": 4096},
              "start_line": {"type": "integer", "minimum": 1, "default": 1},
              "num_lines": {"type": "integer", "minimum": 1, "maximum": 2000, "default": 100}
            },
            "required": ["repository", "file_path"],
            "additionalProperties": false
          }
          """);

    /** {@code list_repositories} input schema (no arguments). */
    static final Map<String, Object> LIST_REPOSITORIES =
            parse("{\"type\": \"object\", \"properties\": {}, \"additionalProperties\": false}");

    private ToolSchemas() {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("invalid built-in tool schema", e);
        }
    }
}
