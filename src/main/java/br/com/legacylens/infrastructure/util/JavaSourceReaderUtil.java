package br.com.legacylens.infrastructure.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@UtilityClass
public class JavaSourceReaderUtil {

    public String readFile(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ( (line = br.readLine()) != null ) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            log.warn("⚠️ Erro ao ler arquivo {}: {}", file, e.getMessage());
            return "";
        }
    }
}
