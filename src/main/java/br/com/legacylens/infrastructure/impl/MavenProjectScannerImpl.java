package br.com.legacylens.infrastructure.impl;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import br.com.legacylens.infrastructure.util.LegacyHeuristicsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 📘 Analisa projetos Maven:
 * - Lê pom.xml via MavenXpp3Reader
 * - Detecta Java, Spring e Boot versions (com fallback inteligente)
 * - Aplica heurísticas de bibliotecas e frameworks
 */
@Slf4j
@Component
public class MavenProjectScannerImpl implements ProjectScannerPort {

    @Override
    public ProjectScan scan(String dir) {
        try {
            Path pom = Path.of(dir).resolve("pom.xml");
            log.info("📖 Lendo pom.xml em {}", pom);

            Model model = new MavenXpp3Reader().read(new FileReader(pom.toFile()));

            // 🔹 Detectar versão do Java
            String javaVersion = model.getProperties() != null
                    ? model.getProperties().getProperty("java.version",
                    model.getProperties().getProperty("maven.compiler.source"))
                    : null;

            // 🔹 Mapear dependências
            var libs = new HashMap<String, String>();
            if (model.getDependencies() != null) {
                model.getDependencies().forEach(d -> {
                    String key = d.getGroupId() + ":" + d.getArtifactId();
                    String version = d.getVersion() != null ? d.getVersion() : "unspecified";
                    libs.put(key, version);
                });
            }

            // 🔹 Detectar Spring Boot via <parent>
            String bootVersion = detectBootVersion(model);
            String springVersion = detectSpringVersion(libs, bootVersion);

            // 🔹 Aplicar heurísticas do código-fonte (arquitetura, log, testes)
            libs.putAll(LegacyHeuristicsUtil.detectLibrariesFromSource(Path.of(dir)));

            // 🔹 Log formatado
            log.info("""
                    ✅ Projeto Maven analisado:
                      • Java: {}
                      • Spring: {}
                      • Boot: {}
                      • Libs detectadas: {}
                    """, javaVersion, springVersion, bootVersion, libs.size());

            return new ProjectScan("MAVEN", javaVersion, springVersion, bootVersion, libs);

        } catch (Exception e) {
            log.error("❌ Erro ao analisar projeto Maven: {}", e.getMessage(), e);
            return new ProjectScan("MAVEN_ERROR", null, null, null, Map.of());
        }
    }

    // ==========================================================
    // 🔧 Métodos auxiliares
    // ==========================================================

    private String detectBootVersion(Model model) {
        try {
            Parent parent = model.getParent();
            if (parent != null && "org.springframework.boot".equals(parent.getGroupId())) {
                return parent.getVersion();
            }
            // fallback via propriedades
            if (model.getProperties() != null) {
                String bootProp = model.getProperties().getProperty("spring-boot.version");
                if (bootProp != null && !bootProp.isBlank()) return bootProp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String detectSpringVersion(Map<String, String> libs, String fallbackBoot) {
        return libs.entrySet().stream()
                .filter(e -> e.getKey().contains("spring-core") || e.getKey().contains("spring-context"))
                .map(Map.Entry::getValue)
                .filter(v -> v != null && !v.equalsIgnoreCase("unspecified"))
                .findFirst()
                .orElse(fallbackBoot != null ? fallbackBoot : "Desconhecida");
    }
}
