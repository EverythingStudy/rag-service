package cn.project.base.mysqlmcpserver.service;

import java.util.List;
import java.util.Map;

/**
 * 查询结果格式化工具。
 * <p>
 * 将 JDBC/MyBatis 返回的 List&lt;Map&lt;String, Object&gt;&gt; 格式化为
 * 可读的 ASCII 表格，方便 AI 模型阅读和分析。
 */
public class ResultFormatter {

    private ResultFormatter() {}

    /**
     * 将查询结果格式化为 ASCII 表格。
     *
     * @param rows 查询结果集
     * @return 格式化后的表格字符串
     */
    public static String format(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "(空结果集)";
        }

        // 提取列名
        String[] columns = rows.get(0).keySet().toArray(new String[0]);

        // 计算每列的最大显示宽度
        int[] widths = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            widths[i] = columns[i].length();
        }
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.length; i++) {
                String val = (row.get(columns[i]) != null) ? row.get(columns[i]).toString() : "NULL";
                widths[i] = Math.max(widths[i], val.length());
            }
        }

        StringBuilder sb = new StringBuilder();

        // 分隔线
        String sep = buildSeparator(widths);
        sb.append(sep).append('\n');

        // 表头
        sb.append("| ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(padRight(columns[i], widths[i]));
        }
        sb.append(" |\n").append(sep).append('\n');

        // 数据行
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sb.append(" | ");
                String val = (row.get(columns[i]) != null) ? row.get(columns[i]).toString() : "NULL";
                sb.append(padRight(val, widths[i]));
            }
            sb.append(" |\n");
        }
        sb.append(sep);

        return sb.toString();
    }

    /** 构建表分隔线，如 +---+-------+ */
    private static String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+-");
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append("-+-");
            sb.append("-".repeat(widths[i]));
        }
        sb.append("-+");
        return sb.toString();
    }

    /** 右填充空格到指定宽度 */
    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }
}
