package br.com.legacylens.infrastructure.impl.uml;

import br.com.legacylens.config.LegacyLensConfigLoader;
import br.com.legacylens.domain.model.UmlDiagram;
import br.com.legacylens.domain.ports.UmlGeneratorPort;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 💠 PlantUmlGeneratorImpl
 * -----------------------------------------
 * Gera diagramas UML estruturais a partir de projetos Java.
 * Suporta Maven, Gradle ou compilação manual via JavaCompiler.
 * Inclui fallback inteligente com detecção automática de frameworks e multi-módulo.
 */
@Slf4j
@Component
public class PlantUmlGeneratorImpl implements UmlGeneratorPort {

    @Override
    public UmlDiagram generateFromPathOrJar(String source, Path outDir) {
        Instant start = Instant.now();
        log.info("===== [PlantUML] Iniciando geração do diagrama UML =====");
        log.info("📦 Projeto: {}", source);

        Path projectPath = Path.of(source);
        var cfg = LegacyLensConfigLoader.get();
        boolean detectMultiModule = cfg.getExecution() != null && cfg.getExecution().isDetectMultiModule();

        try {
            // --- Se multi-módulo ativo, gerar um .puml por módulo ---
            if (detectMultiModule && hasMultipleModules(projectPath)) {
                log.info("🧩 Multi-módulo detectado — gerando diagramas por submódulo...");
                var modules = detectModules(projectPath);
                for (Path module : modules) {
                    generateSingleModuleDiagram(module, outDir);
                }
                log.info("✅ Diagramas multi-módulo concluídos.");
                return new UmlDiagram("diagram-multi.puml");
            }

            // --- Caso contrário, gerar apenas 1 .puml global ---
            generateSingleModuleDiagram(projectPath, outDir);

        } catch (Exception e) {
            log.error("❌ Erro durante geração UML: {}", e.getMessage(), e);
            return new UmlDiagram("diagram-error.puml");
        }

        long ms = Duration.between(start, Instant.now()).toMillis();
        log.info("===== [PlantUML] Processo concluído em {} ms =====", ms);
        return new UmlDiagram("diagram.puml");
    }

    // ==============================================================
    // 🔹 Geração de um único módulo
    // ==============================================================
    private void generateSingleModuleDiagram(Path projectPath, Path outDir) throws IOException {
        Instant start = Instant.now();
        String moduleName = projectPath.getFileName() != null
                ? projectPath.getFileName().toString()
                : "root";

        log.info("📘 Gerando diagrama para módulo: {}", moduleName);

        StringBuilder puml = new StringBuilder("@startuml\n");
        int count = 0;

        // Detecta build e compila
        Path pom = findFile(projectPath, "pom.xml");
        Path gradle = findFile(projectPath, "build.gradle");
        Path mvnw = findFile(projectPath, "mvnw");
        Path gradlew = findFile(projectPath, "gradlew");

        if (pom != null || gradle != null || mvnw != null || gradlew != null) {
            compileProject(projectPath, pom, gradle, mvnw, gradlew);
        } else {
            compileWithSmartFallback(projectPath);
        }

        // Diretórios de classes
        List<Path> classesDirs = findAllClassesDirectories(projectPath);
        if (classesDirs.isEmpty()) {
            compileWithSmartFallback(projectPath);
            classesDirs = findAllClassesDirectories(projectPath);
        }

        if (classesDirs.isEmpty()) {
            log.warn("⚠️ Nenhum diretório de classes encontrado no módulo {}", moduleName);
            return;
        }

        // Pacotes detectados
        Set<String> packages = new TreeSet<>();
        for (Path dir : classesDirs) packages.addAll(detectPackages(dir));

        // Escaneia com ClassGraph
        ClassGraph cg = new ClassGraph().ignoreClassVisibility();
        for (Path dir : classesDirs) cg = cg.overrideClasspath(dir.toString());
        if (!packages.isEmpty()) cg = cg.acceptPackages(packages.toArray(new String[0]));

        try (var scan = cg.enableClassInfo().scan()) {
            var allClasses = scan.getAllClasses();
            for (ClassInfo ci : allClasses) {
                if (!ci.isStandardClass()) continue;
                puml.append("class ").append(ci.getSimpleName()).append("\n");
                count++;

                if (ci.getSuperclass() != null) {
                    puml.append(ci.getSimpleName())
                            .append(" --|> ")
                            .append(ci.getSuperclass().getSimpleName())
                            .append("\n");
                }
                for (var itf : ci.getInterfaces()) {
                    puml.append(ci.getSimpleName())
                            .append(" ..|> ")
                            .append(itf.getSimpleName())
                            .append("\n");
                }

                if (count >= 500) {
                    log.warn("⚠️ Limite de 500 classes atingido no módulo {}", moduleName);
                    break;
                }
            }
        }

        puml.append("@enduml\n");

        // Salva arquivo
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("diagram_" + moduleName + ".puml");
        Files.writeString(outFile, puml.toString());
        log.info("✅ Diagrama do módulo '{}' gerado em {} ({} classes)", moduleName, outFile, count);

        long ms = Duration.between(start, Instant.now()).toMillis();
        log.debug("⏱️ Tempo módulo {}: {} ms", moduleName, ms);
    }

    // ==============================================================
    // 🧠 Multi-módulo detection
    // ==============================================================
    private boolean hasMultipleModules(Path root) throws IOException {
        try (var s = Files.walk(root, 2)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("src/main/java")) || Files.exists(p.resolve("pom.xml")))
                    .count() > 1;
        }
    }

    private List<Path> detectModules(Path root) throws IOException {
        try (var s = Files.walk(root, 2)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("src/main/java")) || Files.exists(p.resolve("pom.xml")))
                    .collect(Collectors.toList());
        }
    }

    // ==============================================================
    // 🔧 Utilitários de compilação
    // ==============================================================
    private void compileProject(Path projectPath, Path pom, Path gradle, Path mvnw, Path gradlew) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;

            if (mvnw != null && Files.exists(mvnw)) {
                pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", mvnw.toString(), "clean", "compile", "-q", "-DskipTests")
                        : new ProcessBuilder(mvnw.toString(), "clean", "compile", "-q", "-DskipTests");
            } else if (pom != null) {
                pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", "mvn", "clean", "compile", "-q", "-DskipTests")
                        : new ProcessBuilder("mvn", "clean", "compile", "-q", "-DskipTests");
            } else if (gradlew != null && Files.exists(gradlew)) {
                pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", gradlew.toString(), "build", "-x", "test")
                        : new ProcessBuilder(gradlew.toString(), "build", "-x", "test");
            } else if (gradle != null) {
                pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", "gradle", "build", "-x", "test")
                        : new ProcessBuilder("gradle", "build", "-x", "test");
            } else {
                compileWithSmartFallback(projectPath);
                return;
            }

            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> log.debug("[BUILD] {}", line));
            }

            process.waitFor(3, TimeUnit.MINUTES);
            if (process.exitValue() != 0) {
                log.warn("⚠️ Compilação falhou — fallback automático acionado.");
                compileWithSmartFallback(projectPath);
            } else {
                log.info("✅ Compilação concluída com sucesso ({})", projectPath);
            }

        } catch (Exception e) {
            log.error("❌ Erro na compilação: {}", e.getMessage());
            compileWithSmartFallback(projectPath);
        }
    }

    private void compileWithSmartFallback(Path projectPath) {
        try {
            Path srcDir = findSourceDir(projectPath);
            if (srcDir == null) return;

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                log.error("❌ JDK não disponível (somente JRE).");
                return;
            }

            Path targetDir = projectPath.resolve("target/classes");
            Files.createDirectories(targetDir);

            var javaFiles = Files.walk(srcDir)
                    .filter(f -> f.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .toList();

            if (javaFiles.isEmpty()) return;

            String joinedSource = Files.walk(srcDir)
                    .filter(f -> f.toString().endsWith(".java"))
                    .limit(200)
                    .map(f -> {
                        try {
                            return Files.readString(f);
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .collect(Collectors.joining("\n"))
                    .toLowerCase();

            boolean isSpring = joinedSource.contains("springframework");
            boolean isCamunda = joinedSource.contains("camunda");
            boolean isCamel = joinedSource.contains("camel");
            boolean isFeign = joinedSource.contains("feign");
            boolean isJakarta = joinedSource.contains("jakarta.persistence");

            Path m2 = Path.of(System.getProperty("user.home"), ".m2", "repository");
            List<Path> classpath = new ArrayList<>();
            if (isSpring) classpath.addAll(findJars(m2, "spring-"));
            if (isCamunda) classpath.addAll(findJars(m2, "camunda-"));
            if (isCamel) classpath.addAll(findJars(m2, "camel-"));
            if (isFeign) classpath.addAll(findJars(m2, "feign-"));
            if (isJakarta) classpath.addAll(findJars(m2, "jakarta.persistence"));

            String cp = classpath.stream()
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));

            var fm = compiler.getStandardFileManager(null, null, null);
            compiler.getTask(null, fm, null,
                    List.of("-d", targetDir.toString(), "-classpath", cp),
                    null, fm.getJavaFileObjectsFromFiles(javaFiles)).call();

            log.info("✅ Compilação manual concluída com sucesso ({})", projectPath);
        } catch (Exception e) {
            log.error("❌ Erro no fallback inteligente: {}", e.getMessage());
        }
    }

    private List<Path> findJars(Path root, String keyword) throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var s = Files.walk(root, 4)) {
            return s.filter(p -> p.toString().endsWith(".jar") &&
                            p.getFileName().toString().toLowerCase().contains(keyword))
                    .limit(25)
                    .collect(Collectors.toList());
        }
    }

    private Path findFile(Path root, String name) throws IOException {
        try (var s = Files.walk(root, 3)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private List<Path> findAllClassesDirectories(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        for (String d : List.of("target/classes", "build/classes/java/main", "bin")) {
            Path p = root.resolve(d);
            if (Files.exists(p)) dirs.add(p);
        }
        try (var s = Files.walk(root, 4)) {
            s.filter(Files::isDirectory)
                    .filter(p -> p.endsWith("classes"))
                    .forEach(dirs::add);
        }
        return dirs.stream().distinct().collect(Collectors.toList());
    }

    private Set<String> detectPackages(Path classesDir) throws IOException {
        try (var s = Files.walk(classesDir)) {
            return s.filter(f -> f.toString().endsWith(".class"))
                    .map(classesDir::relativize)
                    .map(Path::toString)
                    .map(s2 -> s2.replace(File.separatorChar, '.'))
                    .map(s2 -> s2.substring(0, s2.lastIndexOf('.')))
                    .map(s2 -> s2.contains(".") ? s2.substring(0, s2.lastIndexOf('.')) : s2)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private Path findSourceDir(Path root) throws IOException {
        for (String c : List.of("src/main/java", "src", "app", "code")) {
            Path path = root.resolve(c);
            if (Files.exists(path)) return path;
        }
        try (var s = Files.walk(root, 4)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("java"))
                    .findFirst().orElse(null);
        }
    }
}
