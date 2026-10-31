package com.example.springai.tools;

import com.example.springai.entity.Order;
import com.example.springai.service.OrderService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 第三课：用 ToolContext 拿到"当前登录用户"。
 * 和 OrderTools 的区别：这里的工具没有 userId 参数，模型无法指定要操作谁的订单。
 */
@Component
public class MyOrderTools {

    private final OrderService orderService;

    public MyOrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    // ToolContext 参数不会出现在发给模型的参数说明里，由 Spring AI 在执行时自动传入
    @Tool(description = "查询当前登录用户的全部订单")
    List<Order> listMyOrders(ToolContext toolContext) {
        String userId = currentUserId(toolContext);
        System.out.println(">>> 模型调用了工具 listMyOrders，ToolContext 中的 userId = " + userId);
        return orderService.findByUserId(userId);
    }

    @Tool(description = "取消当前登录用户的一个订单。只有状态为【待发货】的订单可以取消")
    String cancelMyOrder(@ToolParam(description = "要取消的订单号") String orderId, ToolContext toolContext) {
        String userId = currentUserId(toolContext);
        System.out.println(">>> 模型调用了工具 cancelMyOrder，orderId = " + orderId + "，ToolContext 中的 userId = " + userId);
        return orderService.cancel(orderId, userId);
    }
    // 返回值是对象：Spring AI 会自动把它转成 JSON 发给模型
    @Tool(description = "根据订单号查询自己的订单详情，包括商品、金额和状态。查不到时返回 null")
    Order getMyOrder(@ToolParam(description = "订单号，例如 A1001") String orderId, ToolContext toolContext) {
        String userId = currentUserId(toolContext);

        System.out.println(">>> 模型调用了工具 getMyOrder，orderId = " + orderId);
        return orderService.findByIdAndUserId(orderId,userId).orElse(null);
    }


    private String currentUserId(ToolContext toolContext) {
        return (String) toolContext.getContext().get("userId");
    }
}