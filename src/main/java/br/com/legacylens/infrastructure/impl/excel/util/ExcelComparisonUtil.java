package br.com.legacylens.infrastructure.impl.excel.util;

import br.com.legacylens.domain.model.ProjectScan;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🧠 ExcelComparisonUtil
 * -------------------------------------------------------------
 * Gera comparações "Legado vs Novo" com base em ProjectScan.
 * Lê dados do record (javaVersion, springVersion, springBootVersion)
 * e do mapa libraries (arquitetura, logging, testes, etc).
 *
 * 100% resiliente, sem NPE e com fallback automático.
 */
@Slf4j
public class ExcelComparisonUtil {

    /**
     * Representa uma linha de comparação entre sistema legado e novo.
     */
    public record Comparison(
            String item,
            String legacy,
            String modern,
            String status,
            String recommendation
    ) {}

    /**
     * Monta as comparações principais com base no ProjectScan.
     */
    public static List<Comparison> buildComparisons(ProjectScan scan) {
        List<Comparison> list = new ArrayList<>();

        if (scan == null) {
            log.warn("⚠️ ProjectScan nulo — retornando comparações padrão.");
            return defaultComparisons();
        }

        try {
            // 1️⃣ Linguagem e Framework base
            list.add(compare("Java",
                    nvl(scan.javaVersion()), "17 / 21 LTS",
                    compareJava(scan.javaVersion())));

            list.add(compare("Spring",
                    nvl(scan.springVersion()), "6.1.x",
                    compareSpring(scan.springVersion())));

            list.add(compare("Spring Boot",
                    nvl(scan.springBootVersion()), "3.5.x",
                    compareBoot(scan.springBootVersion())));

            // 2️⃣ Detecção de bibliotecas (fallback seguro)
            Map<String, String> libs = Optional.ofNullable(scan.libraries())
                    .orElse(Collections.emptyMap());

            String arch = libs.getOrDefault("architecture", libs.getOrDefault("arch", "-"));
            String log = libs.getOrDefault("logging", libs.getOrDefault("logger", "-"));
            String web = libs.getOrDefault("webFramework", libs.getOrDefault("framework", "-"));
            String test = libs.getOrDefault("test", libs.getOrDefault("tests", "-"));

            // 3️⃣ Comparações adicionais via Map
            list.add(compare("Arquitetura", nvl(arch), "Clean / Hexagonal", compareArch(arch)));
            list.add(compare("Framework Web", nvl(web), "Spring REST", compareFramework(web)));
            list.add(compare("Logging", nvl(log), "SLF4J + Logback", compareLogging(log)));
            list.add(compare("Testes", nvl(test), "JUnit 5", compareTests(test)));

        } catch (Exception e) {
            log.error("❌ Erro ao montar comparações: {}", e.getMessage(), e);
            return defaultComparisons();
        }

        return list;
    }

    // ==========================================================
    // 🧩 Comparações e fallback padrão
    // ==========================================================
    private static List<Comparison> defaultComparisons() {
        return List.of(
                new Comparison("Java", "-", "17 / 21 LTS", "⚠️ Atualizar", "Atualizar para versão LTS suportada"),
                new Comparison("Spring Boot", "-", "3.5.x", "⚠️ Atualizar", "Migrar para Spring Boot 3.x"),
                new Comparison("Arquitetura", "-", "Clean / Hexagonal", "⚠️ Atualizar", "Revisar padrão arquitetural")
        );
    }

    private static Comparison compare(String item, String legacy, String modern, String[] info) {
        return new Comparison(item, legacy, modern, info[0], info[1]);
    }

    // ==========================================================
    // 🧠 Heurísticas de comparação
    // ==========================================================
    private static String[] compareJava(String legacy) {
        if (legacy == null || legacy.isBlank())
            return arr("❌ Legado", "Versão não detectada — migrar para Java 17 ou 21 LTS");

        if (legacy.startsWith("8"))
            return arr("❌ Legado", "Migrar de Java 8 → 17 ou 21 LTS");
        if (legacy.startsWith("11"))
            return arr("⚠️ Atualizar", "Atualizar para Java 17 LTS");
        if (legacy.startsWith("17") || legacy.startsWith("21"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Verificar compatibilidade com LTS recente");
    }

    private static String[] compareSpring(String legacy) {
        if (legacy == null)
            return arr("⚠️ Atualizar", "Versão não detectada — atualizar para Spring 6.x");

        if (legacy.startsWith("4"))
            return arr("❌ Legado", "Migrar de Spring 4 → 6.x");
        if (legacy.startsWith("5"))
            return arr("⚠️ Atualizar", "Atualizar para Spring 6.x");
        if (legacy.startsWith("6"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Revisar versão — alvo Spring 6.x");
    }

    private static String[] compareBoot(String legacy) {
        if (legacy == null)
            return arr("⚠️ Atualizar", "Versão não detectada — migrar para Spring Boot 3.x");

        if (legacy.startsWith("1") || legacy.startsWith("2"))
            return arr("⚠️ Atualizar", "Atualizar para Spring Boot 3.5.x (Jakarta)");
        if (legacy.startsWith("3"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Verificar compatibilidade com Spring Boot 3.x");
    }

    private static String[] compareArch(String legacy) {
        if (legacy == null || legacy.equals("-"))
            return arr("⚠️ Atualizar", "Arquitetura não detectada — revisar estrutura do projeto");

        String lower = legacy.toLowerCase();
        if (lower.contains("jee") || lower.contains("ejb") || lower.contains("monolito"))
            return arr("⚠️ Atualizar", "Migrar para arquitetura modular (Clean, Hexagonal)");
        if (lower.contains("clean") || lower.contains("hexagonal"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Revisar padrão arquitetural — adotar Clean Architecture");
    }

    private static String[] compareFramework(String legacy) {
        if (legacy == null || legacy.equals("-"))
            return arr("⚠️ Atualizar", "Framework não detectado — definir padrão REST");

        String lower = legacy.toLowerCase();
        if (lower.contains("jsf") || lower.contains("servlet"))
            return arr("❌ Legado", "Migrar para REST com Spring MVC");
        if (lower.contains("spring") || lower.contains("rest"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Verificar padrão REST / MVC moderno");
    }

    private static String[] compareLogging(String legacy) {
        if (legacy == null || legacy.equals("-"))
            return arr("⚠️ Atualizar", "Framework de log não detectado — usar SLF4J + Logback");

        String lower = legacy.toLowerCase();
        if (lower.contains("log4j"))
            return arr("❌ Legado", "Migrar para SLF4J + Logback (Log4J obsoleto)");
        if (lower.contains("slf4j") || lower.contains("logback"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Verificar compatibilidade com SLF4J");
    }

    private static String[] compareTests(String legacy) {
        if (legacy == null || legacy.equals("-"))
            return arr("⚠️ Atualizar", "Framework de testes não detectado — usar JUnit 5");

        String lower = legacy.toLowerCase();
        if (lower.contains("junit4"))
            return arr("⚠️ Atualizar", "Atualizar de JUnit 4 → 5");
        if (lower.contains("junit5") || lower.contains("jupiter"))
            return arr("✅ Atual", "Sem ação necessária");

        return arr("⚠️ Atualizar", "Adotar JUnit 5 como padrão de testes");
    }

    // ==========================================================
    // 🔧 Helpers utilitários
    // ==========================================================
    private static String[] arr(String a, String b) {
        return new String[]{a, b};
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }

    /**
     * Retorna apenas os itens que precisam de atenção (não “✅ Atual”).
     */
    public static List<Comparison> filterOutdated(List<Comparison> all) {
        if (all == null) return List.of();
        return all.stream()
                .filter(c -> !Objects.equals(c.status(), "✅ Atual"))
                .toList();
    }
}
