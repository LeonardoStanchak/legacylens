package br.com.legacylens.application.impl;

import br.com.legacylens.application.GenerateReportsService;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ExcelReportPort;
import br.com.legacylens.domain.ports.ReadmePort;
import br.com.legacylens.domain.ports.UmlGeneratorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
public class GenerateReportsServiceImpl implements GenerateReportsService {

    private final UmlGeneratorPort uml;
    private final ExcelReportPort excel;
    private final ReadmePort readme;

    public GenerateReportsServiceImpl(UmlGeneratorPort uml, ExcelReportPort excel, ReadmePort readme) {
        this.uml = uml;
        this.excel = excel;
        this.readme = readme;
    }

    @Override
    public void generateAll(ProjectScan scan, String source, Path outDir) {
        log.info("Gerando relatórios do projeto analisado em {}", outDir);
        uml.generateFromPathOrJar(source, outDir);
        excel.write(scan, outDir);
        readme.write(scan, outDir);
        log.info("Relatórios concluídos com sucesso.");
    }
}
