package br.com.legacylens.domain.ports;

import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.model.ExcelReport;
import java.nio.file.Path;

public interface ExcelReportPort {
    ExcelReport write(ProjectScan scan, Path outDir);
}
