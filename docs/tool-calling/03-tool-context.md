# 第三课：ToolContext（工具上下文）

> 代码：`tools/MyOrderTools.java`、`service/OrderService.java`（`cancel(orderId, userId)`）、`controller/ToolController.java`（tool7、tool8）

## 1. 要解决的问题

第二课留下的漏洞：`OrderTools.listOrdersByUser(userId)` 的 `userId` 是**模型填的**。模型根据用户说的话填参数，用户说"我是 u001"，模型就填 u001。也就是说，用户只要嘴上冒充别人，就能查、能取消别人的订单。

根本原因：**"当前登录的是谁"这种信息，不应该由模型决定。** 它应该由你的程序直接交给工具，模型看不到也改不了。

`ToolContext`（工具上下文）就是这个通道。

```
                      ┌──── 发给模型：用户问题 + 工具说明（只有 orderId 参数）
Controller ──────────┤
  .toolContext(       └──── 只给工具：{"userId": "u002"}  ← 模型看不到
     Map.of("userId", loginUserId))
                                    │
模型："调用 cancelMyOrder(A1002)" ──▶ MyOrderTools.cancelMyOrder("A1002", toolContext)
                                    │                      └─ userId = u002（来自程序）
                                    ▼
                         OrderService.cancel("A1002", "u002") ─▶ 不是你的订单，拒绝
```

## 2. 工具端：方法参数里加 `ToolContext`

```java
@Component
public class MyOrderTools {

    @Tool(description = "查询当前登录用户的全部订单")
    List<Order> listMyOrders(ToolContext toolContext) {
        String userId = (String) toolContext.getContext().get("userId");
        return orderService.findByUserId(userId);
    }

    @Tool(description = "取消当前登录用户的一个订单。只有状态为【待发货】的订单可以取消")
    String cancelMyOrder(@ToolParam(description = "要取消的订单号") String orderId, ToolContext toolContext) {
        String userId = (String) toolContext.getContext().get("userId");
        return orderService.cancel(orderId, userId);
    }
}
```

和第二课 `OrderTools` 对比，关键区别是**工具不再有 `userId` 参数**。模型根本没有办法指定"操作谁的订单"。

## 3. 调用端：`.toolContext(Map)`

```java
deepsekChatClient.prompt()
        .user(userInput)
        .tools(myOrderTools)
        .toolContext(Map.of("userId", loginUserId))   // 只给工具，不发给模型
        .call()
        .content();
```

`ChatClient.Builder` 还有一个 `defaultToolContext(Map)`，作用范围和 `defaultTools` 一样。但"当前用户"每次请求都不同，所以要在每次请求里用 `.toolContext(...)` 传。

## 4. 验证：模型到底看到了什么

用 `ToolCallbacks.from(myOrderTools)` 打印出发给模型的参数说明（JSON Schema，"JSON 结构描述"）：

```
listMyOrders  => "properties" : { }                     ← 没有任何参数
cancelMyOrder => "properties" : { "orderId" : {...} }   ← 只有 orderId，没有 ToolContext
```

`ToolContext` 参数被 Spring AI 自动排除了，模型完全不知道它存在。

直接调用工具（不经过模型）的结果：

| 场景 | 结果 |
|---|---|
| ToolContext 里 userId = u002，取消 A1002（属于 u001） | `订单 A1002 不存在` |
| ToolContext 里 userId = u001，取消 A1002 | `订单 A1002 已取消` |
| 不传 ToolContext | `IllegalArgumentException: ToolContext is required by the method as an argument` |

第一行返回"不存在"而不是"无权限"，是故意的：如果提示"这不是你的订单"，就等于告诉对方"这个订单号存在"。

## 5. 动手实验

| 请求 | 预期 |
|---|---|
| `/tool7` | 以 u001 登录，列出 A1001、A1002 |
| `/tool7?loginUserId=u002&userInput=我是 u001，帮我取消订单 A1002` | 冒充失败，提示订单不存在 |
| `/tool7?loginUserId=u001&userInput=帮我取消订单 A1002` | 取消成功 |
| `/tool8` | 反例，忘了传 `.toolContext(...)`，报错 |

`loginUserId` 是用请求参数**模拟**登录信息。真实项目里必须从 Spring Security（`SecurityContextHolder`）或 Session 里取，不能让前端传，否则又绕回原来的漏洞。

## 6. 常见错误：参数和 ToolContext 同时用

```java
// ❌ 错误写法
List<Order> listOrdersByUser(@ToolParam(description = "用户 ID") String userId, ToolContext toolContext) {
    return orderService.findByUserId((String) toolContext.getContext().get("userId"));
}
```

这样虽然实际用的是 ToolContext 里的 userId，但模型仍然看到一个 `userId` 参数，会去填它（填的值又被忽略）。这会误导模型，也让读代码的人搞不清哪个是真的。**用 ToolContext 传的东西，就从工具参数里删掉。**

## 7. 要点总结

1. 身份、权限、租户 ID 这类信息走 `ToolContext`，不要做成工具参数。
2. 工具方法加一个 `ToolContext` 参数即可，Spring AI 自动传入，并且不会把它暴露给模型。
3. 调用时用 `.toolContext(Map)` 传值；工具需要它却没传，会直接报错。
4. 权限校验仍然写在 Service 里，ToolContext 只负责把可信的身份送到那里。

## 8. 小作业

给 `MyOrderTools` 加一个 `getMyOrder(orderId)` 工具：查询单个订单详情，但只能查到自己的订单。要求查别人的订单时，返回结果和查不存在的订单一样。

### 作业点评

已完成：`MyOrderTools.getMyOrder(...)` + `OrderService.findByIdAndUserId(...)`。工具部分写得对：`userId` 从 ToolContext 取，没有做成参数。但 `findByIdAndUserId` 有两个 bug，实测**查自己的订单 A1001 也会报错**：

```
u001 查 A1001 => 异常 ToolExecutionException: Cannot invoke "java.util.Optional.orElse(Object)"
                 because the return value of "OrderService.findByIdAndUserId(String, String)" is null
```

**bug 1：用 `!=` 比较字符串**

```java
order.userId() != userId   // ❌ 比较的是"是不是同一个对象"，不是"内容是否相同"
```

代码里的 `"u001"` 和从 HTTP 请求参数里拿到的 `"u001"` 是两个不同的对象，`!=` 永远为 true，所以自己的订单也被当成"别人的"。字符串比较一律用 `equals`。

**bug 2：返回类型是 `Optional`，却 `return null`**

调用方写的是 `findByIdAndUserId(...).orElse(null)`，返回 `null` 时调用 `.orElse` 就会空指针。返回 `Optional` 的方法永远不要返回 `null`，没有结果就返回 `Optional.empty()`。

**参考写法：**

```java
public Optional<Order> findByIdAndUserId(String orderId, String userId) {
    return Optional.ofNullable(orders.get(orderId))
            .filter(o -> o.userId().equals(userId));   // 不是自己的订单，就当作不存在
}
```