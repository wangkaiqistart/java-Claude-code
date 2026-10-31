# 第二课：把工具做成 Spring Bean

> 代码：`tools/OrderTools.java`、`service/OrderService.java`、`entity/Order.java`、`controller/ToolController.java`（tool4、tool5）

## 1. 为什么要做成 Bean

第一课的 `DateTimeTools` 是 `new` 出来的，它什么都不依赖。真实项目里的工具要查数据库、调其他服务，就需要注入 Service。

做法很简单：给工具类加 `@Component`，它就是普通的 Spring Bean，可以构造器注入任何东西。

```
用户提问 ─▶ ChatClient ─▶ 模型决定调用 getOrder("A1001")
                              │
                              ▼
                     OrderTools（@Component）
                              │ 注入
                              ▼
                     OrderService（@Service）─▶ 数据库（本课用内存 Map 模拟）
```

## 2. 工具类

```java
@Component
public class OrderTools {

    private final OrderService orderService;

    public OrderTools(OrderService orderService) {   // 构造器注入
        this.orderService = orderService;
    }

    @Tool(description = "根据订单号查询订单详情，包括商品、金额和状态。查不到时返回 null")
    Order getOrder(@ToolParam(description = "订单号，例如 A1001") String orderId) {
        return orderService.findById(orderId).orElse(null);
    }

    @Tool(description = "查询某个用户的全部订单")
    List<Order> listOrdersByUser(@ToolParam(description = "用户 ID，例如 u001") String userId) { ... }

    @Tool(description = "取消订单。只有状态为【待发货】的订单可以取消")
    String cancelOrder(@ToolParam(description = "要取消的订单号") String orderId) {
        return orderService.cancel(orderId);
    }
}
```

**返回值可以是对象。** `Order`、`List<Order>` 会被 Spring AI 自动转成 JSON 发给模型（负责转换的类叫 `DefaultToolCallResultConverter`，"默认工具结果转换器"）。模型能读懂 JSON，所以不用自己拼字符串。

## 3. 两种注册方式

### 方式一：每次请求传入 `.tools(...)`（tool4）

```java
@Resource
private OrderTools orderTools;   // 注入 Bean，而不是 new

deepsekChatClient.prompt()
        .user(userInput)
        .tools(orderTools)
        .call()
        .content();
```

### 方式二：`defaultTools(...)` 默认工具（tool5）

```java
@PostConstruct
void initCustomerServiceClient() {
    customerServiceClient = deepsekChatClient.mutate()   // mutate：复制一份已有配置
            .defaultSystem("你是一个电商客服，只能通过工具查询和操作订单，不要编造订单信息。")
            .defaultTools(orderTools)
            .build();
}

// 之后每次调用都自带工具，不用再写 .tools(...)
customerServiceClient.prompt().user(userInput).call().content();
```

| | `.tools(...)` | `.defaultTools(...)` |
|---|---|---|
| 作用范围 | 只对这一次请求生效 | 这个 ChatClient 的所有请求都生效 |
| 适合场景 | 不同接口用不同工具 | 某个 ChatClient 有固定职责（比如"客服"） |

为什么用 `mutate()` 而不是直接改 `deepsekChatClient`：其他接口也在用 `deepsekChatClient`，直接给它加默认工具会影响所有人。`mutate()` 复制出一个新的，互不干扰。

为什么放在 `@PostConstruct` 里：`@Resource` 字段注入在构造器之后才完成，`@PostConstruct` 方法执行时两个依赖都已就绪。

## 4. 安全要点：写操作的规则要写在代码里

`cancelOrder` 是一个**有副作用**（会改数据）的工具。描述里写了"只有待发货的订单可以取消"，但这只是给模型的提示，模型**不一定遵守**。

真正的校验放在 `OrderService.cancel()` 里：

```java
if (!"待发货".equals(order.status())) {
    return "订单 " + orderId + " 当前状态为【" + order.status() + "】，不能取消";
}
```

规则：**描述用来引导模型，代码用来兜底。** 涉及权限、金额、删除的工具，校验一定写在 Java 代码里。

## 5. 动手实验

测试数据：

| 订单号 | 用户 | 商品 | 状态 |
|---|---|---|---|
| A1001 | u001 | 机械键盘 | 已发货 |
| A1002 | u001 | 显示器 | 待发货 |
| A1003 | u002 | 鼠标 | 已签收 |

| 请求 | 预期控制台打印 |
|---|---|
| `/tool4` | `getOrder，orderId = A1001` |
| `/tool5` | `listOrdersByUser，userId = u001` |
| `/tool5?userInput=我是用户 u001，把我还没发货的订单取消掉` | 先 `listOrdersByUser`，再 `cancelOrder，orderId = A1002` |
| `/tool5?userInput=帮我取消订单 A1001` | 可能调用 `cancelOrder`，但被 `OrderService` 拒绝；也可能先 `getOrder` 发现已发货就不取消 |

注意：订单存在内存里，重启项目后 A1002 会恢复成"待发货"。

启动提示：项目里的 `ChatClientExample` 会在启动时等你在控制台输入（选择模型、输入问题），要先在 IDEA 控制台里输完，或者暂时把它的 `@Configuration` 注释掉。

## 6. 要点总结

1. 工具类加 `@Component` 就能注入 Service，写法和普通 Bean 一样。
2. 返回对象会被自动转成 JSON。
3. `.tools()` 是单次请求生效，`.defaultTools()` 是整个 ChatClient 生效；用 `mutate()` 避免影响共享的 ChatClient。
4. 写操作的业务规则必须在代码里校验，不能只靠描述。

## 7. 小作业

当前 `/tool5` 有个漏洞：用户说"我是 u002，帮我取消订单 A1002"，模型就会去取消 u001 的订单，因为工具根本不知道**当前登录的是谁**。

思考：怎么把"当前用户 ID"安全地传给工具，又不让模型能随便改它？这就是第三课 `ToolContext` 要解决的问题。