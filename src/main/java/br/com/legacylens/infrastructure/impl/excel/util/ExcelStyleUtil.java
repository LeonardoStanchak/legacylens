package br.com.legacylens.infrastructure.impl.excel.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 🎨 ExcelStyleUtil
 * -------------------------------------------------------------
 * Cria e padroniza estilos visuais reutilizáveis para relatórios Excel.
 * Totalmente à prova de falhas (fallbacks automáticos se o workbook for nulo).
 *
 * Estilos disponíveis:
 *  - header(): cabeçalho principal com fundo cinza e negrito
 *  - ok(): fundo verde-claro (para itens atualizados)
 *  - warn(): fundo amarelo-claro (para itens que precisam de atualização)
 *  - danger(): fundo vermelho-claro (para itens legados)
 *  - neutral(): fundo branco com borda simples
 *
 * 100% Apache POI (gratuito)
 */
@Slf4j
public class ExcelStyleUtil {

    /**
     * Container de estilos predefinidos.
     */
    public record Styles(
            CellStyle header,
            CellStyle ok,
            CellStyle warn,
            CellStyle danger,
            CellStyle neutral
    ) {}

    /**
     * Cria e retorna todos os estilos padronizados.
     * Se o workbook for nulo, retorna um conjunto vazio funcional.
     */
    public static Styles createStyles(XSSFWorkbook wb) {
        if (wb == null) {
            log.warn("⚠️ Workbook nulo — retornando estilos default simples.");
            return new Styles(null, null, null, null, null);
        }

        try {
            return new Styles(
                    createHeaderStyle(wb),
                    createColorStyle(wb, IndexedColors.LIGHT_GREEN, true),
                    createColorStyle(wb, IndexedColors.LIGHT_YELLOW, true),
                    createColorStyle(wb, IndexedColors.ROSE, true),
                    createColorStyle(wb, IndexedColors.WHITE, false)
            );
        } catch (Exception e) {
            log.error("❌ Falha ao criar estilos Excel: {}", e.getMessage(), e);
            // fallback seguro (estilos neutros)
            CellStyle neutral = createFallbackStyle(wb);
            return new Styles(neutral, neutral, neutral, neutral, neutral);
        }
    }

    // ==========================================================
    // 🎨 Estilos principais
    // ==========================================================
    private static CellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        try {
            XSSFFont font = wb.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            addBorders(style);
        } catch (Exception e) {
            log.warn("⚠️ Falha ao criar estilo de cabeçalho, usando fallback simples: {}", e.getMessage());
            return createFallbackStyle(wb);
        }
        return style;
    }

    private static CellStyle createColorStyle(XSSFWorkbook wb, IndexedColors color, boolean bold) {
        XSSFCellStyle style = wb.createCellStyle();
        try {
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            addBorders(style);
            if (bold) {
                XSSFFont font = wb.createFont();
                font.setBold(true);
                style.setFont(font);
            }
        } catch (Exception e) {
            log.warn("⚠️ Falha ao criar estilo colorido {}, aplicando fallback: {}", color, e.getMessage());
            return createFallbackStyle(wb);
        }
        return style;
    }

    // ==========================================================
    // 🧱 Fallbacks e utilitários
    // ==========================================================
    private static CellStyle createFallbackStyle(XSSFWorkbook wb) {
        try {
            XSSFCellStyle fallback = wb.createCellStyle();
            addBorders(fallback);
            return fallback;
        } catch (Exception ex) {
            log.error("❌ Falha ao criar estilo fallback: {}", ex.getMessage());
            return null; // último recurso
        }
    }

    private static void addBorders(CellStyle style) {
        if (style == null) return;
        try {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        } catch (Exception e) {
            log.debug("🔹 Falha leve ao aplicar bordas: {}", e.getMessage());
        }
    }

    /**
     * Aplica um estilo com segurança (não lança exceções se for nulo).
     */
    public static void safeApplyStyle(Cell cell, CellStyle style) {
        try {
            if (cell != null && style != null) cell.setCellStyle(style);
        } catch (Exception e) {
            log.debug("⚠️ Falha ao aplicar estilo: {}", e.getMessage());
        }
    }
}
