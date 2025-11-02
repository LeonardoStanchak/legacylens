package br.com.legacylens.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
public record ProjectScan(
        String projectType,
        String javaVersion,
        String springVersion,
        String springBootVersion,
        Map<String, String> libraries
) {}
