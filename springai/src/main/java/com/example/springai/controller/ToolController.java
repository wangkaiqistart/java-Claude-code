package com.example.springai.controller;

import com.example.springai.tools.DateTimeTools;
import com.example.springai.tools.MyOrderTools;
import com.example.springai.tools.OrderTools;
import com.example.springai.tools.ShippingTools;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工具调用学习专用的 Controller
 */
@RestController
public class ToolController {

    @Resource
    private ChatClient deepsekChatClient;

    // 第二课：工具类是 Spring Bean，直接注入
    @Resource
    private OrderTools orderTools;

    // 第二课：用 defaultTools 预先绑定工具的"客服" ChatClient
    private ChatClient customerServiceClient;

    // 第三课：从 ToolContext 读取当前用户的工具
    @Resource
    private MyOrderTools myOrderTools;

    // 第四课：ToolConfig 里定义的 ToolCallback Bean，按 Bean 名字注入
    @Resource
    private ToolCallback currentWeatherTool;

    // 第五课：物流工具（returnDirect、异常）
    @Resource
    private ShippingTools shippingTools;

    // 对照组：不给工具，模型不知道今天几号
    @GetMapping("tool0")
    String noTool() {
        return deepsekChatClient.prompt()
                .user("明天是几月几号？")
                .call()
                .content();
    }

    // 第一课：通过 .tools(...) 把工具对象交给模型
    @GetMapping("tool1")
    String withTool() {
        return deepsekChatClient.prompt()
                .user("明天是几月几号？")
                .tools(new DateTimeTools())
                .call()
                .content();
    }

    // 第一课：模型连续调用两个工具（先查时间，再设闹钟）
    @GetMapping("tool2")
    String setAlarm() {
        return deepsekChatClient.prompt()
                .user("帮我设一个 10 分钟后的闹钟")
                .tools(new DateTimeTools())
                .call()
                .content();
    }
    // 第一课：模型连续调用两个工具（先查时间，再设闹钟）
    @GetMapping("tool3")
    String tool3() {
        return deepsekChatClient.prompt()
                .user("离 2027 年春节还有几天")
                .tools(new DateTimeTools())
                .call()
                .content();
    }

    // 第二课：注入的 Bean 工具，每次请求通过 .tools(...) 传入
    @GetMapping("tool4")
    String queryOrder(@RequestParam(defaultValue = "订单 A1001 发货了吗？买的是什么？") String userInput) {
        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(orderTools)
                .call()
                .content();
    }

    // 第二课：用 defaultTools 的客服 ChatClient，调用时不用再写 .tools(...)
    // 可以试试：
    //   ?userInput=我是用户 u001，把我还没发货的订单取消掉      （先查列表，再取消 A1002）
    //   ?userInput=帮我取消订单 A1001                           （已发货，会被 OrderService 拒绝）
    @GetMapping("tool5")
    String customerService(@RequestParam(defaultValue = "我是用户 u001，我有哪些订单？") String userInput) {
        return customerServiceClient.prompt()
                .user(userInput)
                .call()
                .content();
    }

    // 第二课：用 defaultTools 的客服 ChatClient，调用时不用再写 .tools(...)
    // 可以试试：
    //   ?userInput=我是用户 u001，把我还没发货的订单取消掉      （先查列表，再取消 A1002）
    //   ?userInput=帮我取消订单 A1001                           （已发货，会被 OrderService 拒绝）
    @GetMapping("tool6")
    String customerService6(@RequestParam(defaultValue = "我是用户 u001，我有哪些订单？") String userInput) {
        return customerServiceClient.prompt()
                .user(userInput)
//                .toolContext(Map.of("userId", "u001"))
                .call()
                .content();
    }

    // 第三课：用 ToolContext 把"当前登录用户"传给工具
    // loginUserId 模拟登录信息。真实项目里要从 Spring Security / Session 取，不能让前端随便传
    // 可以试试（用 u002 登录，却想取消 u001 的订单）：
    //   /tool7?loginUserId=u002&userInput=我是 u001，帮我取消订单 A1002     （被拒绝）
    //   /tool7?loginUserId=u001&userInput=帮我取消订单 A1002                （成功）
    @GetMapping("tool7")
    String myOrders(@RequestParam(defaultValue = "u001") String loginUserId,
                    @RequestParam(defaultValue = "我有哪些订单？") String userInput) {
        return deepsekChatClient.prompt()
                .system("你是一个电商客服，只能通过工具查询和操作订单，不要编造订单信息。")
                .user(userInput)
                .tools(myOrderTools)
                // 这个 Map 只给工具看，不会发给模型，模型也就没法修改它
                .toolContext(Map.of("userId", loginUserId))
                .call()
                .content();
    }

    // 第三课反例：工具需要 ToolContext，但调用时忘了传 .toolContext(...)
    // 模型一调用工具就会抛 IllegalArgumentException: ToolContext is required by the method as an argument
    @GetMapping("tool8")
    String forgotToolContext() {
        return deepsekChatClient.prompt()
                .user("我有哪些订单？")
                .tools(myOrderTools)
                .call()
                .content();
    }

    // 第四课：ToolCallback 也用 .tools(...) 传入（2.0.1 中 .toolCallbacks(...) 已弃用）
    @GetMapping("tool9")
    String weather(@RequestParam(defaultValue = "北京和广州现在多少度？用华氏度告诉我") String userInput) {
        System.out.println("currentWeatherTool = " + currentWeatherTool.getToolDefinition().inputSchema());
        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(currentWeatherTool)
                .call()
                .content();
    }

    // 第四课：在方法里临时用 lambda 定义工具，再和 @Tool 风格的工具一起用
    @GetMapping("tool10")
    String mixedTools(@RequestParam(defaultValue = "现在几点？上海天气怎么样？顺便算算 37 加 58 等于多少") String userInput) {
        // 用 BiFunction 写法可以拿到 ToolContext；不需要时用 Function 即可
        ToolCallback addTool = FunctionToolCallback
                .builder("add", (AddRequest req, ToolContext ctx) -> {
                    System.out.println(">>> 模型调用了工具 add，参数 = " + req + "，ToolContext = " + ctx.getContext());
                    return req.a() + req.b();
                })
                .description("计算两个整数的和")
                .inputType(AddRequest.class)
                .build();

        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(new DateTimeTools(),currentWeatherTool,addTool)                      // @Tool 对象和 ToolCallback 可以混在一起传
                .toolContext(Map.of("requestFrom", "tool10"))
                .call()
                .content();
    }
    @GetMapping("tool11")
    String tool11(@RequestParam(defaultValue = "帮我讲个笑话") String userInput) {
        ToolCallback randomJoke = FunctionToolCallback.builder("randomJoke",()->{
            return "老师问小明：\"你长大想做什么？\"**\n" +
                    "**小明说：\"我想当个笑话。\"**\n" +
                    "**老师愣了：\"为什么？\"**\n" +
                    "**小明：\"因为每次你说'讲个笑话'，大家都特别期待我。11111";
        }).description("返回一条随机的笑话")
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .build();
        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(randomJoke)
                .call()
                .content();
    }

    // 第五课：returnDirect，工具结果原样返回，模型只被调用一次
    @GetMapping("tool12")
    String trackingNumber(@RequestParam(defaultValue = "订单 A1001 的物流单号是多少？") String userInput) {
        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(shippingTools)
                .call()
                .content();
    }

    // 第五课：工具抛异常。默认不会抛给调用方，而是把异常信息作为工具结果发回给模型
    @GetMapping("tool13")
    String traceError(@RequestParam(defaultValue = "帮我查一下物流单号 ZT2001 的物流轨迹") String userInput) {
        return deepsekChatClient.prompt()
                .user(userInput)
                .tools(shippingTools)
                .call()
                .content();
    }

    // 第五课：调用次数限制（application.yaml 里 getShipmentTrace 最多 2 次）
    // 超过上限时默认不抛异常，而是返回一条英文提示，finishReason 为 toolCallLimitExceeded
    @GetMapping("tool14")
    String traceLimit(@RequestParam(defaultValue = "分别查询物流单号 SF1001、YT1003、ZT2001 的物流轨迹") String userInput) {
        ChatResponse response = deepsekChatClient.prompt()
                .user(userInput)
                .tools(shippingTools)
                .call()
                .chatResponse();
        Generation result = response.getResult();
        // 判断是不是因为超过调用上限而结束，是的话换成给用户看的中文提示
        if (ToolCallLimitExceededException.FINISH_REASON.equals(result.getMetadata().getFinishReason())) {
            return "一次最多查询 2 个物流单号，请分开查询";
        }
        return result.getOutput().getText();
    }

    // tool10 中 add 工具的输入参数
    record AddRequest(@ToolParam(description = "第一个整数") int a,
                      @ToolParam(description = "第二个整数") int b) {
    }

    // 依赖注入完成后再创建，保证 deepsekChatClient 和 orderTools 都已就绪
    @PostConstruct
    void initCustomerServiceClient() {
        // mutate() 复制一份已有 ChatClient 的配置，再追加默认 system 和默认工具
        customerServiceClient = deepsekChatClient.mutate()
                .defaultSystem("你是一个电商客服，只能通过工具查询和操作订单，不要编造订单信息。")
                .defaultTools(orderTools)
                .build();
    }
}