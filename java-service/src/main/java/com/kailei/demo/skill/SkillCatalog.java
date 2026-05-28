package com.kailei.demo.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);
    private static final String SKILL_PATTERN = "classpath*:/skills/*/skill.yml";

    private final Map<String, SkillDefinition> skillsByActionType = new LinkedHashMap<>();
    private final Map<String, SkillDefinition> skillsByName = new LinkedHashMap<>();

    public SkillCatalog() {
        loadSkills();
    }

    public List<SkillDefinition> list() {
        return List.copyOf(skillsByName.values());
    }

    public Optional<SkillDefinition> findByActionType(String actionType) {
        return Optional.ofNullable(skillsByActionType.get(actionType));
    }

    public Optional<SkillDefinition> findByName(String name) {
        return Optional.ofNullable(skillsByName.get(name));
    }

    public SkillDefinition getOrUnknown(String actionType) {
        return findByActionType(actionType).orElseGet(() -> SkillDefinition.unknown(actionType));
    }

    public List<Map<String, Object>> publicViews() {
        return list().stream()
                .map(this::toPublicView)
                .toList();
    }

    private Map<String, Object> toPublicView(SkillDefinition skill) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", skill.name());
        view.put("displayName", skill.displayName());
        view.put("description", skill.description());
        view.put("triggerKeywords", skill.triggerKeywords());
        view.put("actionType", skill.actionType());
        view.put("riskLevel", skill.riskLevel());
        view.put("requiresConfirmation", skill.requiresConfirmation());
        view.put("inputSchema", skill.inputSchema());
        return view;
    }

    @SuppressWarnings("unchecked")
    private void loadSkills() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(SKILL_PATTERN);
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Object loaded = yaml.load(inputStream);
                    if (!(loaded instanceof Map<?, ?> raw)) {
                        log.warn("Skip invalid skill file: {}", resource.getFilename());
                        continue;
                    }
                    SkillDefinition skill = toSkillDefinition((Map<String, Object>) raw);
                    register(skill);
                }
            }
            log.info("Loaded {} skill(s): {}", skillsByName.size(), skillsByName.keySet());
        } catch (Exception ex) {
            throw new IllegalStateException("加载 SkillCatalog 失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private SkillDefinition toSkillDefinition(Map<String, Object> raw) {
        String name = stringValue(raw.get("name"));
        String actionType = stringValue(raw.get("actionType"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill.yml 缺少 name");
        }
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("skill.yml 缺少 actionType: " + name);
        }
        Object executionRaw = raw.get("execution");
        SkillExecution execution = executionRaw instanceof Map<?, ?> executionMap
                ? toSkillExecution((Map<String, Object>) executionMap)
                : new SkillExecution("noop", null, null, null, Map.of(), Map.of(), Map.of());

        return new SkillDefinition(
                name,
                defaultString(raw.get("displayName"), name),
                defaultString(raw.get("description"), ""),
                stringList(raw.get("triggerKeywords")),
                actionType,
                defaultString(raw.get("riskLevel"), "LOW"),
                booleanValue(raw.get("requiresConfirmation")),
                mapValue(raw.get("inputSchema")),
                execution
        );
    }

    private SkillExecution toSkillExecution(Map<String, Object> raw) {
        return new SkillExecution(
                defaultString(raw.get("type"), "noop"),
                stringValue(raw.get("executor")),
                stringValue(raw.get("method")),
                stringValue(raw.get("url")),
                stringMap(raw.get("headers")),
                mapValue(raw.get("query")),
                mapValue(raw.get("body"))
        );
    }

    private void register(SkillDefinition skill) {
        if (skillsByName.containsKey(skill.name())) {
            throw new IllegalArgumentException("重复的 skill name: " + skill.name());
        }
        if (skillsByActionType.containsKey(skill.actionType())) {
            throw new IllegalArgumentException("重复的 actionType: " + skill.actionType());
        }
        skillsByName.put(skill.name(), skill);
        skillsByActionType.put(skill.actionType(), skill);
    }

    private static String defaultString(Object value, String fallback) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> out = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, val) -> out.put(String.valueOf(key), val));
            return out;
        }
        return Map.of();
    }

    private static Map<String, String> stringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            map.forEach((key, val) -> out.put(String.valueOf(key), val == null ? "" : String.valueOf(val)));
            return out;
        }
        return Map.of();
    }
}
