package com.example.springai.controller;

import com.example.springai.entity.ActorFilms;
import jakarta.annotation.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
public class MyController {

    private final ChatClient clientClient;
    @Resource
    private ChatClient defaultChatClient;
    @Resource
    private ChatClient customChatClient;
    @Resource
    private ChatClient openAiChatClient;
    @Resource
    private ChatClient deepsekChatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    public MyController(ChatClient.Builder chatClientBuilder) {
        this.clientClient = chatClientBuilder.build();
    }

    @GetMapping("ai")
    String generation(String userInput) {
        return this.clientClient.prompt().user(userInput).call().content();
    }

    @GetMapping("ai1")
    String generation1(String userInput) {
        return defaultChatClient.prompt().user(userInput).call().content();
    }

    @GetMapping("ai2")
    String generation2(String userInput) {
        return customChatClient.prompt().user(userInput).call().content();
    }

    @GetMapping("ai3")
    String generation3(String userInput) {
        return openAiChatClient.prompt().user(userInput).call().content();
    }

    @GetMapping("ai4")
    String generation4(String userInput) {
        return deepsekChatClient.prompt().user(userInput).call().content();
    }

    @GetMapping("ai5")
    String generation5(String userInput) {
        return deepsekChatClient
                .prompt(userInput)
                .call()
                .content();
    }


    @GetMapping("ai6")
    ChatResponse generation6(String userInput) {
        return deepsekChatClient
                .prompt()
                .user("Tell me a joke")
                .call()
                .chatResponse();
    }

    @GetMapping("ai7")
    ActorFilms generation7(String userInput) {
        return deepsekChatClient.prompt()
                .user("Generate the filmography for a random actor.")
                .call()
                .entity(ActorFilms.class);
    }

    @GetMapping("ai8")
    List<ActorFilms> generation8(String userInput) {
        return deepsekChatClient.prompt()
                .user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
                .call()
                .entity(new ParameterizedTypeReference<List<ActorFilms>>() {
                });
    }


//    可靠性配置 EntityParamSpec

    @GetMapping("ai9")
    ActorFilms generation9(String userInput) {

        return deepsekChatClient.prompt()
                .user("Generate the filmography for a random actor.")
                .call()
                .entity(ActorFilms.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());

    }
// 流式响应


    @GetMapping(value = "/ai10", produces = "text/event-stream;charset=UTF-8")
    Flux<String> generation10(String userInput) {

        return deepsekChatClient.prompt()
                .user("Tell me a 故事,300 字的")
                .stream()
                .content();

    }

    @GetMapping(value = "/ai11", produces = "text/event-stream;charset=UTF-8")
    Flux<ChatResponse> generation11(String userInput) {

        return deepsekChatClient.prompt()
                .user("Tell me a 故事,300 字的")
                .stream()
                .chatResponse();

    }

    // 提示词模版
    @GetMapping("ai12")
    @Nullable
    String generation12(String userInput) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey("sk-cd973eb09fd44073b80b25c69765a397")
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();
        return ChatClient.create(chatModel).prompt()
                .user(u ->
                        u.text("Tell me a {adjective} joke")
                                .param("adjective", "cat")
                )
                .call()
                .content();
    }

    @GetMapping("ai13")
    @Nullable
    String generation13(String userInput) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey("sk-cd973eb09fd44073b80b25c69765a397")
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();
        String answer = ChatClient.create(chatModel).prompt()
                .user(u -> u
                        .text("Tell me the names of 5 movies whose soundtrack was composed by <composer>")
                        .param("composer", "John Williams"))
                .templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .call()
                .content();
        return answer;
    }
    // 消息元数据： ChatClient 支持为用户消息和系统消息添加元数据。元数据随消息传递，为下游处理提供附加信息；具体如何使用取决于模型适配器 和消息消费者。
    // 为用户消息添加元数据

    // 逐项添加元数据，使用 matedata 逐项添加，或一次传入一个 Map。
    @GetMapping("ai14")
    @Nullable
    String generation14(String userInput) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey("sk-cd973eb09fd44073b80b25c69765a397")
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();
        Prompt prompt =  Prompt.builder()
                .content("今天星期几？")

                .build();
        ChatResponse chatResponse =   chatModel.call(prompt);
        System.out.println(prompt.getUserMessage());
        System.out.println("chatResponse"+chatResponse);
        System.out.println(prompt.getUserMessage());
        var chatClient = ChatClient.create(chatModel);
        // 逐项添加元数据
        String response = chatClient.prompt()
                .user(u -> u.text("What's the weather like?")
                        .metadata("messageId", "msg-123")
                        .metadata("userId", "user-456")
                        .metadata("priority", "high"))
                .call()
                .content();

// 一次添加多项元数据
        Map<String, Object> userMetadata = Map.of(
                "messageId", "msg-123",
                "userId", "user-456",
                "timestamp", System.currentTimeMillis()
        );

        var resp = chatClient
                .prompt()
                .user(u -> u.text(userInput)
                        .metadata(userMetadata)

                )
                .advisors(a ->
                        a.advisors(
                                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                        SimpleLoggerAdvisor.builder().build()
                                )
                                .param(ChatMemory.CONVERSATION_ID, userMetadata.get("messageId"))
                )
                .call()
                .content();


        PromptTemplate promptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
            Tell me the names of 5 movies whose soundtrack was composed by <composer>.
            """)
                .build();
        String str=  promptTemplate.render(Map.of(
                "composer","cat"
        ));
        System.out.println("str"+str);
       Message message =  promptTemplate.createMessage(Map.of(
               "composer","cat"
       ));
        System.out.println("message = " + message);
        Prompt prompt1 =  promptTemplate.create(Map.of(
                "composer","cat"
        ));
        System.out.println("prompt1 = " + prompt1);
        return resp;
    }
    // 工具调用：除非明确禁用，ChatClient 会自动注册 ToolCallingAdvisor。即使当前请求没有设置静态配置工具，其他 Advisor 在运行时动态加入的工具也能被处理


    @GetMapping("ai15")
    List<ActorFilms> generation15(String name, String voice) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey("sk-cd973eb09fd44073b80b25c69765a397")
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();
        String userText = """
                Tell me about three famous pirates from the Golden Age of Piracy and why they did.
                Write at least a sentence for each pirate.
                """;

        Message userMessage = new UserMessage(userText);

        String systemText = """
                You are a helpful AI assistant that helps people find information.
                Your name is {name}
                You should reply to the user's request with your name and also in the style of a {voice}.
                """;

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));

        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

        List<Generation> response = chatModel.call(prompt).getResults();
        return deepsekChatClient.prompt()
                .user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
                .call()
                .entity(new ParameterizedTypeReference<List<ActorFilms>>() {
                });

    }
    @Value("classpath:/prompts/system-message.st")
    private org.springframework.core.io.Resource systemResource;
    @GetMapping("ai16")
    List<ActorFilms> generation16() {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey("sk-cd973eb09fd44073b80b25c69765a397")
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
        return deepsekChatClient.prompt()
                .system(systemPromptTemplate.render())
                .user("你是谁？")
                .call()
                .entity(new ParameterizedTypeReference<List<ActorFilms>>() {
                });

    }

    @GetMapping("ai17")
    ChatResponse generation17() {

        var imageResource = new ClassPathResource("multimodal.test.png");


       return chatClient.prompt().user(s->
                s.text("Explain what do you see in this picture?,使用中文")
                        .media(new Media(MimeTypeUtils.IMAGE_PNG, imageResource))
        ).call().chatResponse();

    }


}
