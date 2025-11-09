package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class InjectionResolverUtil {

    /**
     * Retorna um mapa varName -> TypeName para as dependências injetadas no conteúdo.
     * Suporta:
     * - @Autowired/@Inject em campo
     * - Injeção por construtor
     * - @EJB e @Resource
     * - Heurística por declaração de campo privada
     */
    public Map<String, String> detectInjections(String content, Set<String> knownTargets, String architecture) {
        Map<String, String> map = new HashMap<>();

        // 1) Campos com @Autowired/@Inject/@EJB/@Resource
        Pattern annField = Pattern.compile("@(?:Autowired|Inject|EJB|Resource)[^\\n]*\\n\\s*private\\s+(\\w+)\\s+(\\w+)\\s*;");
        Matcher m1 = annField.matcher(content);
        while (m1.find()) {
            map.put(m1.group(2), m1.group(1));
        }

        // 2) Campos privados típicos (sem anotação)
        Pattern plainField = Pattern.compile("\\bprivate\\s+(\\w+)\\s+(\\w+)\\s*;");
        Matcher m2 = plainField.matcher(content);
        while (m2.find()) {
            String type = m2.group(1);
            String var = m2.group(2);
            if (isKnown(type, knownTargets)) map.putIfAbsent(var, type);
        }

        // 3) Construtor com args (injeção por construtor)
        //   public Classe( TipoA a, TipoB b, ... ) { this.a = a; this.b = b; ... }
        Pattern ctor = Pattern.compile("\\bpublic\\s+\\w+\\s*\\(([^)]*)\\)\\s*\\{");
        Matcher m3 = ctor.matcher(content);
        while (m3.find()) {
            String args = m3.group(1);
            Matcher arg = Pattern.compile("(\\w+)\\s+(\\w+)").matcher(args);
            List<String> ctorVars = new ArrayList<>();
            Map<String, String> ctorTypes = new HashMap<>();
            while (arg.find()) {
                String type = arg.group(1);
                String name = arg.group(2);
                ctorVars.add(name);
                ctorTypes.put(name, type);
            }
            // this.var = var;
            Matcher assigns = Pattern.compile("this\\.(\\w+)\\s*=\\s*(\\w+)\\s*;").matcher(content);
            while (assigns.find()) {
                String field = assigns.group(1);
                String passed = assigns.group(2);
                if (ctorVars.contains(passed)) {
                    String type = ctorTypes.get(passed);
                    if (isKnown(type, knownTargets)) map.putIfAbsent(field, type);
                }
            }
        }

        return map;
    }

    private boolean isKnown(String type, Set<String> known) {
        String t = type.toLowerCase().replace("impl", "").replace("repository", "").replace("dao", "").replace("service","");
        return known.stream().anyMatch(k -> {
            String kk = k.toLowerCase().replace("impl", "").replace("repository", "").replace("dao", "").replace("service","");
            return kk.equals(t) || kk.contains(t) || t.contains(kk);
        });
    }
}
