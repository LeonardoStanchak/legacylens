package br.com.legacylens.app.rest;

import br.com.legacylens.application.AnalyzeProjectService;
import br.com.legacylens.application.GenerateReportsService;
import br.com.legacylens.config.LegacyLensConfigLoader;
import br.com.legacylens.domain.model.ProjectScan;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 🚀 ProjectController — entrada principal da API
 *  - Recebe projetos via upload ZIP ou Git URL.
 *  - Descompacta, aplica heurísticas automáticas e executa análise.
 *  - Gera UML + Sequence + Excel (sem README).
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final AnalyzeProjectService analyze;
    private final GenerateReportsService reports;

    public ProjectController(AnalyzeProjectService analyze, GenerateReportsService reports) {
        this.analyze = analyze;
        this.reports = reports;
    }

    public record AnalyzeResponse(String outputDir, ProjectScan scan) {}

    // ================================================================
    // 🔹 ANALISAR UPLOAD ZIP
    // ================================================================
    @PostMapping(path = "/analyze/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyzeUpload(@RequestParam("file") MultipartFile file) throws Exception {
        log.info("📦 Recebendo arquivo ZIP: {}", file.getOriginalFilename());

        Path tmpDir = Files.createTempDirectory("legacylens_");
        Path uploaded = tmpDir.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), uploaded, StandardCopyOption.REPLACE_EXISTING);

        Path projectPath = uploaded;
        if (uploaded.toString().endsWith(".zip")) {
            Path unzipDir = tmpDir.resolve("unzipped");
            unzip(uploaded, unzipDir);
            projectPath = unzipDir;
        }

        // 🧠 Aplica configuração inteligente (arquitetura, módulos, tamanho)
        applySmartConfiguration(projectPath);

        // 🔍 Executa análise
        var scan = analyze.execute(projectPath.toString());
        Path outDir = Path.of("output", String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(outDir);

        // 📊 Gera relatórios (UML + Sequence + Excel)
        reports.generateAll(scan, projectPath.toString(), outDir);
        log.info("✅ Artefatos gerados em: {}", outDir);

        return new AnalyzeResponse(outDir.toAbsolutePath().toString(), scan);
    }

    // ================================================================
    // 🔹 ANALISAR VIA GIT
    // ================================================================
    @PostMapping("/analyze/git")
    public AnalyzeResponse analyzeGit(@RequestParam("url") String gitUrl) throws Exception {
        log.info("🚀 Iniciando análise via Git: {}", gitUrl);

        Path baseDir = Paths.get(System.getProperty("user.home"), "Documents", "legados");
        Files.createDirectories(baseDir);

        cleanOldRepositories(baseDir);

        String repoName = gitUrl.substring(gitUrl.lastIndexOf('/') + 1).replace(".git", "");
        Path cloneDir = baseDir.resolve(repoName + "_" + System.currentTimeMillis());
        Files.createDirectories(cloneDir);

        try {
            // Clone rápido (depth=1)
            try (var git = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(cloneDir.toFile())
                    .setDepth(1)
                    .call()) {
                log.info("✅ Clone concluído: {}", cloneDir);
            }

            // Corrige "pom" sem extensão
            Path pomNoExt = cloneDir.resolve("pom");
            if (Files.exists(pomNoExt) && !Files.exists(cloneDir.resolve("pom.xml"))) {
                Files.move(pomNoExt, cloneDir.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
                log.info("🧩 Arquivo 'pom' renomeado para 'pom.xml'");
            }

            // 🧠 Inteligência automática (arquitetura + módulos)
            applySmartConfiguration(cloneDir);

            // 🔍 Executa análise
            var scan = analyze.execute(cloneDir.toString());

            // 📊 Gera relatórios
            Path outDir = Path.of("output", String.valueOf(System.currentTimeMillis()));
            Files.createDirectories(outDir);
            reports.generateAll(scan, cloneDir.toString(), outDir);

            log.info("📊 Análise concluída com sucesso. Artefatos em {}", outDir);

            return new AnalyzeResponse(outDir.toAbsolutePath().toString(), scan);
        } finally {
            try {
                deleteDirectoryRecursively(cloneDir);
                log.info("🧹 Diretório temporário removido: {}", cloneDir);
            } catch (Exception ex) {
                log.warn("⚠️ Falha ao remover diretório temporário: {}", ex.getMessage());
            }
        }
    }

    // ================================================================
    // 🔧 UTILITÁRIOS
    // ================================================================
    private void unzip(Path zipFile, Path outputDir) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path filePath = outputDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) Files.createDirectories(filePath);
                else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zin, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        log.info("📂 ZIP extraído em {}", outputDir);
    }

    private void deleteDirectoryRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void cleanOldRepositories(Path baseDir) {
        try {
            Files.list(baseDir)
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() <
                                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            deleteDirectoryRecursively(p);
                            log.info("🧹 Repositório antigo removido: {}", p);
                        } catch (Exception e) {
                            log.warn("Falha ao limpar repositório {}: {}", p, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("⚠️ Erro ao limpar repositórios antigos: {}", e.getMessage());
        }
    }

    // ================================================================
    // 🧠 CONFIGURAÇÃO INTELIGENTE
    // ================================================================
    private void applySmartConfiguration(Path projectPath) {
        try {
            log.info("🧠 Aplicando inteligência automática...");
            LegacyLensConfigLoader.applyAutoIntelligence(projectPath);

            var cfg = LegacyLensConfigLoader.get();
            var exec = cfg.getExecution();

            log.info("🔧 YAML ativo: sequence={} multiModule={}",
                    cfg.getSequence().isEnabled(),
                    exec != null && exec.isDetectMultiModule());

        } catch (Exception e) {
            log.warn("⚠️ Falha ao aplicar inteligência automática: {}", e.getMessage());
        }
    }
}
