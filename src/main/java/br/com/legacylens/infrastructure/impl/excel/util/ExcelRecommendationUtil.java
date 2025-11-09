package br.com.legacylens.infrastructure.impl.excel.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;
import java.util.Objects;

import static br.com.legacylens.infrastructure.impl.excel.util.ExcelComparisonUtil.*;
import static br.com.legacylens.infrastructure.impl.excel.util.ExcelStyleUtil.*;

/**
 * 💡 ExcelRecommendationUtil
 * -------------------------------------------------------------
 * Gera a aba "Recomendações" do relatório Excel.
 * Usa os resultados das comparações (Comparison) e aplica estilos visuais.
 *
 * 100% resiliente — funciona mesmo se a lista vier nula, vazia
 * ou se o Workbook não puder criar células.
 *
 * Recursos:
 *  - Filtro automático: exibe apenas tecnologias desatualizadas
 *  - Fallback completo (mensagem padrão se não houver recomendações)
 *  - Estilos aplicados (cabeçalho, corpo, cores)
 *  - Autoajuste de coluna e espaçamento
 */
@Slf4j
public class ExcelRecommendationUtil {

    /**
     * Cria uma aba "Recomendações" no Workbook Excel.
     *
     * @param wb     workbook ativo
     * @param comps  lista de comparações (gerada pelo ExcelComparisonUtil)
     * @param styles estilos visuais (gerados pelo ExcelStyleUtil)
     */
    public static void buildRecommendationsSheet(
            XSSFWorkbook wb,
            List<Comparison> comps,
            Styles styles
    ) {
        if (wb == null) {
            log.warn("⚠️ Workbook nulo — impossibilitado de criar aba 'Recomendações'.");
            return;
        }

        try {
            var sheet = wb.createSheet("Recomendações");
            int rowIdx = 0;

            // Cabeçalho principal
            Row titleRow = safeRow(sheet, rowIdx++);
            Cell titleCell = safeCell(titleRow, 0);
            titleCell.setCellValue("🔧 Recomendações Automáticas Baseadas na Análise");
            safeApplyStyle(titleCell, styles != null ? styles.header() : null);

            // Linha de espaçamento
            rowIdx++;

            // Filtro dos itens que realmente precisam de atenção
            List<Comparison> outdated = ExcelComparisonUtil.filterOutdated(comps);

            if (outdated == null || outdated.isEmpty()) {
                Row okRow = safeRow(sheet, rowIdx++);
                Cell okCell = safeCell(okRow, 0);
                okCell.setCellValue("✅ Nenhuma recomendação — todas as dependências estão atualizadas!");
                safeApplyStyle(okCell, styles != null ? styles.ok() : null);
                sheet.autoSizeColumn(0);
                return;
            }

            // Cria tabela de recomendações
            Row header = safeRow(sheet, rowIdx++);
            safeCell(header, 0).setCellValue("Item");
            safeCell(header, 1).setCellValue("Versão Legado");
            safeCell(header, 2).setCellValue("Versão Nova");
            safeCell(header, 3).setCellValue("Recomendação");

            // Aplica estilo de cabeçalho
            for (int i = 0; i <= 3; i++) {
                Cell cell = header.getCell(i);
                safeApplyStyle(cell, styles != null ? styles.header() : null);
            }

            // Linhas de conteúdo
            for (Comparison c : outdated) {
                Row row = safeRow(sheet, rowIdx++);
                safeCell(row, 0).setCellValue(nvl(c.item()));
                safeCell(row, 1).setCellValue(nvl(c.legacy()));
                safeCell(row, 2).setCellValue(nvl(c.modern()));
                Cell recCell = safeCell(row, 3);
                recCell.setCellValue(generateHint(c));
                applyStatusColor(recCell, c.status(), styles);
            }

            // Autoajuste de colunas e largura mínima
            for (int i = 0; i <= 3; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width < 5000) sheet.setColumnWidth(i, 5000);
            }

            log.info("✅ Aba 'Recomendações' criada com sucesso ({} itens)", outdated.size());

        } catch (Exception e) {
            log.error("❌ Falha ao gerar aba 'Recomendações': {}", e.getMessage(), e);
            try {
                var sheet = wb.createSheet("Recomendações (Erro)");
                Row r = safeRow(sheet, 0);
                safeCell(r, 0).setCellValue("❌ Falha ao gerar recomendações: " + e.getMessage());
            } catch (Exception ex) {
                log.error("❌ Falha adicional ao criar fallback de recomendações: {}", ex.getMessage());
            }
        }
    }

    // ==========================================================
    // 🔧 Auxiliares seguros e heurísticos
    // ==========================================================
    private static Row safeRow(Sheet sheet, int idx) {
        try {
            return sheet.getRow(idx) != null ? sheet.getRow(idx) : sheet.createRow(idx);
        } catch (Exception e) {
            log.debug("⚠️ Falha ao criar linha {}: {}", idx, e.getMessage());
            return sheet.createRow(Math.max(0, idx));
        }
    }

    private static Cell safeCell(Row row, int idx) {
        try {
            return row.getCell(idx) != null ? row.getCell(idx) : row.createCell(idx);
        } catch (Exception e) {
            log.debug("⚠️ Falha ao criar célula {}: {}", idx, e.getMessage());
            return row.createCell(Math.max(0, idx));
        }
    }

    private static void applyStatusColor(Cell cell, String status, Styles styles) {
        if (cell == null || styles == null) return;
        if (status == null) return;
        switch (status) {
            case "✅ Atual" -> safeApplyStyle(cell, styles.ok());
            case "⚠️ Atualizar" -> safeApplyStyle(cell, styles.warn());
            case "❌ Legado" -> safeApplyStyle(cell, styles.danger());
            default -> safeApplyStyle(cell, styles.neutral());
        }
    }

    private static String generateHint(Comparison c) {
        if (c == null) return "-";
        String item = nvl(c.item());
        String rec = nvl(c.recommendation());
        String legacy = nvl(c.legacy());
        String status = nvl(c.status());

        return switch (status) {
            case "✅ Atual" -> "Sem ação necessária para " + item;
            case "⚠️ Atualizar" -> "Revisar dependência " + item + " — " + rec;
            case "❌ Legado" -> "Migrar urgentemente " + item + " (" + legacy + ")";
            default -> rec;
        };
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }
}
