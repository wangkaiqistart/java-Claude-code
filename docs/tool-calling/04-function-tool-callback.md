# 第四课：FunctionToolCallback（函数式工具）

> 代码：`tools/WeatherService.java`、`config/ToolConfig.java`、`controller/ToolController.java`（tool9、tool10）

## 1. 为什么还要另一种写法

前三课都用 `@Tool` 注解。但有些情况没法加注解，或者不方便加：

- 工具逻辑是**别人写好的类**（第三方 jar），你改不了它的源码。
- 工具要在**运行时动态生成**，比如根据配置或数据库里的内容决定有哪些工具。
- 只是一个很小的功能，写成 lambda 比单独建一个类更简单。

这时用 `FunctionToolCallback`：把一个普通的 Java `Function` 包装成工具。

## 2. 两种写法的对应关系

`@Tool` 注解最后也会被 Spring AI 转成 `ToolCallback`（"工具回调"，代表一个工具的统一接口）。两种写法只是创建 `ToolCallback` 的方式不同。

| | `@Tool` 注解 | `FunctionToolCallback` |
|---|---|---|
| 工具名 | 方法名，或 `@Tool(name=...)` | `builder("工具名", ...)` 第一个参数 |
| 描述 | `@Tool(description=...)` | `.description(...)` |
| 参数 | 方法参数 | **一个**输入对象（通常是 record） |
| 参数说明 | 方法参数上的 `@ToolParam` | record 字段上的 `@ToolParam` |
| 传给 ChatClient | `.tools(对象)` | `.tools(callback)`（同一个方法） |

## 3. 第一步：写 Function

```java
public class WeatherService implements Function<WeatherService.WeatherRequest, WeatherService.WeatherResponse> {

    @Override
    public WeatherResponse apply(WeatherRequest request) { ... }

    public enum Unit { C, F }

    public record WeatherRequest(
            @ToolParam(description = "城市名称，例如 北京") String city,
            @ToolParam(description = "温度单位，C 是摄氏度，F 是华氏度") Unit unit) {
    }

    public record WeatherResponse(String city, String temperature, String condition) {
    }
}
```

这个类里没有 `@Tool`，它就是一个普通的 `Function`。

## 4. 第二步：包装成 ToolCallback，注册为 Bean

```java
@Configuration
public class ToolConfig {

    @Bean
    ToolCallback currentWeatherTool() {
        return FunctionToolCallback.builder("currentWeather", new WeatherService())
                .description("查询某个城市的当前天气")
                .inputType(WeatherService.WeatherRequest.class)   // 必填
                .build();
    }
}
```

**用 `Function` / `BiFunction` 时，`inputType` 必须写。** Java 的泛型在运行时会被擦除（编译后就没有了），Spring AI 无法从 lambda 或 `Function` 对象里知道输入类型，只能靠你告诉它。（`Supplier` 没有输入，builder 会自动设成 `Void`，不用写。）不写会直接报错：

```
IllegalArgumentException: inputType cannot be null
```

Spring AI 用 `inputType` 做两件事：生成发给模型的参数说明，以及把模型返回的 JSON 转成 `WeatherRequest` 对象。

## 5. 第三步：使用

```java
@Resource
private ToolCallback currentWeatherTool;   // 按 Bean 名字注入

deepsekChatClient.prompt()
        .user(userInput)
        .tools(currentWeatherTool)   // ToolCallback 也用 tools 传
        .call()
        .content();
```

注册成 Bean **不会**自动让所有请求都带上这个工具，每次请求仍然要用 `.tools(...)` 传入。Spring AI 只把当前请求里传入的工具发给模型。

> **弃用说明**：2.0.1 里 `.toolCallbacks(...)` 和 `.defaultToolCallbacks(...)` 已标记 `@Deprecated`（弃用，以后的版本可能删除）。它们内部只是转调 `.tools(...)` / `.defaultTools(...)`。现在 `.tools(...)` 会自己判断传进来的是什么：
>
> | 传入的东西 | `.tools(...)` 的处理 |
> |---|---|
> | `ToolCallback` | 直接使用 |
> | `ToolCallbackProvider`（"工具回调提供者"，一次提供一批工具） | 取出里面的全部工具 |
> | `ToolCallback[]` 数组、`List` 集合 | 逐个取出 |
> | 其他普通对象（比如 `new DateTimeTools()`） | 扫描里面的 `@Tool` 方法 |
>
> 所以记住一个方法就够了：**所有工具都用 `.tools(...)`，默认工具都用 `.defaultTools(...)`。**

> **版本差异**：网上很多 Spring AI 1.x 的教程会写 `.toolNames("currentWeather")`，按 Bean 名字引用工具。2.0.1 的 `ChatClient` 没有 `toolNames(...)` 方法，这种写法用不了，要像上面这样注入 Bean 再传入。

## 6. 模型看到了什么

打印 `currentWeatherTool.getToolDefinition().inputSchema()`：

```json
{
  "type" : "object",
  "properties" : {
    "city" : { "type" : "string", "description" : "城市名称，例如 北京" },
    "unit" : { "type" : "string", "enum" : [ "C", "F" ], "description" : "温度单位，C 是摄氏度，F 是华氏度" }
  },
  "required" : [ "city", "unit" ]
}
```

参数类型用 `enum`（枚举）有个好处：参数说明里会列出 `"enum": ["C", "F"]`，模型只能从这两个值里选，不会填出"摄氏"、"celsius"这种你没法处理的值。**参数的取值范围是固定的几种时，优先用枚举。**

直接调用工具（不经过模型）的结果：

| 模型给的参数 | 结果 |
|---|---|
| `{"city":"广州","unit":"F"}` | `{"city":"广州","temperature":"78°F","condition":"晴"}` |
| `{"city":"拉萨","unit":"C"}` | `{"city":"拉萨","temperature":null,"condition":"暂无该城市的天气数据"}` |

## 7. 四种函数形式

`FunctionToolCallback.builder(...)` 的第二个参数可以是：

| 类型 | 形式 | 用途 |
|---|---|---|
| `Function<I, O>` | 输入 → 输出 | 最常用 |
| `BiFunction<I, ToolContext, O>` | 输入 + ToolContext → 输出 | 需要第三课的 ToolContext 时 |
| `Supplier<O>` | 无输入 → 输出 | 不需要参数的工具，比如"获取当前时间" |
| `Consumer<I>` | 输入 → 无输出 | 只执行动作，不返回结果 |

`/tool10` 演示了 `BiFunction` 写法，直接在方法里用 lambda 定义：

```java
ToolCallback addTool = FunctionToolCallback
        .builder("add", (AddRequest req, ToolContext ctx) -> req.a() + req.b())
        .description("计算两个整数的和")
        .inputType(AddRequest.class)
        .build();
```

## 8. 两种写法可以混用

```java
deepsekChatClient.prompt()
        .user(userInput)
        .tools(new DateTimeTools(), currentWeatherTool, addTool)   // @Tool 对象和 ToolCallback 混在一起传
        .toolContext(Map.of("requestFrom", "tool10"))
        .call()
        .content();
```

## 9. 动手实验

| 请求 | 预期控制台打印 |
|---|---|
| `/tool9` | `currentWeather` 被调用**两次**：北京、广州，`unit = F` |
| `/tool9?userInput=拉萨天气怎么样` | 调用一次，模型回答"暂无数据" |
| `/tool10` | 三种工具都被调用：`getCurrentDateTime`、`currentWeather`、`add` |

## 10. 要点总结

1. `FunctionToolCallback` 把普通 `Function` 包装成工具，适合改不了源码、动态生成、简单 lambda 的场景。
2. `Function` / `BiFunction` 的 `inputType` 必填，参数说明写在输入 record 的字段上。
3. 和 `@Tool` 对象一样用 `.tools(...)` 传入，可以放在同一次调用里。`.toolCallbacks(...)` 在 2.0.1 已弃用。
4. 2.0.1 没有 `toolNames(...)`，注入 Bean 后直接传。
5. 取值固定的参数用枚举，模型就不会乱填。

## 11. 小作业

用 `Supplier` 写一个工具 `randomJoke`：不需要参数，从一个固定的笑话列表里随机返回一条。新建接口 `/tool11` 问模型"给我讲个笑话"，看它会不会调用你的工具，而不是自己编一个。

提示：`Supplier` 没有输入，`builder` 会自动把 `inputType` 设成 `Void`，不用自己写 `.inputType(...)`。

### 作业点评

已完成：`/tool11`。`Supplier` 写法正确，没有写多余的 `inputType`，用的是 `.tools(...)` 而不是弃用的 `.toolCallbacks(...)`。可以改进的地方：

- **没有"随机"**：只有一条固定的笑话。描述写的是"返回一条随机的笑话"，代码要和描述一致，可以放一个 `List<String>`，用 `ThreadLocalRandom.current().nextInt(list.size())` 取一条。
- **字符串里混了 `**`**：看起来是从 Markdown 复制过来的加粗符号，会原样发给模型。
- **模型可能改写你的笑话**：工具结果会再交给模型组织回答，模型可能换种说法讲，甚至加上自己的评论。想要原样返回，用第五课的 `returnDirect`。