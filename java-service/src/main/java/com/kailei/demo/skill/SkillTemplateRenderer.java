package com.kailei.demo.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.model.TaskAction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillTemplateRenderer {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public SkillTemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> renderMap(Map<String, Object> template, TaskAction action) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            out.put(entry.getKey(), renderValue(entry.getValue(), action));
        }
        return out;
    }

    public Object renderValue(Object value, TaskAction action) {
        if (value instanceof String text) {
            return renderString(text, action);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, val) -> out.put(String.valueOf(key), renderValue(val, action)));
            return out;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Object item : iterable) {
                out.add(renderValue(item, action));
            }
            return out;
        }
        return value;
    }

    public String renderString(String template, TaskAction action) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object replacement = resolve(matcher.group(1), action);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? "" : String.valueOf(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path, TaskAction action) {
        Map<String, Object> args = action.args() == null ? Map.of() : action.args();
        Map<String, Object> root = objectMapper.convertValue(action, new TypeReference<>() {
        });
        root.put("args", args);

        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
