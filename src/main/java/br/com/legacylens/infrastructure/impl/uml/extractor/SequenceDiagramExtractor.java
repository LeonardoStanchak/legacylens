package br.com.legacylens.infrastructure.impl.uml.extractor;

import br.com.legacylens.domain.model.UmlDiagram;
import br.com.legacylens.domain.ports.SequenceDiagramPort;
import br.com.legacylens.infrastructure.util.InjectionResolverUtil;
import br.com.legacylens.infrastructure.util.JavaSourceReaderUtil;
import br.com.legacylens.infrastructure.util.LegacyHeuristicsUtil;
import br.com.legacylens.infrastructure.util.SwaggerExtractorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SequenceDiagramExtractor implements SequenceDiagramPort {

    @Override
    public UmlDiagram generateFromPathOrJar(String source, Path outDir) {
        Instant start = Instant.now();
        log.info("===== [SequenceUML] Iniciando geração dos diagramas de sequência =====");
        log.info("📦 Projeto: {}", source);

        try {
            Path srcDir = findSourceDirectory(Path.of(source));
            if (srcDir == null) {
                log.error("❌ Nenhum diretório src/main/java encontrado em {}", source);
                return new UmlDiagram("sequence-error.puml");
            }

            String architecture = detectArchitecture(srcDir);
            log.info("🏗️ Arquitetura detectada: {}", architecture);

            Map<String, Path> controllers = new LinkedHashMap<>();
            Map<String, Path> services = new LinkedHashMap<>();
            Map<String, Path> repositories = new LinkedHashMap<>();

            try (var stream = Files.walk(srcDir)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".java"))
                        .forEach(path -> {
                            String content = JavaSourceReaderUtil.readFile(path);
                            String className = getClassName(content);
                            if (className == null || className.isBlank()) return;

                            String role = LegacyHeuristicsUtil.identifyClassRole(content, Path.of(path.toString().toLowerCase()));
                            switch (role) {
                                case "controller" -> controllers.put(className, path);
                                case "service" -> services.put(className, path);
                                case "repository" -> repositories.put(className, path);
                            }
                        });
            }

            log.info("📘 Controllers: {}", controllers.keySet());
            log.info("📗 Services: {}", services.keySet());
            log.info("📙 Repositories: {}", repositories.keySet());

            for (Map.Entry<String, Path> entry : controllers.entrySet()) {
                String controller = entry.getKey();
                Path controllerPath = entry.getValue();

                StringBuilder puml = new StringBuilder();
                puml.append("@startuml\n")
                        .append("' Gerado automaticamente pelo LegacyLens\n")
                        .append("title 🧩 Diagrama de Sequência - ").append(controller).append("\n")
                        .append("autonumber\n")
                        .append("actor Usuário\n")
                        .append("skinparam participant {\n")
                        .append("  BackgroundColor<<Controller>> #A9D0F5\n")
                        .append("  BackgroundColor<<Service>> #A9F5BC\n")
                        .append("  BackgroundColor<<Repository>> #F5A9A9\n")
                        .append("}\n\n");

                Set<String> participantsAdded = new HashSet<>();
                Set<String> databasesAdded = new HashSet<>();

                // define participantes
                puml.append("participant ").append(controller).append(" <<Controller>>\n");
                participantsAdded.add(controller);

                analyzeControllerFlow(controller, controllerPath, srcDir, services, repositories,
                        architecture, puml, participantsAdded, databasesAdded);

                puml.append("@enduml\n");

                try {
                    Files.createDirectories(outDir);
                    Path output = outDir.resolve("sequence_" + controller + ".puml");
                    Files.writeString(output, puml.toString().replace("\\n", "\n"));
                    log.info("✅ Diagrama de sequência gerado: {}", output);
                } catch (IOException e) {
                    log.error("❌ Falha ao salvar diagrama de {}: {}", controller, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Erro durante a geração dos diagramas: {}", e.getMessage(), e);
        }

        long ms = Duration.between(start, Instant.now()).toMillis();
        log.info("===== [SequenceUML] Geração concluída em {} ms =====", ms);
        return new UmlDiagram("sequence-[per-controller].puml");
    }

    private void analyzeControllerFlow(String controller,
                                       Path controllerPath,
                                       Path srcDir,
                                       Map<String, Path> services,
                                       Map<String, Path> repositories,
                                       String architecture,
                                       StringBuilder puml,
                                       Set<String> participantsAdded,
                                       Set<String> databasesAdded) {

        String controllerContent = JavaSourceReaderUtil.readFile(controllerPath);
        Map<String, String> injectedServices =
                InjectionResolverUtil.detectInjections(controllerContent, services.keySet(), architecture);

        List<SwaggerExtractorUtil.EndpointDoc> endpoints =
                SwaggerExtractorUtil.extractEndpointDocs(controllerContent, srcDir);

        if (endpoints.isEmpty()) {
            log.warn("⚠️ Nenhum endpoint encontrado em {}", controller);
            return;
        }

        puml.append("group Fluxos de ").append(controller).append("\n");
        Set<String> processedCalls = new HashSet<>();

        for (SwaggerExtractorUtil.EndpointDoc doc : endpoints) {
            String endpointName = Optional.ofNullable(doc.getMethodName()).orElse("unknown");
            String methodBody = extractMethodBody(controllerContent, endpointName);
            if (methodBody.isBlank()) continue;

            // Agrupamento por endpoint
            puml.append("group ").append(endpointName).append("()\n");

            // Nota lateral
            puml.append("note right of ").append(controller).append(" #DDDDDD\n")
                    .append("Método: ").append(endpointName).append("()\n")
                    .append("Request: ").append(Optional.ofNullable(doc.getRequestDto()).orElse("Sem corpo")).append("\n");
            if (!doc.getDtoFields().isEmpty()) {
                for (String f : doc.getDtoFields()) puml.append("     ").append(f).append("\n");
            }
            puml.append("Response: ").append(Optional.ofNullable(doc.getResponseCode()).orElse("200 OK")).append("\n")
                    .append("end note\n");

            Matcher callMatcher = Pattern.compile("(\\w+)\\.(\\w+)\\(").matcher(methodBody);
            while (callMatcher.find()) {
                String var = callMatcher.group(1);
                String calledMethod = callMatcher.group(2);
                if (LegacyHeuristicsUtil.shouldIgnoreMethod(calledMethod)) continue;

                String targetType = injectedServices.getOrDefault(
                        var, LegacyHeuristicsUtil.resolveTarget(var, services.keySet())
                );
                if (targetType == null) continue;

                String serviceClass = LegacyHeuristicsUtil.normalizeType(targetType, services.keySet());
                if (serviceClass == null) continue;

                String callKey = controller + "#" + serviceClass + "#" + calledMethod;
                if (!processedCalls.add(callKey)) continue;

                if (participantsAdded.add(serviceClass)) {
                    puml.append("participant ").append(serviceClass).append(" <<Service>>\n");
                }

                // Fluxo controller -> service
                puml.append(controller)
                        .append(" -> ")
                        .append(serviceClass)
                        .append(" : ")
                        .append(LegacyHeuristicsUtil.humanizeMethod(calledMethod))
                        .append("()\n");

                analyzeServiceFlow(serviceClass, services.get(serviceClass),
                        repositories, architecture, puml, calledMethod, participantsAdded, databasesAdded);
            }

            puml.append("end\n\n");
        }

        puml.append("end\n\n");
    }

    private void analyzeServiceFlow(String serviceName,
                                    Path servicePath,
                                    Map<String, Path> repositories,
                                    String architecture,
                                    StringBuilder puml,
                                    String calledMethod,
                                    Set<String> participantsAdded,
                                    Set<String> databasesAdded) {
        if (servicePath == null || calledMethod == null) return;

        String serviceContent = JavaSourceReaderUtil.readFile(servicePath);
        Map<String, String> injectedRepos =
                InjectionResolverUtil.detectInjections(serviceContent, repositories.keySet(), architecture);

        String methodBody = extractMethodBody(serviceContent, calledMethod);
        if (methodBody.isBlank()) return;

        Matcher repoCall = Pattern.compile("(\\w+)\\.(\\w+)\\(").matcher(methodBody);
        Set<String> processedRepoCalls = new HashSet<>();

        while (repoCall.find()) {
            String var = repoCall.group(1);
            String repoMethod = repoCall.group(2);
            if (LegacyHeuristicsUtil.shouldIgnoreMethod(repoMethod)) continue;

            String targetType = injectedRepos.getOrDefault(
                    var, LegacyHeuristicsUtil.resolveTarget(var, repositories.keySet())
            );
            if (targetType == null) continue;

            String repoClass = LegacyHeuristicsUtil.normalizeType(targetType, repositories.keySet());
            if (repoClass == null) continue;

            String repoKey = repoClass + "#" + repoMethod;
            if (!processedRepoCalls.add(repoKey)) continue;

            if (databasesAdded.add(repoClass)) {
                puml.append("database ").append(repoClass).append(" <<Repository>>\n");
            }

            // Fluxo service -> repo
            puml.append(serviceName)
                    .append(" -> ")
                    .append(repoClass)
                    .append(" : ")
                    .append(LegacyHeuristicsUtil.humanizeMethod(repoMethod))
                    .append("()\n")
                    .append(repoClass)
                    .append(" --> ")
                    .append(serviceName)
                    .append(" : resultado\n");
        }

        // processamento interno
        puml.append(serviceName).append(" --> ").append(serviceName).append(" : processamento interno\n");
        puml.append(serviceName).append(" --> ").append(" ").append("return\n");
    }

    private String detectArchitecture(Path srcDir) {
        try (var stream = Files.walk(srcDir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .map(JavaSourceReaderUtil::readFile)
                    .map(content -> {
                        if (content.contains("@RestController") || content.contains("@SpringBootApplication"))
                            return "Spring Boot";
                        if (content.contains("@EJB") || content.contains("@Stateless") || content.contains("SessionBean"))
                            return "EJB / Java EE";
                        if (content.contains("extends HttpServlet") || content.contains("@WebServlet"))
                            return "Servlet / JEE";
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Java Puro");
        } catch (Exception e) {
            log.warn("⚠️ Falha ao detectar arquitetura: {}", e.getMessage());
            return "Desconhecida";
        }
    }

    private String getClassName(String content) {
        Matcher m = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private Path findSourceDirectory(Path root) throws IOException {
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

    private String extractMethodBody(String content, String methodName) {
        String signatureRegex =
                "(?s)@?[\\w\\s\\(\\)\\.,\"=:/_-]*?\\b(public|protected|private)?\\s*[\\w<>,\\[\\]\\s]+\\b"
                        + Pattern.quote(methodName) + "\\s*\\([^\\)]*\\)\\s*\\{";
        Matcher startM = Pattern.compile(signatureRegex).matcher(content);
        if (!startM.find()) return "";

        int start = startM.end() - 1;
        int i = start;
        int depth = 0;
        while (i < content.length()) {
            char ch = content.charAt(i++);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return content.substring(start + 1, i - 1).trim();
            }
        }
        return "";
    }
}
