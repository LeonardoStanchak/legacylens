package br.com.legacylens.infrastructure.impl;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.Map;
import java.util.regex.*;

@Slf4j
@Component
public class GradleProjectScannerImpl implements ProjectScannerPort {

    @Override
    public ProjectScan scan(String dir) {
        try {
            Path build = Files.exists(Path.of(dir, "build.gradle"))
                    ? Path.of(dir, "build.gradle")
                    : Path.of(dir, "build.gradle.kts");
            String content = Files.readString(build);

            String javaVersion = find(content, "sourceCompatibility\\s*=\\s*['\\\"]?(\\d+|\\d+\\.\\d+)['\\\"]?");
            String bootVersion = find(content, "spring-boot\\s*[:=]\\s*['\\\"]?(\\d+\\.\\d+\\.\\d+)['\\\"]?");

            log.info("Projeto Gradle analisado com sucesso. Java={}, Boot={}", javaVersion, bootVersion);
            return new ProjectScan("GRADLE", javaVersion, null, bootVersion, Map.of());
        } catch (Exception e) {
            log.error("Erro ao analisar projeto Gradle: {}", e.getMessage(), e);
            return new ProjectScan("GRADLE_ERROR", null, null, null, Map.of());
        }
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
