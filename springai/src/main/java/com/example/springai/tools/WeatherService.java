package com.example.springai.tools;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;
import java.util.function.Function;

/**
 * 第四课：用 java.util.function.Function 定义工具，类里没有任何 @Tool 注解。
 * 输入是 WeatherRequest，输出是 WeatherResponse，Spring AI 根据 WeatherRequest 生成参数说明。
 */
public class WeatherService implements Function<WeatherService.WeatherRequest, WeatherService.WeatherResponse> {

    // 模拟的天气数据，真实项目里这里会调用天气 API
    private static final Map<String, Integer> TEMPERATURES = Map.of(
            "北京", 12,
            "上海", 18,
            "广州", 26
    );

    @Override
    public WeatherResponse apply(WeatherRequest request) {
        System.out.println(">>> 模型调用了工具 currentWeather，参数 = " + request);
        Integer celsius = TEMPERATURES.get(request.city());
        if (celsius == null) {
            return new WeatherResponse(request.city(), null, "暂无该城市的天气数据");
        }
        int temp = request.unit() == Unit.F ? celsius * 9 / 5 + 32 : celsius;
        return new WeatherResponse(request.city(), temp + "°" + request.unit(), "晴");
    }

    public enum Unit { C, F }

    // record 字段上的 @ToolParam 会进入参数说明，模型据此填参数
    public record WeatherRequest(
            @ToolParam(description = "城市名称，例如 北京") String city,
            @ToolParam(description = "温度单位，C 是摄氏度，F 是华氏度") Unit unit) {
    }

    public record WeatherResponse(String city, String temperature, String condition) {
    }
}