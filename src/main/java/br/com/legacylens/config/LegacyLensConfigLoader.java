package br.com.legacylens.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 🔧 Carrega o legacylens.yml e aplica heurísticas automáticas.
 * Detecta arquitetura (Spring, Camel, etc.), multi-módulo e ajusta configuração.
 */
@Slf4j
@Component
public class LegacyLensConfigLoader {

    @Value("${legacylens.config.enabled:true}")
    private boolean yamlEnabled;

    @Value("${legacylens.config.prefer-external:true}")
    private boolean preferExternal;

    @Value("${legacylens.config.location:}")
    private String yamlLocation;

    private static LegacyLensConfig config;
    private static Path lastAnalyzedPath;

    @PostConstruct
    public void init() {
        if (!yamlEnabled) {
            config = new LegacyLensConfig();
            log.warn("⚠️ YAML desativado — aplicando defaults.");
            return;
        }

        config = loadYamlConfig();
        if (config == null) config = new LegacyLensConfig();

        // Garantia extra: nunca deixar sequence nula
        if (config.getSequence() == null) {
            config.setSequence(new LegacyLensConfig.Sequence());
            config.getSequence().setEnabled(true);
        }

        log.info("✅ LegacyLensConfig carregado (multiModule={} sequence={})",
                config.getExecution().isDetectMultiModule(), config.getSequence().isEnabled());
    }

    // ============================================================
    // 🔹 Carregamento do YAML
    // ============================================================
    private LegacyLensConfig loadYamlConfig() {
        try {
            InputStream inputStream = null;
            Path external = Path.of("legacylens.yml");

            if (yamlLocation != null && !yamlLocation.isBlank()) {
                Path path = Path.of(yamlLocation.replace("file:", ""));
                if (Files.exists(path)) inputStream = Files.newInputStream(path);
            }
            if (inputStream == null && preferExternal && Files.exists(external)) {
                inputStream = Files.newInputStream(external);
            }
            if (inputStream == null) {
                ClassPathResource internal = new ClassPathResource("legacylens.yml");
                if (internal.exists()) inputStream = internal.getInputStream();
            }
            if (inputStream == null) return new LegacyLensConfig();

            LegacyLensConfig loaded = new Yaml().loadAs(inputStream, LegacyLensConfig.class);
            return (loaded != null) ? loaded : new LegacyLensConfig();

        } catch (Exception e) {
            log.error("❌ Erro ao carregar YAML: {}", e.getMessage(), e);
            return new LegacyLensConfig();
        }
    }

    // ============================================================
    // 🧠 Heurística automática — applyAutoIntelligence
    // ============================================================
    public static void applyAutoIntelligence(Path projectPath) {
        if (projectPath == null || !Files.exists(projectPath)) {
            log.warn("⚠️ Caminho inválido para aplicar inteligência automática.");
            return;
        }
        lastAnalyzedPath = projectPath;

        try {
            long javaFiles = countJavaFiles(projectPath);
            String architecture = detectArchitecture(projectPath);

            boolean isSpring = architecture.contains("Spring");
            boolean isCamunda = architecture.contains("Camunda");
            boolean isCamel = architecture.contains("Camel");
            boolean isFeign = architecture.contains("Feign");
            boolean isJakarta = architecture.contains("Jakarta");
            boolean isLarge = javaFiles > 500;

            log.info("🧠 AutoIntelligence: arquitetura={} | classes={}", architecture, javaFiles);

            // === Ajuste de sequence ===
            if (isLarge) {
                config.getSequence().setEnabled(false);
                log.warn("⚠️ Sequence Diagram desativado (projeto grande ou legado).");
            } else {
                config.getSequence().setEnabled(true);
            }

            // === Ajuste de multi-módulo ===
            if (isSpring || isCamunda || isCamel || isFeign) {
                config.getExecution().setDetectMultiModule(true);
                log.info("🧩 Modo multi-módulo ativado automaticamente (arquitetura moderna).");
            } else {
                config.getExecution().setDetectMultiModule(false);
                log.info("📦 Projeto simples — multi-módulo desativado.");
            }

            // === Verifica build directory ===
            Path target = projectPath.resolve("target/classes");
            if (!Files.exists(target)) {
                log.warn("⚠️ Sem diretório de compilação — ativando fallback manual.");
                config.getUml().setFallbackEnabled(true);
            }

        } catch (Exception e) {
            log.warn("⚠️ Falha ao aplicar inteligência automática: {}", e.getMessage());
        }
    }

    // ============================================================
    // 🔧 Utilitários internos
    // ============================================================
    private static long countJavaFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String detectArchitecture(Path srcDir) {
        try (Stream<Path> stream = Files.walk(srcDir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            String content = Files.readString(p);
                            if (content.contains("@RestController") || content.contains("@SpringBootApplication"))
                                return "Spring Boot";
                            if (content.contains("Camunda") || content.contains("ProcessEngine"))
                                return "Camunda BPM";
                            if (content.contains("camelContext") || content.contains("RouteBuilder"))
                                return "Apache Camel";
                            if (content.contains("@FeignClient"))
                                return "Feign Client";
                            if (content.contains("jakarta.persistence") || content.contains("@Entity"))
                                return "Jakarta EE / JPA";
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Java Puro");
        } catch (IOException e) {
            return "Desconhecida";
        }
    }

    // ============================================================
    // 🌍 Métodos públicos
    // ============================================================
    public static LegacyLensConfig get() {
        if (config == null) config = new LegacyLensConfig();
        if (config.getSequence() == null) {
            config.setSequence(new LegacyLensConfig.Sequence());
            config.getSequence().setEnabled(true);
        }
        return config;
    }

    public static void reload() {
        log.info("🔄 Recarregando configurações do LegacyLens...");
        LegacyLensConfigLoader loader = new LegacyLensConfigLoader();
        config = loader.loadYamlConfig();
        if (lastAnalyzedPath != null) applyAutoIntelligence(lastAnalyzedPath);
    }
}
