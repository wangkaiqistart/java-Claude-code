package com.example.springai.service;

import com.example.springai.entity.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟的订单业务层，用内存 Map 代替数据库。
 * 真实项目里这里会注入 Repository / Mapper 去查库，工具类的写法完全一样。
 */
@Service
public class OrderService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public OrderService() {
        // 初始化几条测试数据
        save(new Order("A1001", "u001", "机械键盘", new BigDecimal("399.00"), "已发货"));
        save(new Order("A1002", "u001", "显示器", new BigDecimal("1299.00"), "待发货"));
        save(new Order("A1003", "u002", "鼠标", new BigDecimal("99.00"), "已签收"));
    }

    private void save(Order order) {
        orders.put(order.orderId(), order);
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    /**
     * 第三课作业：只查当前用户自己的订单。
     * 不是自己的订单和不存在的订单一样，都返回 Optional.empty()，避免泄露别人的订单信息
     */
    public Optional<Order> findByIdAndUserId(String orderId, String userId) {
        return Optional.ofNullable(orders.get(orderId))
                // 字符串比较内容要用 equals，!= 比较的是不是同一个对象
                .filter(o -> o.userId().equals(userId));
    }

    public List<Order> findByUserId(String userId) {
        return orders.values().stream()
                .filter(o -> o.userId().equals(userId))
                .toList();
    }

    /**
     * 取消订单。业务规则在这里校验，不依赖模型"自觉"：只有待发货的订单能取消。
     */
    public String cancel(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return "订单 " + orderId + " 不存在";
        }
        if (!"待发货".equals(order.status())) {
            return "订单 " + orderId + " 当前状态为【" + order.status() + "】，不能取消";
        }
        orders.put(orderId, order.withStatus("已取消"));
        return "订单 " + orderId + " 已取消";
    }

    /**
     * 第三课：带归属校验的取消。userId 来自登录信息（ToolContext），不是模型填的。
     */
    public String cancel(String orderId, String userId) {
        Order order = orders.get(orderId);
        // 不属于当前用户的订单，和"不存在"返回同样的提示，避免泄露别人的订单信息
        if (order == null || !order.userId().equals(userId)) {
            return "订单 " + orderId + " 不存在";
        }
        return cancel(orderId);
    }
}