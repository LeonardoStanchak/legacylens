package br.com.legacylens.infrastructure.impl;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class MavenProjectScannerImpl implements ProjectScannerPort {

    @Override
    public ProjectScan scan(String dir) {
        try {
            Path pom = Path.of(dir).resolve("pom.xml");
            log.info("Lendo arquivo pom.xml em {}", pom);
            Model model = new MavenXpp3Reader().read(new FileReader(pom.toFile()));

            // Detectar versão do Java
            String javaVersion = model.getProperties() != null
                    ? model.getProperties().getProperty("java.version",
                    model.getProperties().getProperty("maven.compiler.source"))
                    : null;

            // Mapear dependências
            var libs = new HashMap<String, String>();
            if (model.getDependencies() != null) {
                model.getDependencies().forEach(d ->
                        libs.put(d.getGroupId() + ":" + d.getArtifactId(),
                                d.getVersion() != null ? d.getVersion() : "unspecified"));
            }

            // Detectar Spring Boot no <parent>
            String bootVersion = null;
            if (model.getParent() != null &&
                    "org.springframework.boot".equals(model.getParent().getGroupId())) {
                bootVersion = model.getParent().getVersion();
            }

            // Detectar versão do Spring nas dependências
            String springVersion = libs.keySet().stream()
                    .filter(k -> k.contains("spring-boot") || k.contains("spring-core"))
                    .findFirst()
                    .map(libs::get)
                    .orElse(bootVersion);

            log.info("Projeto Maven analisado com sucesso. Java={}, Spring={}, Boot={}",
                    javaVersion, springVersion, bootVersion);

            return new ProjectScan("MAVEN", javaVersion, springVersion, bootVersion, libs);

        } catch (Exception e) {
            log.error("Erro ao analisar projeto Maven: {}", e.getMessage(), e);
            return new ProjectScan("MAVEN_ERROR", null, null, null, Map.of());
        }
    }
}
