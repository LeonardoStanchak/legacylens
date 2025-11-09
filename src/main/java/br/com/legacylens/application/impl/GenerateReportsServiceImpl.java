package br.com.legacylens.application.impl;

import br.com.legacylens.application.GenerateReportsService;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ExcelReportPort;
import br.com.legacylens.domain.ports.ReadmePort;
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
    private final ReadmePort readme;

    public GenerateReportsServiceImpl(
            UmlGeneratorPort uml,
            SequenceDiagramPort sequence,
            ExcelReportPort excel,
            ReadmePort readme) {
        this.uml = uml;
        this.sequence = sequence;
        this.excel = excel;
        this.readme = readme;
    }

    @Override
    public void generateAll(ProjectScan scan, String source, Path outDir) {
        log.info("Gerando relatórios do projeto analisado em {}", outDir);
        try {
            log.info("📘 Gerando diagrama estrutural UML...");
            uml.generateFromPathOrJar(source, outDir);

            log.info("📗 Gerando diagrama de sequência...");
            sequence.generateFromPathOrJar(source, outDir);

            log.info("📊 Gerando planilha Excel...");
            excel.write(scan, outDir);

            log.info("📝 Gerando README técnico...");
            readme.write(scan, outDir);

            log.info("✅ Relatórios concluídos com sucesso em {}", outDir);
        } catch (Exception e) {
            log.error("❌ Erro durante geração dos relatórios: {}", e.getMessage(), e);
        }
    }
}
