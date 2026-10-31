package com.example.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 第一课：用 @Tool 注解把普通 Java 方法变成模型可调用的工具
 */
public class DateTimeTools {

    // description 很重要：模型靠它判断"什么时候该用这个工具"
    @Tool(description = "获取用户所在时区的当前日期和时间")
    String getCurrentDateTime() {
        System.out.println(">>> 模型调用了工具 getCurrentDateTime");
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toString();
    }

    // 带参数的工具：@ToolParam 告诉模型这个参数是什么意思、该怎么填
    @Tool(description = "为用户设置一个闹钟，时间使用 ISO-8601 格式")
    String setAlarm(@ToolParam(description = "闹钟时间，ISO-8601 格式，例如 2026-10-31T18:00:00") String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println(">>> 模型调用了工具 setAlarm，参数 time = " + alarmTime);
        return "闹钟已设置在 " + alarmTime;
    }
    // 计算两个日期相差几天
    @Tool(name = "daysBetween", description = "计算两个日期相差几天")
    long daysBetween(@ToolParam(description = "开始日期，ISO-8601 格式，例如 2026-10-31T18:00:00")String startTime,@ToolParam(description = "结束日期，ISO-8601 格式，例如 2026-10-31T18:00:00")String endTime){
        LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime end = LocalDateTime.parse(endTime, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println(">>> 模型调用了工具 计算两个日期相差几天，参数 start = " + start);
        return ChronoUnit.DAYS.between(start, end);
    }
}