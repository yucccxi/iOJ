package me.iynotuo.judge.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Explicit Jackson mapper so the agent does not depend on web auto-configuration.
 */
@Configuration
public class AgentConfiguration {

    @Bean
    public JsonMapper agentJsonMapper() {
        return JsonMapper.builder().build();
    }
}
