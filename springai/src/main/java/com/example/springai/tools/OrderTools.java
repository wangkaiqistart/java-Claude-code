package com.example.springai.tools;

import com.example.springai.entity.Order;
import com.example.springai.service.OrderService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 第二课：工具类本身是 Spring Bean，可以像普通 Bean 一样注入 Service
 */
@Component
public class OrderTools {

    private final OrderService orderService;

    // 构造器注入，和你平时写 Controller / Service 一样
    public OrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    // 返回值是对象：Spring AI 会自动把它转成 JSON 发给模型
    @Tool(description = "根据订单号查询订单详情，包括商品、金额和状态。查不到时返回 null")
    Order getOrder(@ToolParam(description = "订单号，例如 A1001") String orderId) {
        System.out.println(">>> 模型调用了工具 getOrder，orderId = " + orderId);
        return orderService.findById(orderId).orElse(null);
    }

    @Tool(description = "查询某个用户的全部订单")
//    List<Order> listOrdersByUser(@ToolParam(description = "用户 ID，例如 u001") String userId, ToolContext toolContext) {
    List<Order> listOrdersByUser(@ToolParam(description = "用户 ID，例如 u001") String userId) {
        System.out.println(">>> 模型调用了工具 listOrdersByUser，userId = " + userId);
//        Map<String,Object> map =  toolContext.getContext();

//        return orderService.findByUserId((String) map.get("userId"));

        return orderService.findByUserId(userId);
    }

    // 有副作用的"写"工具：能不能取消由 OrderService 校验，不能只靠描述约束模型
    @Tool(description = "取消订单。只有状态为【待发货】的订单可以取消")
    String cancelOrder(@ToolParam(description = "要取消的订单号") String orderId) {
        System.out.println(">>> 模型调用了工具 cancelOrder，orderId = " + orderId);
        return orderService.cancel(orderId);
    }
}