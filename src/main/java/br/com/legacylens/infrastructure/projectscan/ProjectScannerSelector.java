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

/**
 * 🧠 ProjectScannerSelector
 * -----------------------------------------------------
 * Seleciona automaticamente o tipo de projeto (Maven, Gradle ou JAR)
 * e delega a análise para o scanner correspondente.
 *
 * Recursos:
 *  - Busca recursiva de pom.xml / build.gradle até 4 níveis
 *  - Detecção de projetos extraídos em subpastas
 *  - Tratamento seguro de exceções e logs detalhados
 */
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
            Path path = Path.of(pathOrJar).normalize();
            if (!Files.exists(path)) {
                log.error("❌ Caminho inexistente: {}", path);
                return new ProjectScan("NOT_FOUND", null, null, null, java.util.Map.of());
            }

            // =====================================================
            // 📦 Caso seja um arquivo JAR direto
            // =====================================================
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                log.info("📦 Detectado arquivo JAR: {}", path);
                return jar.scan(path.toString());
            }

            // =====================================================
            // 🧭 Caso seja um diretório — busca recursiva
            // =====================================================
            if (Files.isDirectory(path)) {
                Path pom = findFile(path, "pom.xml", 4);
                Path gradle = findFile(path, "build.gradle", 4);
                Path gradleKts = findFile(path, "build.gradle.kts", 4);

                // Maven
                if (pom != null) {
                    Path root = pom.getParent();
                    log.info("""
                            🧩 Projeto Maven detectado:
                               • Raiz: {}
                               • pom.xml: {}
                            """, path, pom);
                    return maven.scan(root.toString());
                }

                // Gradle
                if (gradle != null || gradleKts != null) {
                    Path root = gradle != null ? gradle.getParent() : gradleKts.getParent();
                    log.info("""
                            🧩 Projeto Gradle detectado:
                               • Raiz: {}
                               • build.gradle: {}
                            """, path, root);
                    return this.gradle.scan(root.toString());
                }

                // Caso possua .class (sem build file)
                boolean hasClasses = Files.walk(path, 3)
                        .anyMatch(p -> p.toString().endsWith(".class"));
                if (hasClasses) {
                    log.info("📚 Diretório contém classes compiladas, analisando como JAR decompilado...");
                    return jar.scan(path.toString());
                }
            }

            // =====================================================
            // ⚠️ Fallback — não identificado
            // =====================================================
            log.warn("""
                    ⚠️ Tipo de projeto não identificado.
                       Caminho analisado: {}
                       Dica: verifique se há pom.xml, build.gradle ou MANIFEST.MF
                    """, pathOrJar);

            return new ProjectScan("UNKNOWN", null, null, null, java.util.Map.of());

        } catch (Exception e) {
            log.error("❌ Erro ao detectar tipo de projeto: {}", e.getMessage(), e);
            return new ProjectScan("ERROR", null, null, null, java.util.Map.of());
        }
    }

    // ==========================================================
    // 🔍 Utilitário interno — busca recursiva de arquivo
    // ==========================================================
    private Path findFile(Path root, String fileName, int depth) {
        try (var stream = Files.walk(root, depth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Falha ao buscar {} em {}: {}", fileName, root, e.getMessage());
            return null;
        }
    }
}
