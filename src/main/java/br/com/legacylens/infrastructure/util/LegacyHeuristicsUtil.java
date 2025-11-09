package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@UtilityClass
public class LegacyHeuristicsUtil {

    // ==========================================================
    // 🔹 Métodos originais (não alterar)
    // ==========================================================
    public String humanizeMethod(String method) {
        Map<String, String> dict = Map.ofEntries(
                Map.entry("get", "buscar"),
                Map.entry("find", "buscar"),
                Map.entry("save", "salvar"),
                Map.entry("traz", "recuperar"),
                Map.entry("load", "carregar"),
                Map.entry("add", "adicionar"),
                Map.entry("create", "criar"),
                Map.entry("update", "atualizar"),
                Map.entry("delete", "remover"),
                Map.entry("persist", "salvar"),
                Map.entry("fetch", "buscar"),
                Map.entry("list", "listar")
        );
        for (var e : dict.entrySet()) {
            if (method.toLowerCase().startsWith(e.getKey()))
                return e.getValue() + method.substring(e.getKey().length());
        }
        return method;
    }

    public boolean shouldIgnoreMethod(String name) {
        String n = name.toLowerCase();
        return n.equals("equals") || n.equals("hashcode") || n.equals("tostring")
                || n.startsWith("get") || n.startsWith("set")
                || n.startsWith("builder") || n.startsWith("log") || n.startsWith("trace")
                || n.startsWith("debug") || n.startsWith("info") || n.startsWith("warn") || n.startsWith("error")
                || n.startsWith("validate"); // ruído comum
    }

    public String identifyClassRole(String content, Path path) {
        String lower = path.toString().toLowerCase();
        String c = content;

        if (c.contains("@RestController") || c.contains("@Controller") || lower.contains("controller"))
            return "controller";

        if (c.contains("@Service") || lower.contains("service") ||
                c.contains("implements SessionBean") || c.contains("@Stateless") || c.contains("@Stateful"))
            return "service";

        if (c.contains("@Repository") || lower.contains("repository") ||
                c.contains("EntityManager") || c.contains("JpaRepository") || c.contains("CrudRepository"))
            return "repository";

        // heurísticas de nome
        if (lower.endsWith("controller.java")) return "controller";
        if (lower.endsWith("service.java") || lower.endsWith("serviceimpl.java")) return "service";
        if (lower.endsWith("repository.java") || lower.endsWith("dao.java")) return "repository";

        return "other";
    }

    public String resolveTarget(String varName, Set<String> classNames) {
        String base = varName.toLowerCase().replace("service", "").replace("repo", "").replace("repository", "");
        return classNames.stream()
                .filter(name -> {
                    String n = name.toLowerCase().replace("service", "").replace("impl", "")
                            .replace("repository", "").replace("dao", "");
                    return base.contains(n) || n.contains(base);
                })
                .findFirst()
                .orElse(null);
    }

    public String normalizeType(String typeName, Set<String> knownClasses) {
        String t = typeName.toLowerCase().replace("impl", "").replace("repository", "").replace("dao", "");
        return knownClasses.stream()
                .filter(c -> {
                    String k = c.toLowerCase().replace("impl", "").replace("repository", "").replace("dao", "");
                    return c.equalsIgnoreCase(typeName) || k.contains(t) || t.contains(k);
                })
                .findFirst()
                .orElse(typeName);
    }

    public boolean isLegacyEJB(String content) {
        return content.contains("@EJB")
                || content.contains("@Stateless")
                || content.contains("@Stateful")
                || content.contains("SessionBean")
                || content.contains("InitialContext")
                || content.contains("lookup(");
    }

    // ==========================================================
    // 🧠 NOVO: Heurísticas complementares para ProjectScan / Excel
    // ==========================================================
    public Map<String, String> detectLibrariesFromSource(Path root) {
        Map<String, String> libs = new HashMap<>();

        if (root == null || !Files.exists(root)) {
            log.warn("⚠️ Caminho inválido para heurísticas de bibliotecas: {}", root);
            return Map.of(
                    "architecture", "Desconhecida",
                    "framework", "Desconhecido",
                    "logging", "Desconhecido",
                    "test", "Desconhecido"
            );
        }

        try (var stream = Files.walk(root, 6)) {
            stream.filter(f -> f.toString().endsWith(".java"))
                    .limit(1000)
                    .forEach(file -> analyzeJavaFile(file, libs));
        } catch (IOException e) {
            log.error("❌ Erro ao varrer projeto para heurísticas: {}", e.getMessage());
        }

        libs.putIfAbsent("architecture", "Desconhecida");
        libs.putIfAbsent("framework", "Desconhecido");
        libs.putIfAbsent("logging", "Desconhecido");
        libs.putIfAbsent("test", "Desconhecido");

        return libs;
    }

    private void analyzeJavaFile(Path file, Map<String, String> libs) {
        try {
            String code = Files.readString(file);

            // Arquitetura
            if (code.contains("@SpringBootApplication")) libs.put("architecture", "Spring Boot");
            else if (isLegacyEJB(code)) libs.put("architecture", "JEE / EJB");
            else if (code.contains("@Microservice")) libs.put("architecture", "Microsserviço");

            // Framework Web
            if (code.contains("@RestController")) libs.put("framework", "Spring REST");
            else if (code.contains("javax.servlet")) libs.put("framework", "Servlet");
            else if (code.contains("JSF")) libs.put("framework", "JSF");

            // Logging
            if (code.contains("log4j")) libs.put("logging", "Log4J");
            else if (code.contains("slf4j")) libs.put("logging", "SLF4J");
            else if (code.contains("logback")) libs.put("logging", "Logback");

            // Testes
            if (code.contains("org.junit.jupiter")) libs.put("test", "JUnit5");
            else if (code.contains("org.junit")) libs.put("test", "JUnit4");
            else if (code.contains("org.testng")) libs.put("test", "TestNG");

        } catch (IOException ignored) {
        }
    }
}
