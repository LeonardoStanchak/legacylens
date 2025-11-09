package br.com.legacylens.application.impl;

import br.com.legacylens.application.AnalyzeProjectService;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ProjectScannerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 🚀 Camada de aplicação responsável por orquestrar a análise do projeto.
 * Interage com o ProjectScannerPort (que seleciona Maven, Gradle ou JAR)
 * e aplica enriquecimentos automáticos.
 */
@Slf4j
@Service
public class AnalyzeProjectServiceImpl implements AnalyzeProjectService {

    private final ProjectScannerPort scanner;

    public AnalyzeProjectServiceImpl(ProjectScannerPort scanner) {
        this.scanner = scanner;
    }

    @Override
    public ProjectScan execute(String pathOrJar) {
        log.info("🚀 Executando análise de projeto: {}", pathOrJar);

        var scan = scanner.scan(pathOrJar);

        if (scan == null) {
            log.error("❌ Falha: scanner retornou null para {}", pathOrJar);
            return new ProjectScan("ERROR", null, null, null, java.util.Map.of());
        }

        log.info("""
                🧩 Análise concluída:
                  • Tipo: {}
                  • Java: {}
                  • Spring: {}
                  • Boot: {}
                  • Libs detectadas: {}
                """,
                scan.projectType(), scan.javaVersion(), scan.springVersion(), scan.springBootVersion(), scan.libraries().size());

        return scan;
    }
}
