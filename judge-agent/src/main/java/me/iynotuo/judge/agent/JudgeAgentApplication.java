package me.iynotuo.judge.agent;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Remote judge agent. It intentionally has no web server, database or security filter chain: its only
 * outbound interface is the internal judge API of the main server.
 */
@SpringBootApplication(scanBasePackages = {
        "me.iynotuo.judge.core",
        "me.iynotuo.judge.agent"
})
@ConfigurationPropertiesScan(basePackages = {
        "me.iynotuo.judge.agent",
        "me.iynotuo.judge.core.config"
})
@EnableScheduling
public class JudgeAgentApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(JudgeAgentApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
