package br.com.legacylens.infrastructure.util;

import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class SwaggerExtractorUtil {

    @Data
    public static class EndpointDoc {
        private String methodName;
        private String requestDto;
        private String responseDto;
        private String responseCode;
        private List<String> dtoFields = new ArrayList<>();
    }

    /**
     * Extrai metadados de endpoints REST (mesmo sem ResponseEntity).
     */
    public List<EndpointDoc> extractEndpointDocs(String controllerContent, Path srcDir) {
        List<EndpointDoc> docs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 🧩 Detecta métodos REST (Get/Post/Put/DeleteMapping)
        Matcher m = Pattern.compile(
                "@(?:GetMapping|PostMapping|PutMapping|DeleteMapping)[^\\n]*\\n\\s*public\\s+[^{;]+\\s+(\\w+)\\s*\\("
        ).matcher(controllerContent);

        while (m.find()) {
            String methodName = m.group(1);
            if (!seen.add(methodName)) continue;

            EndpointDoc doc = new EndpointDoc();
            doc.setMethodName(methodName);

            // 🧠 Detecta tipo de retorno
            Matcher retMatcher = Pattern.compile("public\\s+([\\w<>]+)\\s+" + methodName).matcher(controllerContent);
            if (retMatcher.find()) {
                String type = retMatcher.group(1);
                doc.setResponseDto(type.contains("ResponseEntity") ? type.replace("ResponseEntity<", "").replace(">", "") : type);
            }

            // 🧩 Detecta DTO de entrada
            Matcher bodyMatcher = Pattern.compile("@RequestBody\\s*(?:final\\s+)?(\\w+)\\s+(\\w+)").matcher(controllerContent);
            if (bodyMatcher.find()) {
                String dto = bodyMatcher.group(1);
                doc.setRequestDto(dto);
                doc.setDtoFields(extractDtoFields(srcDir, dto));
            }

            doc.setResponseCode("200 OK");
            docs.add(doc);
        }

        return docs;
    }

    /**
     * Lê os campos principais de um DTO (para exibir no diagrama).
     */
    private List<String> extractDtoFields(Path srcDir, String dtoName) {
        try (var stream = Files.walk(srcDir)) {
            Optional<Path> dtoFile = stream
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(dtoName + ".java"))
                    .findFirst();

            if (dtoFile.isEmpty()) return List.of();

            String content = Files.readString(dtoFile.get());
            Matcher fieldMatcher = Pattern.compile("private\\s+([\\w<>\\[\\]]+)\\s+(\\w+)\\s*;").matcher(content);
            List<String> fields = new ArrayList<>();

            while (fieldMatcher.find()) {
                String type = fieldMatcher.group(1);
                String name = fieldMatcher.group(2);
                fields.add(name + ": " + type);
            }
            return fields;
        } catch (IOException e) {
            log.warn("⚠️ Falha ao ler DTO {}: {}", dtoName, e.getMessage());
            return List.of();
        }
    }
}
