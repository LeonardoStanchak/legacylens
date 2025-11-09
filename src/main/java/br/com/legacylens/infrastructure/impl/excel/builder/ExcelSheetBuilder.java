package br.com.legacylens.infrastructure.impl.excel.builder;

import br.com.legacylens.infrastructure.impl.excel.util.ExcelComparisonUtil;
import br.com.legacylens.infrastructure.impl.excel.util.ExcelStyleUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

import static br.com.legacylens.infrastructure.impl.excel.util.ExcelComparisonUtil.*;
import static br.com.legacylens.infrastructure.impl.excel.util.ExcelStyleUtil.*;

/**
 * 📊 ExcelSheetBuilder
 * -------------------------------------------------------------
 * Responsável por gerar a aba principal "Inventário Legado vs Novo".
 *
 * Utiliza:
 *  - {@link ExcelComparisonUtil} → lista de comparações (dados)
 *  - {@link ExcelStyleUtil} → estilos visuais (cabeçalho, cores, etc)
 *
 * Recursos:
 *  ✅ Cabeçalho estilizado e colorido
 *  ✅ Linhas com cores conforme o status (verde, amarelo, vermelho)
 *  ✅ Autoajuste de colunas e largura mínima
 *  ✅ Tolerância a nulos (Workbook, lista ou estilos)
 *  ✅ Log detalhado de cada etapa
 */
@Slf4j
public class ExcelSheetBuilder {

    /**
     * Cria a aba "Inventário Legado vs Novo" no Workbook.
     *
     * @param wb     workbook ativo
     * @param comparisons lista de comparações (gerada pelo ExcelComparisonUtil)
     * @param styles estilos visuais (gerados pelo ExcelStyleUtil)
     */
    public static void buildInventorySheet(
            XSSFWorkbook wb,
            List<Comparison> comparisons,
            Styles styles
    ) {
        if (wb == null) {
            log.warn("⚠️ Workbook nulo — não foi possível gerar a aba 'Inventário Legado vs Novo'.");
            return;
        }

        try {
            var sheet = wb.createSheet("Inventário Legado vs Novo");
            int rowIdx = 0;

            // ==========================================================
            // 🧱 Cabeçalho
            // ==========================================================
            String[] headers = {"Item", "Versão Legado", "Versão Nova", "Status", "Recomendação"};
            Row headerRow = safeRow(sheet, rowIdx++);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = safeCell(headerRow, i);
                cell.setCellValue(headers[i]);
                safeApplyStyle(cell, styles != null ? styles.header() : null);
            }

            if (comparisons == null || comparisons.isEmpty()) {
                Row emptyRow = safeRow(sheet, rowIdx++);
                Cell cell = safeCell(emptyRow, 0);
                cell.setCellValue("⚠️ Nenhum dado de comparação disponível.");
                safeApplyStyle(cell, styles != null ? styles.warn() : null);
                sheet.autoSizeColumn(0);
                return;
            }

            // ==========================================================
            // 🧩 Linhas de dados
            // ==========================================================
            for (Comparison c : comparisons) {
                Row row = safeRow(sheet, rowIdx++);
                safeCell(row, 0).setCellValue(nvl(c.item()));
                safeCell(row, 1).setCellValue(nvl(c.legacy()));
                safeCell(row, 2).setCellValue(nvl(c.modern()));

                // Coluna de Status
                Cell statusCell = safeCell(row, 3);
                statusCell.setCellValue(nvl(c.status()));
                applyStatusColor(statusCell, c.status(), styles);

                // Coluna de Recomendação
                Cell recCell = safeCell(row, 4);
                recCell.setCellValue(nvl(c.recommendation()));
                safeApplyStyle(recCell, styles != null ? styles.neutral() : null);
            }

            // ==========================================================
            // 📏 Ajuste de colunas e largura mínima
            // ==========================================================
            for (int i = 0; i < headers.length; i++) {
                try {
                    sheet.autoSizeColumn(i);
                    int width = sheet.getColumnWidth(i);
                    if (width < 4500) sheet.setColumnWidth(i, 4500);
                } catch (Exception e) {
                    log.debug("⚠️ Falha ao ajustar coluna {}: {}", i, e.getMessage());
                }
            }

            // Congela cabeçalho (linha 1)
            try {
                sheet.createFreezePane(0, 1);
            } catch (Exception e) {
                log.debug("⚠️ Falha ao aplicar freezePane: {}", e.getMessage());
            }

            // Estilo geral (opcional: gridlines)
            sheet.setDisplayGridlines(true);
            sheet.setZoom(100);

            log.info("✅ Aba 'Inventário Legado vs Novo' criada com sucesso ({} itens).", comparisons.size());

        } catch (Exception e) {
            log.error("❌ Erro ao gerar aba 'Inventário Legado vs Novo': {}", e.getMessage(), e);
            try {
                var sheet = wb.createSheet("Inventário (Erro)");
                Row r = safeRow(sheet, 0);
                safeCell(r, 0).setCellValue("❌ Falha ao gerar inventário: " + e.getMessage());
            } catch (Exception ex) {
                log.error("❌ Falha adicional ao criar aba de erro: {}", ex.getMessage());
            }
        }
    }

    // ==========================================================
    // 🔧 Métodos auxiliares seguros
    // ==========================================================
    private static Row safeRow(Sheet sheet, int idx) {
        try {
            return sheet.getRow(idx) != null ? sheet.getRow(idx) : sheet.createRow(idx);
        } catch (Exception e) {
            log.debug("⚠️ Erro ao criar linha {}: {}", idx, e.getMessage());
            return sheet.createRow(Math.max(0, idx));
        }
    }

    private static Cell safeCell(Row row, int idx) {
        try {
            return row.getCell(idx) != null ? row.getCell(idx) : row.createCell(idx);
        } catch (Exception e) {
            log.debug("⚠️ Erro ao criar célula {}: {}", idx, e.getMessage());
            return row.createCell(Math.max(0, idx));
        }
    }

    private static void applyStatusColor(Cell cell, String status, Styles styles) {
        if (cell == null || styles == null || status == null) return;
        switch (status) {
            case "✅ Atual" -> safeApplyStyle(cell, styles.ok());
            case "⚠️ Atualizar" -> safeApplyStyle(cell, styles.warn());
            case "❌ Legado" -> safeApplyStyle(cell, styles.danger());
            default -> safeApplyStyle(cell, styles.neutral());
        }
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }
}
