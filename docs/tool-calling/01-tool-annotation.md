# 第一课：工具调用（Tool Calling）入门

> 版本：Spring Boot 4.1.2 + Spring AI 2.0.1
> 代码：`springai/src/main/java/com/example/springai/tools/DateTimeTools.java`、`controller/ToolController.java`

## 1. 工具调用是什么

大模型有两个先天不足：

- **不知道实时信息**：它不知道今天几号，也不知道现在的天气。
- **不能动手做事**：它不能查你的数据库，也不能发邮件。

工具调用（Tool Calling，也叫 Function Calling，"函数调用"）就是把你写的 Java 方法交给模型，让它在需要的时候请求调用。

**最关键的一点：工具不是模型执行的，是你的程序执行的。** 模型只会回复"请帮我调用某某方法，参数是某某"。

```
你的程序                                   大模型
   │ ① 用户问题 + 工具清单(名字/描述/参数)  ──▶ │
   │                                          │ 判断：要知道"明天"，得先查今天
   │ ◀── ② "请调用 getCurrentDateTime"          │
   │ ③ Spring AI 自动执行你的 Java 方法          │
   │ ④ 把方法返回值发回去                   ──▶ │
   │ ◀── ⑤ 最终回答："明天是 11 月 1 日"        │
```

第 ②③④ 步由 Spring AI 自动完成，负责这件事的是 `ToolCallingAdvisor`（Advisor 意思是"顾问/拦截器"）。除非明确禁用，`ChatClient` 会自动注册它。

## 2. 定义工具：`@Tool` 和 `@ToolParam`

```java
public class DateTimeTools {

    @Tool(description = "获取用户所在时区的当前日期和时间")
    String getCurrentDateTime() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toString();
    }

    @Tool(description = "为用户设置一个闹钟，时间使用 ISO-8601 格式")
    String setAlarm(@ToolParam(description = "闹钟时间，ISO-8601 格式，例如 2026-10-31T18:00:00") String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        return "闹钟已设置在 " + alarmTime;
    }
}
```

| 注解 / 属性 | 作用 |
|---|---|
| `@Tool` | 把方法标记为工具 |
| `@Tool(description = ...)` | **最重要**。模型看不到你的代码，只能靠描述决定要不要调用、什么时候调用 |
| `@Tool(name = ...)` | 工具名，不写就用方法名 |
| `@ToolParam(description = ...)` | 说明参数含义，模型按这个说明生成参数值 |

## 3. 使用工具：`.tools(...)`

```java
deepsekChatClient.prompt()
        .user("明天是几月几号？")
        .tools(new DateTimeTools())   // 只多了这一行
        .call()
        .content();
```

## 4. 动手实验

| 地址 | 作用 | 预期结果 |
|---|---|---|
| `localhost:8080/tool0` | 对照组，不给工具 | 模型说不知道今天几号，或者瞎猜一个日期 |
| `localhost:8080/tool1` | 给了工具 | 答案正确，控制台打印 `>>> 模型调用了工具 getCurrentDateTime` |
| `localhost:8080/tool2` | 设 10 分钟后的闹钟 | 控制台打印**两行**：模型先查当前时间，再算出时间调用 `setAlarm` |

`tool2` 能看到模型自己规划出"先查时间，再设闹钟"两步。

## 5. 要点总结

1. 工具由**你的程序**执行，模型只负责"决定调用哪个、参数填什么"。
2. `description` 写得好不好，直接决定模型会不会正确使用工具。
3. 一次请求里模型可以连续调用多个工具。

## 6. 小作业

在 `DateTimeTools` 里加一个工具："计算两个日期相差几天"。然后新建一个接口问模型"离 2027 年春节还有几天"，看它会不会调用你的工具。

### 作业点评

已完成：`DateTimeTools.cal(...)` + `/tool3`。逻辑正确，可以改进的一点：

- **工具名就是方法名 `cal`**，模型看到的名字也是 `cal`，含义不清楚。改成 `daysBetween`，或者写 `@Tool(name = "daysBetween", description = ...)`。名字和描述一样，都是给模型看的"说明书"。
- 这个问题模型要调用两次：先 `getCurrentDateTime` 拿到今天，再 `cal` 算天数。2027 年春节是 2 月 6 日，这个日期由模型自己的知识提供，不是工具查的。