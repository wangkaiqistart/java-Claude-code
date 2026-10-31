# 第五课：returnDirect、异常处理、调用次数限制

> 代码：`tools/ShippingTools.java`、`tools/PlainTextResultConverter.java`、`controller/ToolController.java`（tool12 ~ tool14）、`application.yaml`（`spring.ai.tools`）
>
> 本课结论都用"假模型"验证过：写一个按脚本返回"调用工具"的 `ChatModel`，用和 `ChatClientConfig2` 相同的方式构建 ChatClient，不花 API 额度。

## 1. returnDirect：工具结果直接返回

正常流程里，工具结果要再发给模型，由模型组织成最终回答（第一课流程图的第 ④⑤ 步）。`returnDirect = true` 跳过这一步，工具返回什么，调用方就拿到什么。

```java
@Tool(description = "查询订单的物流单号", returnDirect = true, resultConverter = PlainTextResultConverter.class)
String getTrackingNumber(@ToolParam(description = "订单号，例如 A1001") String orderId) { ... }
```

验证结果：返回内容 `订单 A1001 的物流单号：SF1001`，**模型只被调用 1 次**（正常是 2 次）。

适合场景：结果要原样展示（单号、报表、生成的文件链接），不希望模型改写；或者想省一次模型调用的时间和费用。

### 为什么要 `resultConverter`

工具返回值默认由 `DefaultToolCallResultConverter` 转成 JSON，**`String` 也会被转**，结果两边多出引号：`"订单 A1001 的物流单号：SF1001"`。平时这些引号是给模型看的，无所谓；但 returnDirect 时会直接展示给用户。所以这里写了一个转换器，`String` 原样返回，其他类型仍然转 JSON：

```java
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
```

`FunctionToolCallback` 的写法：`.toolMetadata(ToolMetadata.builder().returnDirect(true).build())` 和 `.toolCallResultConverter(...)`。

## 2. 工具抛异常

```java
@Tool(description = "根据物流单号查询物流轨迹")
String getShipmentTrace(String trackingNo) {
    ...
    default -> throw new IllegalArgumentException("物流单号 " + trackingNo + " 不存在");
}
```

| 配置 `spring.ai.tools.throw-exception-on-error` | 行为 | 验证结果 |
|---|---|---|
| `false`（默认） | 异常信息作为工具结果**发回给模型**，模型自己组织回答 | 模型收到 `物流单号 XX999 不存在`，回答"抱歉，没查到"，模型共调用 2 次 |
| `true` | 异常**抛给调用方** | 调用方收到 `ToolExecutionException`，原因是 `IllegalArgumentException` |

默认行为的好处：模型看到错误后可以换个参数重试，或者向用户解释。

**安全提醒**：默认情况下，异常的 `getMessage()` 会原样发给模型，模型可能再转述给用户。不要让数据库报错、内部地址、密钥这类信息出现在异常信息里。

**例外**：第三课 `/tool8` 那种"工具需要 ToolContext 但没传"的错误，发生在工具执行之前，不走上面的处理，**总是直接抛给调用方**（已验证：`IllegalArgumentException: ToolContext is required by the method as an argument`）。

## 3. 调用次数限制

模型可能陷入死循环，反复调用同一个工具。Spring AI 2.0.1 默认就有上限：

| 配置 | 含义 | 默认值 |
|---|---|---|
| `spring.ai.tools.limits.max-calls-per-tool-default` | 每个工具在一轮对话里最多调用几次 | 40 |
| `spring.ai.tools.limits.max-calls-per-tool.<工具名>` | 单独给某个工具设上限 | 无 |
| `spring.ai.tools.limits.max-total-tool-calls` | 所有工具加起来最多调用几次 | 150 |
| `spring.ai.tools.limits.excluded-tools` | 不受单工具上限限制的工具 | 无 |
| `spring.ai.tools.limits.on-limit-exceeded` | 超过上限怎么办：`throw` / `return-error-response` | `throw` |

设成 `-1` 表示不限制。本课配置：

```yaml
spring:
  ai:
    tools:
      limits:
        max-calls-per-tool:
          getShipmentTrace: 2     # key 是工具名，驼峰写法会原样保留（已验证）
```

### 两种超限行为（实测和名字给人的印象不一样）

| `on-limit-exceeded` | 实际行为 |
|---|---|
| `throw`（默认） | **调用方不会收到异常**。循环停止，返回一条英文内容 `Tool call limit (3) exceeded for tool 'getShipmentTrace'...`，`finishReason`（结束原因）是 `toolCallLimitExceeded` |
| `return-error-response` | 这条错误作为工具结果**发回给模型**，循环继续，由模型决定下一步 |

`throw` 的"抛出"发生在 Spring AI 内部，`ToolCallingAdvisor` 自己接住了，所以你的代码要检查 `finishReason`：

```java
Generation result = response.getResult();
if (ToolCallLimitExceededException.FINISH_REASON.equals(result.getMetadata().getFinishReason())) {
    return "一次最多查询 2 个物流单号，请分开查询";
}
```

`return-error-response` 要小心：假模型实验里，模型无视错误一直调用，超限的调用不再执行工具，但**模型本身被无限调用**，直到程序内存溢出。真实模型一般看到错误就会停，但不能保证。需要绝对的安全上限时，用默认的 `throw`。

一次回复里同时调用多次也会被拦住：假模型一次请求 5 次 `getShipmentTrace`（上限 3），只执行了 3 次。

## 4. 动手实验

| 请求 | 预期 |
|---|---|
| `/tool12` | 直接返回 `订单 A1001 的物流单号：SF1001`，没有引号，模型没有加工 |
| `/tool13` | ZT2001 不存在，模型收到异常信息后回答"没查到" |
| `/tool14` | 查 3 个单号，第 3 次超过上限，返回中文提示 |

## 5. 要点总结

1. `returnDirect = true`：工具结果直接返回，模型少调用一次；返回 `String` 时配合 `resultConverter` 去掉 JSON 引号。
2. 工具异常默认发回给模型；`throw-exception-on-error: true` 改成抛给调用方。异常信息里不要放敏感内容。
3. 默认上限：每个工具 40 次、总共 150 次。超限默认不抛异常，要检查 `finishReason`。
4. 慎用 `return-error-response`，它依赖模型自己停下来。

## 6. 小作业

把第四课作业的 `randomJoke` 改成 returnDirect，让模型不再改写你的笑话。提示：`FunctionToolCallback` 用 `.toolMetadata(...)` 设置。