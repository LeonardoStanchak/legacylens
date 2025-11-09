package br.com.legacylens.infrastructure.impl.excel;

import br.com.legacylens.domain.model.ExcelReport;
import br.com.legacylens.domain.model.ProjectScan;
import br.com.legacylens.domain.ports.ExcelReportPort;
import br.com.legacylens.infrastructure.impl.excel.util.ExcelComparisonUtil;
import br.com.legacylens.infrastructure.impl.excel.util.ExcelRecommendationUtil;
import br.com.legacylens.infrastructure.impl.excel.util.ExcelStyleUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 📊 Gera planilha Excel comparativa "Legado vs Novo".
 * ---------------------------------------------------
 * Inclui:
 *  - Aba principal (Inventário Legado vs Novo)
 *  - Aba de Recomendações automáticas
 *  - Aba de Bibliotecas detectadas (todas as dependências)
 *
 * 100% Apache POI — resiliente, modular e preparado para expansão.
 */
@Slf4j
@Component
public class PoiExcelReportImpl implements ExcelReportPort {

    @Override
    public ExcelReport write(ProjectScan scan, Path outDir) {
        try (var wb = new XSSFWorkbook()) {

            // ==========================================================
            // 🎨 Estilos padronizados
            // ==========================================================
            ExcelStyleUtil.Styles styles = ExcelStyleUtil.createStyles(wb);

            // ==========================================================
            // 📋 Comparações baseadas no ProjectScan
            // ==========================================================
            List<ExcelComparisonUtil.Comparison> comps = ExcelComparisonUtil.buildComparisons(scan);

            // ==========================================================
            // 🧾 Aba principal — Inventário comparativo
            // ==========================================================
            Sheet inv = wb.createSheet("Inventario_Legado_vs_Novo");
            createInventorySheet(inv, comps, styles);

            // ==========================================================
            // 💡 Aba de Recomendações automáticas
            // ==========================================================
            ExcelRecommendationUtil.buildRecommendationsSheet(wb, comps, styles);

            // ==========================================================
            // 📚 Aba de Bibliotecas detectadas
            // ==========================================================
            createLibrariesSheet(wb, scan, styles);

            // ==========================================================
            // 💾 Salvamento seguro
            // ==========================================================
            Files.createDirectories(outDir);
            Path output = outDir.resolve("LegacyLens-Legado-vs-Novo.xlsx");

            try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
                wb.write(fos);
            }

            log.info("✅ Planilha Excel gerada com sucesso em {}", output);
            return new ExcelReport(output.getFileName().toString());

        } catch (Exception e) {
            log.error("❌ Erro ao gerar planilha Excel: {}", e.getMessage(), e);
            return new ExcelReport("error.xlsx");
        }
    }

    // ==========================================================
    // 📄 1️⃣ Aba: Inventário comparativo
    // ==========================================================
    private void createInventorySheet(Sheet inv, List<ExcelComparisonUtil.Comparison> comps,
                                      ExcelStyleUtil.Styles styles) {
        if (inv == null || comps == null) return;

        Row header = inv.createRow(0);
        String[] cols = {"Item", "Versão Legado", "Versão Nova", "Status", "Recomendação"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            ExcelStyleUtil.safeApplyStyle(cell, styles.header());
        }

        int rowNum = 1;
        for (ExcelComparisonUtil.Comparison c : comps) {
            Row row = inv.createRow(rowNum++);
            row.createCell(0).setCellValue(nvl(c.item()));
            row.createCell(1).setCellValue(nvl(c.legacy()));
            row.createCell(2).setCellValue(nvl(c.modern()));
            Cell statusCell = row.createCell(3);
            statusCell.setCellValue(cleanStatus(c.status()));
            Cell recCell = row.createCell(4);
            recCell.setCellValue(nvl(c.recommendation()));

            applyStatusColor(statusCell, c.status(), styles);
        }

        for (int i = 0; i < cols.length; i++) inv.autoSizeColumn(i);
        log.info("📄 Aba 'Inventario_Legado_vs_Novo' criada com {} registros.", comps.size());
    }

    // ==========================================================
    // 📚 2️⃣ Aba: Bibliotecas detectadas
    // ==========================================================
    private void createLibrariesSheet(XSSFWorkbook wb, ProjectScan scan, ExcelStyleUtil.Styles styles) {
        try {
            Sheet sheet = wb.createSheet("Bibliotecas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Dependência");
            header.createCell(1).setCellValue("Versão");
            ExcelStyleUtil.safeApplyStyle(header.getCell(0), styles.header());
            ExcelStyleUtil.safeApplyStyle(header.getCell(1), styles.header());

            Map<String, String> libs = Optional.ofNullable(scan.libraries()).orElse(Map.of());
            if (libs.isEmpty()) {
                Row emptyRow = sheet.createRow(1);
                emptyRow.createCell(0).setCellValue("Nenhuma dependência detectada.");
                ExcelStyleUtil.safeApplyStyle(emptyRow.getCell(0), styles.neutral());
            } else {
                int rowIdx = 1;
                for (Map.Entry<String, String> entry : libs.entrySet()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(entry.getKey());
                    Cell versionCell = row.createCell(1);
                    versionCell.setCellValue(nvl(entry.getValue()));
                    applyLibraryHighlight(row.getCell(0), entry.getKey(), styles);
                    ExcelStyleUtil.safeApplyStyle(versionCell, styles.neutral());
                }
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            log.info("📚 Aba 'Bibliotecas' criada com {} dependências.", libs.size());
        } catch (Exception e) {
            log.error("❌ Falha ao gerar aba 'Bibliotecas': {}", e.getMessage(), e);
        }
    }

    // ==========================================================
    // 🎨 Cores para libs legadas (log4j, javax, etc.)
    // ==========================================================
    private void applyLibraryHighlight(Cell cell, String lib, ExcelStyleUtil.Styles styles) {
        if (cell == null || lib == null) return;
        String lower = lib.toLowerCase();
        if (lower.contains("log4j") || lower.contains("javax") ||
                lower.contains("struts") || lower.contains("springfox")) {
            ExcelStyleUtil.safeApplyStyle(cell, styles.danger());
        } else if (lower.contains("spring-boot") || lower.contains("lombok") ||
                lower.contains("slf4j") || lower.contains("logback")) {
            ExcelStyleUtil.safeApplyStyle(cell, styles.ok());
        } else {
            ExcelStyleUtil.safeApplyStyle(cell, styles.neutral());
        }
    }

    // ==========================================================
    // 🧱 Utilitários internos
    // ==========================================================
    private void applyStatusColor(Cell cell, String status, ExcelStyleUtil.Styles styles) {
        if (cell == null || status == null) return;
        switch (status) {
            case "✅ Atual", "Atual" -> ExcelStyleUtil.safeApplyStyle(cell, styles.ok());
            case "⚠️ Atualizar", "Atualizar" -> ExcelStyleUtil.safeApplyStyle(cell, styles.warn());
            case "❌ Legado", "Legado" -> ExcelStyleUtil.safeApplyStyle(cell, styles.danger());
            default -> ExcelStyleUtil.safeApplyStyle(cell, styles.neutral());
        }
    }

    private static String cleanStatus(String s) {
        return (s == null) ? "-" : s.replace("✅", "").replace("⚠️", "")
                .replace("❌", "").trim();
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }
}
