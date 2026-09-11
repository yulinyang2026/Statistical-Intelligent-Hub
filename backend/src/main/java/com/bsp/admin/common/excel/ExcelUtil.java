package com.bsp.admin.common.excel;

import com.bsp.admin.common.exception.BizException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel（.xlsx）导入导出工具 —— 基于 Apache POI。
 *
 * <p><b>2026-09-11 用户需求</b>：导入导出由 CSV 改为 **Excel（.xlsx）**，不再支持 CSV。
 * 统一走本类，模块 / 专题 / 项目三处共用。</p>
 */
public final class ExcelUtil {

    /** xlsx 的 MIME 类型 */
    public static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int MAX_COL_WIDTH = 12000;
    private static final int MIN_COL_WIDTH = 2400;

    private ExcelUtil() {
    }

    /**
     * 生成单工作表 xlsx。
     *
     * @param sheetName 工作表名（Excel 限制 31 字符，自动截断并过滤非法字符）
     * @param headers   表头（首行，加粗）
     * @param rows      数据行
     */
    public static byte[] write(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));

            CellStyle headStyle = workbook.createCellStyle();
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);

            List<String> head = headers == null ? List.of() : headers;
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < head.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(head.get(i) == null ? "" : head.get(i));
                cell.setCellStyle(headStyle);
            }

            int rowIndex = 1;
            for (List<String> data : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.size(); i++) {
                    String value = data.get(i);
                    row.createCell(i).setCellValue(value == null ? "" : value);
                }
            }

            // 列宽：按表头与各列内容的最大显示宽度估算（中文按 2 个字符宽）
            for (int i = 0; i < head.size(); i++) {
                int width = displayWidth(head.get(i));
                for (List<String> data : rows) {
                    if (i < data.size()) {
                        width = Math.max(width, displayWidth(data.get(i)));
                    }
                }
                sheet.setColumnWidth(i, Math.min(MAX_COL_WIDTH, Math.max(MIN_COL_WIDTH, (width + 4) * 256)));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException("生成 Excel 失败：" + e.getMessage());
        }
    }

    /**
     * 读取第一张工作表为「表头 → 单元格值」的行列表（首行为表头，整行皆空的跳过）。
     *
     * <p>取值为单元格的**显示值**（{@link DataFormatter}）：日期按 Excel 中设置的格式输出，
     * 避免用户直接在 Excel 里填日期时读成序列号。</p>
     */
    public static List<Map<String, String>> readAsMaps(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException("Excel 内容为空");
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            if (last < first) {
                return List.of();
            }

            List<String> headers = new ArrayList<>();
            Row headerRow = sheet.getRow(first);
            int headerCols = headerRow == null ? 0 : headerRow.getLastCellNum();
            for (int c = 0; c < headerCols; c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
            }

            List<Map<String, String>> result = new ArrayList<>();
            for (int r = first + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String key = headers.get(c);
                    if (key.isEmpty()) {
                        continue;
                    }
                    Cell cell = row.getCell(c);
                    String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
                    if (!value.isEmpty()) {
                        hasValue = true;
                    }
                    map.put(key, value);
                }
                if (hasValue) {
                    result.add(map);
                }
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Excel 解析失败（请确认是 .xlsx 格式，不支持老的 .xls）：" + e.getMessage());
        }
    }

    /** 工作表名合法化：Excel 限制 31 字符，且不允许 : \ / ? * [ ] */
    private static String safeSheetName(String name) {
        String s = name == null || name.isBlank() ? "Sheet1" : name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        if (s.isEmpty()) {
            s = "Sheet1";
        }
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    /** 显示宽度：中文/全角按 2，其余按 1 */
    private static int displayWidth(String value) {
        if (value == null) {
            return 0;
        }
        int width = 0;
        for (char c : value.toCharArray()) {
            width += c > 0x7F ? 2 : 1;
        }
        return width;
    }
}
