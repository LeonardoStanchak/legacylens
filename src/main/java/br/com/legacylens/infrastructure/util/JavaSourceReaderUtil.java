package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@UtilityClass
public class JavaSourceReaderUtil {

    /**
     * Lê o conteúdo do arquivo .java e normaliza encoding e comentários.
     */
    public String readFile(Path file) {
        try {
            if (!Files.exists(file)) return "";

            // tenta UTF-8 e fallback para ISO-8859-1
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.contains("�")) {
                content = Files.readString(file, Charset.forName("ISO-8859-1"));
            }

            return cleanComments(content);
        } catch (IOException e) {
            log.warn("⚠️ Erro ao ler arquivo {}: {}", file, e.getMessage());
            return "";
        }
    }

    /**
     * Remove comentários e javadocs antes da análise.
     */
    private String cleanComments(String code) {
        String noBlockComments = code.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noLineComments = noBlockComments.replaceAll("//.*", " ");
        return noLineComments.replaceAll("\\s+", " ").trim();
    }

    /**
     * Conta o número de linhas de código (pode ser usado para métricas).
     */
    public int countLines(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file)) {
            return (int) br.lines().count();
        } catch (IOException e) {
            return 0;
        }
    }
}
