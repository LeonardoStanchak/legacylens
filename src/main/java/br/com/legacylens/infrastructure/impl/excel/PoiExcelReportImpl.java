package br.com.legacylens.infrastructure.impl.excel;

import br.com.legacylens.domain.model.ExcelReport;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ExcelReportPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Path;

@Slf4j
@Component
public class PoiExcelReportImpl implements ExcelReportPort {

    @Override
    public ExcelReport write(ProjectScan scan, Path outDir) {
        var wb = new XSSFWorkbook();
        try {
            var inv = wb.createSheet("Inventário Legado");
            var header = inv.createRow(0);
            header.createCell(0).setCellValue("Item");
            header.createCell(1).setCellValue("Valor");

            var javaRow = inv.createRow(1);
            javaRow.createCell(0).setCellValue("Java");
            javaRow.createCell(1).setCellValue(nvl(scan.javaVersion()));

            var springRow = inv.createRow(2);
            springRow.createCell(0).setCellValue("Spring");
            springRow.createCell(1).setCellValue(nvl(scan.springVersion()));

            var bootRow = inv.createRow(3);
            bootRow.createCell(0).setCellValue("Spring Boot");
            bootRow.createCell(1).setCellValue(nvl(scan.springBootVersion()));

            var rec = wb.createSheet("Recomendações");
            rec.createRow(0).createCell(0).setCellValue("Java 17 LTS / Spring Boot 3.5.x / Jakarta");
            rec.createRow(1).createCell(0).setCellValue("Micrometer + OTel / OpenAPI / JUnit 5");

            Path output = outDir.resolve("LegacyLens-Legado-vs-Novo.xlsx");
            try (var fos = new FileOutputStream(output.toFile())) {
                wb.write(fos);
            }
            log.info("Planilha Excel gerada em {}", output);
            return new ExcelReport(output.getFileName().toString());
        } catch (Exception e) {
            log.error("Erro ao gerar planilha Excel: {}", e.getMessage(), e);
            return new ExcelReport("error.xlsx");
        } finally {
            try { wb.close(); } catch (Exception ignored) {}
        }
    }

    private static String nvl(String s) {
        return s == null ? "-" : s;
    }
}
