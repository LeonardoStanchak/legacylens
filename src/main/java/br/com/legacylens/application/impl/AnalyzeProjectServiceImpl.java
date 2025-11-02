package br.com.legacylens.application.impl;

import br.com.legacylens.application.AnalyzeProjectService;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnalyzeProjectServiceImpl implements AnalyzeProjectService {

    private final ProjectScannerPort scanner;

    public AnalyzeProjectServiceImpl(ProjectScannerPort scanner) {
        this.scanner = scanner;
    }

    @Override
    public ProjectScan execute(String pathOrJar) {
        log.info("Executando análise de projeto: {}", pathOrJar);
        var scan = scanner.scan(pathOrJar);
        log.info("Análise concluída: tipo={} java={} springBoot={}",
                scan.projectType(), scan.javaVersion(), scan.springBootVersion());
        return scan;
    }
}
