package me.iynotuo.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 判题线程池。与 Web 请求线程隔离，避免长耗时判题占满 Tomcat 线程。
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
