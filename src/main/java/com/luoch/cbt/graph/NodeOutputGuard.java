package com.luoch.cbt.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict JSON + enum allow-list + protected-key filtering for every LLM node. */
public final class NodeOutputGuard {

    private final ObjectMapper mapper;

    public NodeOutputGuard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> parse(String raw, NodeSpec spec) throws OutputViolationException {
        Map<String, Object> parsed;
        try {
            parsed = mapper.readValue(stripFence(raw), new TypeReference<Map<String, Object>>() {
            });
        }
        catch (Exception e) {
            throw new OutputViolationException("output is not a JSON object", e);
        }
        if (parsed == null || parsed.isEmpty()) {
            throw new OutputViolationException("empty JSON object");
        }
        for (String key : spec.required()) {
            if (!parsed.containsKey(key)) {
                throw new OutputViolationException("missing required field: " + key);
            }
        }
        for (Map.Entry<String, Set<String>> rule : spec.enums().entrySet()) {
            Object value = parsed.get(rule.getKey());
            if (!(value instanceof String s) || !rule.getValue().contains(s)) {
                throw new OutputViolationException("invalid enum for " + rule.getKey());
            }
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : parsed.entrySet()) {
            if (!StateKeys.PROTECTED.contains(e.getKey())) {
                safe.put(e.getKey(), e.getValue());
            }
        }
        return safe;
    }

    private static String stripFence(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int closing = s.lastIndexOf("```");
            if (closing >= 0) {
                s = s.substring(0, closing);
            }
        }
        return s.trim();
    }

    public static final class OutputViolationException extends Exception {
        public OutputViolationException(String message) {
            super(message);
        }

        public OutputViolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
