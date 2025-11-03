package br.com.legacylens.infrastructure.impl.uml;

import br.com.legacylens.domain.model.UmlDiagram;
import br.com.legacylens.domain.ports.UmlGeneratorPort;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlantUmlGeneratorImpl implements UmlGeneratorPort {

    @Override
    public UmlDiagram generateFromPathOrJar(String source, Path outDir) {
        Instant start = Instant.now();
        log.info("===== [PlantUML] Iniciando geração do diagrama UML =====");
        log.info("📦 Projeto: {}", source);
        log.info("📤 Saída: {}", outDir);

        StringBuilder puml = new StringBuilder("@startuml\n");
        int count = 0;

        try {
            Path projectPath = Path.of(source);
            if (!Files.exists(projectPath)) {
                log.error("❌ Caminho do projeto não encontrado: {}", source);
                return new UmlDiagram("diagram-error.puml");
            }

            // Detecta o sistema de build
            Path pom = findFile(projectPath, "pom.xml");
            Path gradle = findFile(projectPath, "build.gradle");
            Path mvnw = findFile(projectPath, "mvnw");
            Path gradlew = findFile(projectPath, "gradlew");

            if (pom != null || gradle != null || mvnw != null || gradlew != null) {
                log.info("🧩 Sistema de build detectado — compilando...");
                compileProject(projectPath, pom, gradle, mvnw, gradlew);
            } else {
                log.warn("⚠️ Nenhum sistema de build detectado — usando fallback.");
                compileWithFallback(projectPath);
            }

            // Localiza as classes compiladas
            Path classesDir = findClassesDirectory(projectPath);
            if (classesDir == null) {
                log.warn("⚠️ Diretório de classes não encontrado. Executando fallback.");
                compileWithFallback(projectPath);
                classesDir = findClassesDirectory(projectPath);
            }

            if (classesDir == null) {
                log.error("❌ Falha ao localizar diretório de classes. Abortando.");
                return new UmlDiagram("diagram-error.puml");
            }

            log.info("📁 Diretório de classes encontrado: {}", classesDir);

            // Detecta pacotes válidos
            Set<String> packages = detectPackages(classesDir);
            log.info("📦 Pacotes detectados: {}", packages.isEmpty() ? "nenhum" : packages);

            // Escaneia e monta o UML
            try (var scan = new ClassGraph()
                    .overrideClasspath(classesDir.toString())
                    .acceptPackages(packages.toArray(new String[0]))
                    .enableClassInfo()
                    .ignoreClassVisibility()
                    .scan()) {

                var allClasses = scan.getAllClasses();
                log.info("📊 Total de classes detectadas: {}", allClasses.size());

                for (ClassInfo ci : allClasses) {
                    if (!ci.isStandardClass()) continue;

                    puml.append("class ").append(ci.getSimpleName()).append("\n");
                    count++;

                    // Herança
                    if (ci.getSuperclass() != null) {
                        puml.append(ci.getSimpleName()).append(" --|> ")
                                .append(ci.getSuperclass().getSimpleName()).append("\n");
                    }

                    // Interfaces
                    for (var itf : ci.getInterfaces()) {
                        puml.append(ci.getSimpleName()).append(" ..|> ")
                                .append(itf.getSimpleName()).append("\n");
                    }

                    if (count >= 500) {
                        log.warn("⚠️ Limite de 500 classes atingido — truncando diagrama.");
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ Erro durante a geração UML: {}", e.getMessage(), e);
        }

        puml.append("@enduml\n");

        try {
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve("diagram.puml");
            Files.writeString(outFile, puml.toString());
            log.info("🖼️ Diagrama UML gerado com sucesso em {}", outFile);
        } catch (IOException e) {
            log.error("❌ Falha ao salvar diagrama: {}", e.getMessage(), e);
            return new UmlDiagram("diagram-error.puml");
        }

        long ms = Duration.between(start, Instant.now()).toMillis();
        log.info("===== [PlantUML] Processo concluído em {} ms =====", ms);
        return new UmlDiagram("diagram.puml");
    }

    // 🔍 Detectores utilitários
    private Path findFile(Path root, String name) throws IOException {
        try (var s = Files.walk(root, 3)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private Path findClassesDirectory(Path root) throws IOException {
        List<String> dirs = List.of("target/classes", "build/classes/java/main", "bin");
        for (String d : dirs) {
            Path path = root.resolve(d);
            if (Files.exists(path)) return path;
        }
        try (var s = Files.walk(root, 5)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.toString().endsWith("classes"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private Set<String> detectPackages(Path classesDir) throws IOException {
        try (var s = Files.walk(classesDir)) {
            return s.filter(f -> f.toString().endsWith(".class"))
                    .map(classesDir::relativize)
                    .map(Path::toString)
                    .map(s2 -> s2.replace(File.separatorChar, '.'))
                    .map(s2 -> s2.substring(0, s2.lastIndexOf('.')))
                    .map(s2 -> s2.contains(".") ? s2.substring(0, s2.lastIndexOf('.')) : s2)
                    .filter(s2 -> !s2.isBlank())
                    .limit(100)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    // 🔧 Compilação automática (Maven > Gradle > Wrapper > Fallback)
    private void compileProject(Path projectPath, Path pom, Path gradle, Path mvnw, Path gradlew) {
        try {
            ProcessBuilder pb;
            if (mvnw != null && Files.exists(mvnw)) {
                log.info("⚙️ Compilando via Maven Wrapper...");
                pb = new ProcessBuilder("cmd.exe", "/c", mvnw.toAbsolutePath().toString(), "clean", "compile", "-q");
            } else if (pom != null && Files.exists(pom)) {
                log.info("⚙️ Compilando via Maven...");
                pb = new ProcessBuilder("cmd.exe", "/c", "mvn", "clean", "compile", "-q");
            } else if (gradlew != null && Files.exists(gradlew)) {
                log.info("⚙️ Compilando via Gradle Wrapper...");
                pb = new ProcessBuilder("cmd.exe", "/c", gradlew.toAbsolutePath().toString(), "build", "-x", "test");
            } else if (gradle != null && Files.exists(gradle)) {
                log.info("⚙️ Compilando via Gradle...");
                pb = new ProcessBuilder("cmd.exe", "/c", "gradle", "build", "-x", "test");
            } else {
                log.warn("⚠️ Nenhum build system identificado — fallback direto.");
                compileWithFallback(projectPath);
                return;
            }

            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> log.debug("[BUILD] {}", line));
            }

            boolean success = process.waitFor(3, TimeUnit.MINUTES);
            if (success && process.exitValue() == 0) {
                log.info("✅ Compilação concluída com sucesso.");
            } else {
                log.warn("⚠️ Compilação falhou — fallback via JavaCompiler API.");
                compileWithFallback(projectPath);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao compilar: {}", e.getMessage());
            compileWithFallback(projectPath);
        }
    }

    // 🧠 Fallback manual
    private void compileWithFallback(Path projectPath) {
        try {
            Path srcDir = findSourceDir(projectPath);
            if (srcDir == null) {
                log.warn("⚠️ Nenhum diretório de código-fonte encontrado.");
                return;
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                log.error("❌ JDK não disponível (JRE detectada).");
                return;
            }

            Path targetDir = projectPath.resolve("target/classes");
            Files.createDirectories(targetDir);

            var javaFiles = Files.walk(srcDir)
                    .filter(f -> f.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .toList();

            if (javaFiles.isEmpty()) {
                log.warn("⚠️ Nenhum arquivo .java encontrado para compilação.");
                return;
            }

            var fm = compiler.getStandardFileManager(null, null, null);
            compiler.getTask(null, fm, null, List.of("-d", targetDir.toString()), null,
                    fm.getJavaFileObjectsFromFiles(javaFiles)).call();

            log.info("✅ Compilação manual concluída com sucesso em {}", targetDir);
        } catch (Exception e) {
            log.error("❌ Erro durante fallback de compilação: {}", e.getMessage());
        }
    }

    private Path findSourceDir(Path root) throws IOException {
        List<String> candidates = List.of("src/main/java", "src", "app", "code");
        for (String c : candidates) {
            Path path = root.resolve(c);
            if (Files.exists(path)) return path;
        }
        try (var s = Files.walk(root, 4)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("java"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
