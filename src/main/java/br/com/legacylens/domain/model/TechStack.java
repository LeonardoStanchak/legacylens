package br.com.legacylens.domain.model;

import java.util.List;

public record TechStack(
        String javaVersion,
        String springVersion,
        String buildTool,
        List<String> dependencies
) {}
