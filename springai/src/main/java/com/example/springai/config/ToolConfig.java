package com.example.springai.config;

import com.example.springai.tools.WeatherService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第四课：把 Function 包装成 ToolCallback，注册为 Spring Bean
 */
@Configuration
public class ToolConfig {

    @Bean
    ToolCallback currentWeatherTool() {
        // 第一个参数是工具名（模型看到的名字），第二个参数是真正执行的 Function
        return FunctionToolCallback.builder("currentWeather", new WeatherService())
                .description("查询某个城市的当前天气")
                // inputType 必填：Spring AI 靠它生成参数说明，并把模型给的 JSON 转成 WeatherRequest
                .inputType(WeatherService.WeatherRequest.class)
                .build();
    }
}