package me.iynotuo.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Judge thread pool, isolated from web request threads so long-running judging cannot exhaust Tomcat threads.
 */
@Configuration
public class JudgeConfig {

    @Bean(name = "judgeExecutor", destroyMethod = "shutdown")
    public ExecutorService judgeExecutor(JudgeProperties properties) {
        int concurrency = Math.max(1, properties.getConcurrency());
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "judge-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
