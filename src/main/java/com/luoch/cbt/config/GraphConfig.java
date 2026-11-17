package com.luoch.cbt.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luoch.cbt.graph.CbtGraphFactory;
import com.luoch.cbt.graph.LlmClient;
import com.luoch.cbt.graph.Nodes;
import com.luoch.cbt.mapper.SessionRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class GraphConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphConfig.class);

    /**
     * Prints the LLM settings that actually took effect. Spring binds
     * spring.ai.openai.* silently, so a typo in AI_BASE_URL would otherwise only
     * surface as a strange 404 on the first question.
     */
    @Bean
    public LlmClient llmClient(ChatModel chatModel,
                               @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
                               @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
                               @Value("${spring.ai.openai.api-key:}") String apiKey) {
        log.info("LLM ready  base-url={}  model={}  key={}", baseUrl, model, mask(apiKey));
        return new SpringAiLlmClient(chatModel);
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || "sk-not-configured".equals(apiKey)) {
            return "NOT SET";
        }
        return "set(" + apiKey.length() + " chars)";
    }

    @Bean
    public CompiledGraph cbtCompiledGraph(DataSource dataSource,
                                          LlmClient llm,
                                          ObjectMapper mapper,
                                          SessionRecordMapper records) throws Exception {
        MysqlSaver saver = MysqlSaver.builder()
                .dataSource(dataSource)
                .stateSerializer(new SpringAIStateSerializer())
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();

        CompileConfig config = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                .interruptBefore(Nodes.AWAIT_USER)
                .build();

        return CbtGraphFactory.build(llm, mapper, records).compile(config);
    }
}
