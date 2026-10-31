package com.example.springai.controller;

import com.example.springai.tools.DateTimeTools;
import com.example.springai.tools.OrderTools;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
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