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

    public List<EndpointDoc> extractEndpointDocs(String controllerContent, Path srcDir) {
        List<EndpointDoc> docs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Detecta métodos REST com @Get/Post/Put/DeleteMapping
        Matcher m = Pattern.compile(
                "@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)[^\\n]*\\n\\s*"
                        + "(?:@[\\w\\(\\)\"=,\\s]+\\n\\s*)*"
                        + "(public|protected|private)?\\s+[^{;]+\\s+(\\w+)\\s*\\("
        ).matcher(controllerContent);

        while (m.find()) {
            String methodName = m.group(2);
            if (!seen.add(methodName)) continue;

            EndpointDoc doc = new EndpointDoc();
            doc.setMethodName(methodName);

            // Tipo de retorno
            Matcher retMatcher = Pattern.compile("(?:public|protected|private)?\\s*([\\w<>\\[\\]\\.?\\s]+)\\s+"
                    + Pattern.quote(methodName) + "\\s*\\(").matcher(controllerContent);
            if (retMatcher.find()) {
                String type = retMatcher.group(1).trim();
                if (type.contains("ResponseEntity")) {
                    type = type.replace("ResponseEntity<", "").replace(">", "").trim();
                }
                doc.setResponseDto(type);
            }

            // @ResponseStatus(code = HttpStatus.XYZ)
            Matcher status = Pattern.compile("@ResponseStatus\\s*\\(\\s*code\\s*=\\s*HttpStatus\\.(\\w+)\\s*\\)")
                    .matcher(controllerContent);
            if (status.find()) {
                doc.setResponseCode(status.group(1));
            } else {
                doc.setResponseCode("200 OK");
            }

            // DTO de entrada (@RequestBody) — pega o 1º do método
            // (não perfeito, mas ajuda bastante)
            Pattern bodySig = Pattern.compile("@RequestBody\\s*(?:@Valid\\s*)?(?:final\\s+)?(\\w+)\\s+(\\w+)");
            Matcher bodyMatcher = bodySig.matcher(controllerContent);
            if (bodyMatcher.find()) {
                String dto = bodyMatcher.group(1);
                doc.setRequestDto(dto);
                doc.setDtoFields(extractDtoFields(srcDir, dto));
            }

            docs.add(doc);
        }

        return docs;
    }

    private List<String> extractDtoFields(Path srcDir, String dtoName) {
        try (var stream = Files.walk(srcDir)) {
            Optional<Path> dtoFile = stream
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(dtoName + ".java"))
                    .findFirst();

            if (dtoFile.isEmpty()) return List.of();

            String content = Files.readString(dtoFile.get());
            // Campos simples (ignora static e constantes)
            Matcher fieldMatcher = Pattern.compile("\\bprivate\\s+(?!static)([\\w<>\\[\\]]+)\\s+(\\w+)\\s*;")
                    .matcher(content);
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
