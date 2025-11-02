package br.com.legacylens.application;

import br.com.legacylens.domain.model.ProjectScan;
import java.nio.file.Path;

public interface GenerateReportsService {
    void generateAll(ProjectScan scan, String source, Path outDir);
}
