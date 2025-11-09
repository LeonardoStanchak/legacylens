package br.com.legacylens.infrastructure.impl;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import br.com.legacylens.infrastructure.util.LegacyHeuristicsUtil;
import io.github.classgraph.ClassGraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * 📦 Analisa arquivos JAR:
 * - Lê MANIFEST.MF para descobrir versões
 * - Usa ClassGraph para detectar frameworks
 */
@Slf4j
@Component
public class JarProjectScannerImpl implements ProjectScannerPort {

    @Override
    public ProjectScan scan(String jarPath) {
        String bootVersion = null;
        String springIndicator = null;

        try (JarFile jar = new JarFile(Path.of(jarPath).toFile())) {
            var mf = jar.getManifest();
            if (mf != null) {
                bootVersion = mf.getMainAttributes().getValue("Spring-Boot-Version");
                if (bootVersion == null)
                    bootVersion = mf.getMainAttributes().getValue("Implementation-Version");
            }
        } catch (Exception e) {
            log.warn("⚠️ Não foi possível ler o manifest do JAR: {}", e.getMessage());
        }

        try (var scan = new ClassGraph()
                .overrideClasspath(jarPath)
                .acceptPackages("org.springframework")
                .enableClassInfo()
                .scan()) {
            springIndicator = scan.getAllClasses().isEmpty() ? null : "present";
        } catch (Exception e) {
            log.error("❌ Erro ao escanear classes do JAR: {}", e.getMessage(), e);
        }

        if (bootVersion != null || springIndicator != null)
            log.info("✅ JAR analisado com sucesso. Spring={}, Boot={}", springIndicator, bootVersion);
        else
            log.warn("⚠️ Nenhum indicador de Spring detectado no JAR.");

        // Heurísticas adicionais (caso o JAR esteja extraído)
        Map<String, String> libs = LegacyHeuristicsUtil.detectLibrariesFromSource(Path.of(jarPath).getParent());

        return new ProjectScan("JAR", null, springIndicator, bootVersion, libs);
    }
}
