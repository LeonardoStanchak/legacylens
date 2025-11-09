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
    import java.nio.file.*;
    import java.time.Duration;
    import java.time.Instant;
    import java.util.*;
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

                // Build system
                Path pom = findFile(projectPath, "pom.xml");
                Path gradle = findFile(projectPath, "build.gradle");
                Path mvnw = findFile(projectPath, "mvnw");
                Path mvnwCmd = findFile(projectPath, "mvnw.cmd");
                Path gradlew = findFile(projectPath, "gradlew");
                Path gradlewCmd = findFile(projectPath, "gradlew.cmd");

                if (pom != null || gradle != null || mvnw != null || mvnwCmd != null || gradlew != null || gradlewCmd != null) {
                    log.info("🧩 Sistema de build detectado — compilando...");
                    compileProject(projectPath, pom, gradle, mvnw, mvnwCmd, gradlew, gradlewCmd);
                } else {
                    log.warn("⚠️ Nenhum sistema de build detectado — usando fallback.");
                    compileWithFallback(projectPath);
                }

                // Diretórios de classes (mono e multi-módulo)
                List<Path> classesDirs = findAllClassesDirectories(projectPath);
                if (classesDirs.isEmpty()) {
                    log.warn("⚠️ Diretório(s) de classes não encontrado(s). Executando fallback.");
                    compileWithFallback(projectPath);
                    classesDirs = findAllClassesDirectories(projectPath);
                }
                if (classesDirs.isEmpty()) {
                    log.error("❌ Falha ao localizar diretório(s) de classes. Abortando.");
                    return new UmlDiagram("diagram-error.puml");
                }
                log.info("📁 Diretórios de classes: {}", classesDirs);

                // Pacotes detectados
                Set<String> packages = new TreeSet<>();
                for (Path dir : classesDirs) {
                    packages.addAll(detectPackages(dir));
                }
                log.info("📦 Pacotes detectados: {}", packages.isEmpty() ? "nenhum" : packages);

                // Escaneia e monta UML
                ClassGraph cg = new ClassGraph().ignoreClassVisibility();
                for (Path dir : classesDirs) cg = cg.overrideClasspath(dir.toString());
                if (!packages.isEmpty()) cg = cg.acceptPackages(packages.toArray(new String[0]));

                try (var scan = cg.enableClassInfo().scan()) {
                    var allClasses = scan.getAllClasses();
                    log.info("📊 Total de classes detectadas: {}", allClasses.size());

                    for (ClassInfo ci : allClasses) {
                        if (!ci.isStandardClass()) continue;

                        puml.append("class ").append(ci.getSimpleName()).append("\n");
                        count++;

                        if (ci.getSuperclass() != null) {
                            puml.append(ci.getSimpleName()).append(" --|> ")
                                    .append(ci.getSuperclass().getSimpleName()).append("\n");
                        }
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

        private Path findFile(Path root, String name) throws IOException {
            try (var s = Files.walk(root, 3)) {
                return s.filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);
            }
        }

        private List<Path> findAllClassesDirectories(Path root) throws IOException {
            List<String> dirs = List.of("target/classes", "build/classes/java/main", "bin");
            List<Path> found = new ArrayList<>();
            // raiz
            for (String d : dirs) {
                Path p = root.resolve(d);
                if (Files.exists(p)) found.add(p);
            }
            // submódulos
            try (var s = Files.walk(root, 4)) {
                s.filter(Files::isDirectory)
                        .filter(p -> p.endsWith("classes"))
                        .forEach(found::add);
            }
            // normaliza e remove duplicatas
            return found.stream().map(Path::toAbsolutePath).distinct().collect(Collectors.toList());
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
                        .limit(150)
                        .collect(Collectors.toCollection(TreeSet::new));
            }
        }

        private void compileProject(Path projectPath, Path pom, Path gradle, Path mvnw, Path mvnwCmd,
                                    Path gradlew, Path gradlewCmd) {
            try {
                ProcessBuilder pb;
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

                if (isWindows && mvnwCmd != null && Files.exists(mvnwCmd)) {
                    log.info("⚙️ Compilando via Maven Wrapper (Windows)...");
                    pb = new ProcessBuilder("cmd.exe", "/c", mvnwCmd.toAbsolutePath().toString(), "clean", "compile", "-q", "-DskipTests");
                } else if (!isWindows && mvnw != null && Files.exists(mvnw)) {
                    log.info("⚙️ Compilando via Maven Wrapper (Unix)...");
                    pb = new ProcessBuilder(mvnw.toAbsolutePath().toString(), "clean", "compile", "-q", "-DskipTests");
                } else if (pom != null && Files.exists(pom)) {
                    log.info("⚙️ Compilando via Maven...");
                    pb = isWindows
                            ? new ProcessBuilder("cmd.exe", "/c", "mvn", "clean", "compile", "-q", "-DskipTests")
                            : new ProcessBuilder("mvn", "clean", "compile", "-q", "-DskipTests");
                } else if (isWindows && gradlewCmd != null && Files.exists(gradlewCmd)) {
                    log.info("⚙️ Compilando via Gradle Wrapper (Windows)...");
                    pb = new ProcessBuilder("cmd.exe", "/c", gradlewCmd.toAbsolutePath().toString(), "build", "-x", "test");
                } else if (!isWindows && gradlew != null && Files.exists(gradlew)) {
                    log.info("⚙️ Compilando via Gradle Wrapper (Unix)...");
                    pb = new ProcessBuilder(gradlew.toAbsolutePath().toString(), "build", "-x", "test");
                } else if (gradle != null && Files.exists(gradle)) {
                    log.info("⚙️ Compilando via Gradle...");
                    pb = isWindows
                            ? new ProcessBuilder("cmd.exe", "/c", "gradle", "build", "-x", "test")
                            : new ProcessBuilder("gradle", "build", "-x", "test");
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

                boolean success = process.waitFor(5, TimeUnit.MINUTES);
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
