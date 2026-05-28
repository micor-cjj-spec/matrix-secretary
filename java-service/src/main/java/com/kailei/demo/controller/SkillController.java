package com.kailei.demo.controller;

import com.kailei.demo.skill.SkillCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillCatalog skillCatalog;

    public SkillController(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
    }

    @GetMapping
    public Map<String, Object> listSkills() {
        List<Map<String, Object>> skills = skillCatalog.publicViews();
        return Map.of("skills", skills);
    }
}
