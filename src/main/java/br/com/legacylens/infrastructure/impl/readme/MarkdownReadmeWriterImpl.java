package br.com.legacylens.infrastructure.impl.readme;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ReadmePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Slf4j
@Component
public class MarkdownReadmeWriterImpl implements ReadmePort {

    @Override
    public Path write(ProjectScan scan, Path outDir) {
        String md = """
                # LegacyLens — Relatório de Análise

                ## Resumo
                | Item | Valor |
                |---|---|
                | Java | %s |
                | Spring | %s |
                | Spring Boot | %s |

                ## Artefatos
                - UML: `diagram.puml`
                - Excel: `LegacyLens-Legado-vs-Novo.xlsx`

                ## Recomendações
                - Migrar para Java 17 / Spring Boot 3.5.x / Jakarta
                - Observabilidade: Micrometer + OpenTelemetry
                - Documentação: springdoc-openapi
                """.formatted(
                nvl(scan.javaVersion()),
                nvl(scan.springVersion()),
                nvl(scan.springBootVersion())
        );
        try {
            Path output = outDir.resolve("README.md");
            Files.writeString(output, md);
            log.info("README técnico gerado em {}", output);
            return output;
        } catch (Exception e) {
            log.error("Erro ao gerar README: {}", e.getMessage(), e);
            return outDir.resolve("README-error.md");
        }
    }

    private String nvl(String s) {
        return s == null ? "-" : s;
    }
}
