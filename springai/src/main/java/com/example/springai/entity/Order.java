package com.example.springai.entity;

import java.math.BigDecimal;

/**
 * 订单实体（第二课用）
 */
public record Order(String orderId, String userId, String product, BigDecimal amount, String status) {

    public Order withStatus(String newStatus) {
        return new Order(orderId, userId, product, amount, newStatus);
    }
}