package br.com.legacylens.application.impl;

import br.com.legacylens.application.GenerateReportsService;
import br.com.legacylens.config.LegacyLensConfigLoader;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ExcelReportPort;
import br.com.legacylens.domain.ports.SequenceDiagramPort;
import br.com.legacylens.domain.ports.UmlGeneratorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
public class GenerateReportsServiceImpl implements GenerateReportsService {

    private final UmlGeneratorPort uml;
    private final SequenceDiagramPort sequence;
    private final ExcelReportPort excel;

    public GenerateReportsServiceImpl(UmlGeneratorPort uml, SequenceDiagramPort sequence, ExcelReportPort excel) {
        this.uml = uml;
        this.sequence = sequence;
        this.excel = excel;
    }

    @Override
    public void generateAll(ProjectScan scan, String source, Path outDir) {
        var cfg = LegacyLensConfigLoader.get();
        log.info("🚀 Iniciando geração de artefatos (sem README) — destino: {}", outDir);

        try {
            // UML
            log.info("📘 Gerando diagrama UML...");
            var umlResult = uml.generateFromPathOrJar(source, outDir);
            log.debug("UML gerado: {}", umlResult);

            // Sequence
            if (cfg.getSequence() == null || cfg.getSequence().isEnabled()) {
                log.info("📗 Gerando diagramas de sequência...");
                sequence.generateFromPathOrJar(source, outDir);
            }

            // Excel
            if (cfg.getReports() == null || cfg.getReports().getExcel().isEnabled()) {
                log.info("📊 Gerando planilha Excel...");
                excel.write(scan, outDir);
            }

            log.info("✅ Geração concluída em {}", outDir);

        } catch (Exception e) {
            log.error("❌ Falha na geração de relatórios: {}", e.getMessage(), e);
        }
    }
}
