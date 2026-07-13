package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import org.lixiyun.common.agent.skill.tools.Tools;
import org.lixiyun.server.ai.hook.AgentHook.MessageProcessedAgentHook;
import org.lixiyun.server.ai.hook.ModelHook.MessageProcessedModelHook;
import org.lixiyun.server.ai.interceptor.ToolInterceptor.MessageToolInterceptor;
import org.lixiyun.server.ai.tool.FreightServiceTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class OllamaModelConfig {

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel chatModel;

    @Autowired
    private ChatMemory chatMemoryRepositoryWithMySQL;

    @Autowired
    private Map<String, AgentHook> AGENT_HOOKS;

    @Autowired
    private Map<String, ModelHook> MODEL_HOOKS;

    @Autowired
    private Map<String, Tools> TOOLS;

    @Autowired
    private Map<String, ToolInterceptor> TOOL_INTERCEPTORS;

    @Autowired
    private DataSource dataSource;

    @Bean
    public ChatClient ollamaCommonChatClient() {
        return ChatClient.builder(chatModel)
//                .defaultSystem(PromptWord.FREIGHT_CUSTOMER_SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemoryRepositoryWithMySQL)
                                .build()
                )
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.2)   // 控制模型生成结果的随机性，取值范围[0,2], 0.0：完全确定性，输出最可能的结果（适合事实类任务
                        .topP(0.9)          // 选择累积概率达到 topP 的最小词集合, topP=0.9：只考虑累计概率和为 90% 的词
                        .maxTokens(1524)     // 限制模型生成的最大 token 数量
                        .frequencyPenalty(0.7)   // 控制模型重复生成相同词或短语的概率，取值范围[-2.0, 2.0] ,值越大，越倾向于避免重复内容
                        .presencePenalty(0.5)  // 避免已出现词
                        .build())
                .build();
    }

    @Bean("freight-customer-server")
    public ReactAgent ollamaReactAgent() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("freight-customer-server")
                .description("聊天货运客服")
//                .systemPrompt(PromptWord.FREIGHT_CUSTOMER_SYSTEM_PROMPT)
                .chatOptions(ChatOptions.builder()
                        .topK(40)   // 每次只看前 40 个最可能的 token
                        .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                        .frequencyPenalty(0.2)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                        .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
//                        .stopSequences()   // 指定触发模型停止生成的字符串序列
                        .temperature(0.4)  // 控制输出的随机性 / 创造性,越低越保守
                        .maxTokens(32)   // 限制模型单次生成的最大 token 数
                        .build()
                )
                .enableLogging(false)   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
                .hooks(AGENT_HOOKS.get(MessageProcessedAgentHook.NAME),
                        MODEL_HOOKS.get(MessageProcessedModelHook.NAME),
                        HumanInTheLoopHook.builder()
                                .approvalOn(FreightServiceTool.EXIST_DRIVER_AVAILABLE, ToolConfig.builder()
                                        .description("询问司机是否可以接单需要人工处理")
                                        .build())
                                .build())
                .interceptors(TOOL_INTERCEPTORS.get(MessageToolInterceptor.NAME))
                .methodTools(TOOLS.get(FreightServiceTool.NAME))
                .saver(MysqlSaver.builder()
                        .createOption(CreateOption.CREATE_NONE)
                        .dataSource(dataSource)
                        .build())
                .build();
    }

}
