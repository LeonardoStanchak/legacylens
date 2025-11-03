package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class InjectionResolverUtil {

    /**
     * Detecta variáveis injetadas de acordo com a arquitetura do projeto.
     */
    public Map<String, String> detectInjections(String content, Set<String> knownClasses, String architecture) {
        Map<String, String> map = new HashMap<>();

        // @Autowired / @Inject / @EJB
        Matcher annotated = Pattern.compile("(?:@Autowired|@Inject|@EJB)?\\s*private\\s+(\\w+)\\s+(\\w+);").matcher(content);
        while (annotated.find()) {
            map.put(annotated.group(2), annotated.group(1));
        }

        // new SomeService()
        Matcher directNew = Pattern.compile("new\\s+(\\w+)\\s*\\(").matcher(content);
        while (directNew.find()) {
            String type = directNew.group(1);
            knownClasses.stream()
                    .filter(k -> k.equalsIgnoreCase(type) || type.toLowerCase().contains(k.toLowerCase()))
                    .findFirst()
                    .ifPresent(k -> map.put(k.toLowerCase(), k));
        }

        // EJB InitialContext lookup
        if ("EJB / Java EE".equals(architecture)) {
            Matcher ejbLookup = Pattern.compile("lookup\\(\"java:[^\"]+/(\\w+)\"\\)").matcher(content);
            while (ejbLookup.find()) {
                String bean = ejbLookup.group(1);
                map.put(bean.toLowerCase(), bean);
            }
        }

        // Fallback: busca por private Tipo nome; mesmo sem anotação
        Matcher fallback = Pattern.compile("private\\s+(\\w+)\\s+(\\w+);").matcher(content);
        while (fallback.find()) {
            String type = fallback.group(1);
            String name = fallback.group(2);
            knownClasses.stream()
                    .filter(k -> k.equalsIgnoreCase(type)
                            || k.toLowerCase().contains(name.toLowerCase().replace("service", ""))
                            || name.toLowerCase().contains(k.toLowerCase().replace("impl", "")))
                    .findFirst()
                    .ifPresent(k -> map.put(name, k));
        }

        return map;
    }
}
