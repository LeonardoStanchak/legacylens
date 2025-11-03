package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class LegacyHeuristicsUtil {

    /**
     * Traduz nomes de métodos para uma forma legível (heurística simples).
     * Ex.: getById -> buscarById, save -> salvar
     */
    public String humanizeMethod(String method) {
        if (method == null || method.isBlank()) return "executar";

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

        String lower = method.toLowerCase();
        for (var e : dict.entrySet()) {
            String k = e.getKey();
            if (lower.startsWith(k)) {
                return e.getValue() + method.substring(k.length());
            }
        }

        // fallback
        if (method.length() > 3 && Character.isUpperCase(method.charAt(3))) {
            return "executar" + method.substring(3);
        }
        return method;
    }

    /**
     * Identifica o papel da classe no sistema.
     */
    public String identifyClassRole(String content, Path path) {
        String lower = path.toString().toLowerCase();
        String c = content;

        if (c.contains("@RestController") || c.contains("@Controller") || lower.contains("controller"))
            return "controller";

        if (c.contains("@Service") || lower.contains("service") || c.contains("implements SessionBean"))
            return "service";

        if (c.contains("@Repository") || lower.contains("repository") || c.contains("EntityManager"))
            return "repository";

        return "other";
    }

    /**
     * Tenta casar uma variável com classes conhecidas (Service/Repository).
     */
    public String resolveTarget(String varName, Set<String> classNames) {
        String norm = varName.toLowerCase().replace("impl", "").replace("service", "").replace("repository", "");
        return classNames.stream()
                .filter(name -> {
                    String n = name.toLowerCase().replace("impl", "").replace("service", "").replace("repository", "");
                    return norm.contains(n) || n.contains(norm);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Normaliza tipos semelhantes ("UserServiceImpl" → "UserService" se existir).
     */
    public String normalizeType(String typeName, Set<String> knownClasses) {
        if (typeName == null) return null;
        return knownClasses.stream()
                .filter(c ->
                        c.equalsIgnoreCase(typeName)
                                || c.toLowerCase().contains(typeName.toLowerCase().replace("impl", ""))
                                || typeName.toLowerCase().contains(c.toLowerCase().replace("impl", ""))
                )
                .findFirst()
                .orElse(typeName);
    }

    /**
     * Detecta indícios de EJB/JEE.
     */
    public boolean isLegacyEJB(String content) {
        return content.contains("@EJB")
                || content.contains("@Stateless")
                || content.contains("@Stateful")
                || content.contains("SessionBean")
                || content.contains("InitialContext")
                || content.contains("lookup(");
    }

    /**
     * Ignora métodos de baixo valor para o fluxo de negócio.
     */
    public boolean shouldIgnoreMethod(String method) {
        String lower = method.toLowerCase();
        return lower.matches("(get.*|set.*|to.*|from.*|equals|hashcode|tostring|builder|mapstruct.*)")
                || lower.contains("dto")
                || lower.contains("entity")
                || lower.contains("converter")
                || lower.contains("util");
    }
}
