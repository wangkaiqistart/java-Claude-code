package com.example.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 第五课：物流工具，演示 returnDirect 和工具抛异常
 */
@Component
public class ShippingTools {

    // 模拟的物流数据：订单号 -> 物流单号
    private static final Map<String, String> TRACKING_NUMBERS = Map.of(
            "A1001", "SF1001",
            "A1003", "YT1003"
    );

    // returnDirect = true：工具结果直接返回给调用方，不再交给模型加工
    // resultConverter：默认会把 String 转成 JSON（带引号），这里换成原样输出
    @Tool(description = "查询订单的物流单号", returnDirect = true, resultConverter = PlainTextResultConverter.class)
    String getTrackingNumber(@ToolParam(description = "订单号，例如 A1001") String orderId) {
        System.out.println(">>> 模型调用了工具 getTrackingNumber，orderId = " + orderId);
        String trackingNo = TRACKING_NUMBERS.get(orderId);
        if (trackingNo == null) {
            return "订单 " + orderId + " 还没有物流信息";
        }
        return "订单 " + orderId + " 的物流单号：" + trackingNo;
    }

    // 演示工具抛异常：物流单号不认识时抛出 IllegalArgumentException
    @Tool(description = "根据物流单号查询物流轨迹")
    String getShipmentTrace(@ToolParam(description = "物流单号，例如 SF1001") String trackingNo) {
        System.out.println(">>> 模型调用了工具 getShipmentTrace，trackingNo = " + trackingNo);
        return switch (trackingNo) {
            case "SF1001" -> "10-30 已揽收（深圳） -> 10-31 运输中（武汉） -> 预计 11-01 送达";
            case "YT1003" -> "10-28 已揽收（杭州） -> 10-29 已签收";
            default -> throw new IllegalArgumentException("物流单号 " + trackingNo + " 不存在");
        };
    }
}