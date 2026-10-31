package com.example.springai.tools;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * 第五课：工具结果转换器。
 * 默认的 DefaultToolCallResultConverter 会把 String 也转成 JSON，结果两边多了引号。
 * 这里 String 原样返回，其他类型仍交给默认转换器转成 JSON。
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

    private final DefaultToolCallResultConverter delegate = new DefaultToolCallResultConverter();

    @Override
    public String convert(@Nullable Object result, @Nullable Type returnType) {
        if (result instanceof String text) {
            return text;
        }
        return delegate.convert(result, returnType);
    }
}