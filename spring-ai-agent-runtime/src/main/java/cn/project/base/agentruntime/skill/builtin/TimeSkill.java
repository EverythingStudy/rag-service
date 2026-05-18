package cn.project.base.agentruntime.skill.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间日期技能 —— 查询当前时间、日期、时区信息。
 */
@Service
public class TimeSkill {

    @Tool(description = "获取当前日期和时间")
    public String getCurrentTime(
            @ToolParam(description = "时区，例如 Asia/Shanghai、America/New_York，不传则使用系统默认") String timezone) {
        ZonedDateTime now = timezone != null && !timezone.isBlank()
                ? ZonedDateTime.now(ZoneId.of(timezone))
                : ZonedDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEE)"));
    }

    @Tool(description = "获取当前日期（不含时间）")
    public String getCurrentDate(
            @ToolParam(description = "时区，例如 Asia/Shanghai，不传则使用系统默认") String timezone) {
        LocalDate today = timezone != null && !timezone.isBlank()
                ? LocalDate.now(ZoneId.of(timezone))
                : LocalDate.now();
        return today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE)"));
    }

    @Tool(description = "计算两个日期之间的天数差")
    public long daysBetween(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd") String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return java.time.temporal.ChronoUnit.DAYS.between(start, end);
    }
}
