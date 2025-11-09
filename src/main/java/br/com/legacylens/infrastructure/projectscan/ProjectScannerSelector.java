package br.com.legacylens.infrastructure.projectscan;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import br.com.legacylens.infrastructure.impl.GradleProjectScannerImpl;
import br.com.legacylens.infrastructure.impl.JarProjectScannerImpl;
import br.com.legacylens.infrastructure.impl.MavenProjectScannerImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Slf4j
@Component
@Primary
public class ProjectScannerSelector implements ProjectScannerPort {

    private final MavenProjectScannerImpl maven;
    private final GradleProjectScannerImpl gradle;
    private final JarProjectScannerImpl jar;

    public ProjectScannerSelector(MavenProjectScannerImpl maven,
                                  GradleProjectScannerImpl gradle,
                                  JarProjectScannerImpl jar) {
        this.maven = maven;
        this.gradle = gradle;
        this.jar = jar;
    }

    @Override
    public ProjectScan scan(String pathOrJar) {
        try {
            Path path = Path.of(pathOrJar);
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                log.info("Detectado arquivo JAR: {}", path);
                return jar.scan(pathOrJar);
            }
            if (Files.isDirectory(path)) {
                if (Files.exists(path.resolve("pom.xml"))) {
                    log.info("Detectado projeto Maven: {}", path);
                    return maven.scan(pathOrJar);
                }
                if (Files.exists(path.resolve("build.gradle")) || Files.exists(path.resolve("build.gradle.kts"))) {
                    log.info("Detectado projeto Gradle: {}", path);
                    return gradle.scan(pathOrJar);
                }
            }
            log.warn("Tipo de projeto não identificado: {}", pathOrJar);
            return new ProjectScan("UNKNOWN", null, null, null, java.util.Map.of());
        } catch (Exception e) {
            log.error("Erro ao detectar tipo de projeto: {}", e.getMessage(), e);
            return new ProjectScan("ERROR", null, null, null, java.util.Map.of());
        }
    }
}
