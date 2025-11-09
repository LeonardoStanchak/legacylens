package br.com.legacylens.app.rest;

import br.com.legacylens.application.AnalyzeProjectService;
import br.com.legacylens.application.GenerateReportsService;
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
        log.info("📦 Recebendo arquivo para análise: {}", file.getOriginalFilename());
        Path tmpDir = Files.createTempDirectory("legacylens_");
        Path uploaded = tmpDir.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), uploaded, StandardCopyOption.REPLACE_EXISTING);

        Path projectPath = uploaded;
        if (uploaded.toString().endsWith(".zip")) {
            Path unzipDir = tmpDir.resolve("unzipped");
            unzip(uploaded, unzipDir);
            projectPath = unzipDir;
        }

        log.info("🔍 Iniciando análise do projeto em: {}", projectPath);
        var scan = analyze.execute(projectPath.toString());
        Path outDir = Path.of("output", String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(outDir);
        reports.generateAll(scan, projectPath.toString(), outDir);
        log.info("✅ Análise concluída. Artefatos gerados em: {}", outDir);

        return new AnalyzeResponse(outDir.toAbsolutePath().toString(), scan);
    }

    // ================================================================
    // 🔹 ANALISAR VIA GIT
    // ================================================================
    @PostMapping("/analyze/git")
    public AnalyzeResponse analyzeGit(@RequestParam("url") String gitUrl) throws Exception {
        log.info("🚀 Iniciando análise de repositório Git: {}", gitUrl);

        // === Diretório fixo para clones ===
        Path baseDir = Paths.get(System.getProperty("user.home"), "Documents", "legados");
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
            log.info("📁 Diretório base criado em {}", baseDir);
        }

        // Limpa repositórios antigos (>7 dias)
        cleanOldRepositories(baseDir);

        // Extrai nome do repositório (ex: itau-jwt)
        String repoName = gitUrl.substring(gitUrl.lastIndexOf('/') + 1).replace(".git", "");
        Path cloneDir = baseDir.resolve(repoName + "_" + System.currentTimeMillis());
        Files.createDirectories(cloneDir);
        log.info("📂 Clonando repositório para {}", cloneDir);

        try {
            // === Clona repositório ===
            try (var git = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(cloneDir.toFile())
                    .setDepth(1)
                    .call()) {
                log.info("✅ Clone concluído com sucesso: {}", cloneDir);
            }

            // === Corrige pom sem extensão ===
            Path pomNoExt = cloneDir.resolve("pom");
            Path pomXml = cloneDir.resolve("pom.xml");
            if (Files.exists(pomNoExt) && !Files.exists(pomXml)) {
                Files.move(pomNoExt, pomXml, StandardCopyOption.REPLACE_EXISTING);
                log.info("🧩 Arquivo 'pom' renomeado automaticamente para 'pom.xml'");
            }

            // === Executa análise ===
            log.info("🔍 Executando análise do projeto clonado...");
            var scan = analyze.execute(cloneDir.toString());

            // === Gera relatórios ===
            Path outDir = Path.of("output", String.valueOf(System.currentTimeMillis()));
            Files.createDirectories(outDir);
            reports.generateAll(scan, cloneDir.toString(), outDir);

            log.info("📊 Análise de repositório concluída com sucesso!");
            log.info("📤 Artefatos gerados em: {}", outDir);

            return new AnalyzeResponse(outDir.toAbsolutePath().toString(), scan);
        }
        catch (Exception e) {
            log.error("❌ Erro ao analisar repositório {}: {}", gitUrl, e.getMessage(), e);
            throw e;
        }
        finally {
            // === Limpeza final ===
            try {
                deleteDirectoryRecursively(cloneDir);
                log.info("🧹 Diretório temporário removido: {}", cloneDir);
            } catch (Exception ex) {
                log.warn("⚠️ Falha ao remover diretório temporário {}: {}", cloneDir, ex.getMessage());
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
        log.info("📦 Arquivo ZIP extraído em: {}", outputDir);
    }

    private void deleteDirectoryRecursively(Path path) throws Exception {
        if (path != null && Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
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
                            log.warn("Falha ao limpar repositório antigo {}: {}", p, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("⚠️ Falha ao limpar repositórios antigos: {}", e.getMessage());
        }
    }
}
