package me.iynotuo.judge.config;

import me.iynotuo.judge.common.enums.Language;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * 判题相关配置。语言命令支持占位符：
 * <ul>
 *     <li>{@code {command}}：runCommand 展开后的完整命令，仅用于 runWrapper；</li>
 *     <li>{@code {memoryMb}} / {@code {memoryKb}}：题目内存限制；</li>
 *     <li>{@code {timeMs}}：本题时间限制（已乘语言系数）。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "ioj.judge")
public class JudgeProperties {

    /**
     * 是否启用判题 worker。关闭后提交会一直停留在 PENDING，仅用于纯接口调试。
     */
    private boolean enabled = true;

    /**
     * 判题引擎实现，目前只提供 local（本机进程）。后续可扩展 docker。
     */
    private String engine = "local";

    /**
     * 同时评测的提交数。
     */
    private int concurrency = 1;

    /**
     * 拉取待评测提交的间隔（毫秒）。
     */
    private long pollIntervalMs = 1000;

    /**
     * 单个测试点允许的最大输出字节数，超出直接判为 Wrong Answer / Output Limit。
     */
    private int maxOutputBytes = 16 * 1024 * 1024;

    /**
     * 编译输出（stdout+stderr）保留的最大字节数。
     */
    private int maxCompileOutputBytes = 64 * 1024;

    /**
     * 编译超时（毫秒）。
     */
    private long compileTimeoutMs = 30_000;

    private Map<Language, LanguageConfig> languages = new EnumMap<>(Language.class);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxCompileOutputBytes() {
        return maxCompileOutputBytes;
    }

    public void setMaxCompileOutputBytes(int maxCompileOutputBytes) {
        this.maxCompileOutputBytes = maxCompileOutputBytes;
    }

    public long getCompileTimeoutMs() {
        return compileTimeoutMs;
    }

    public void setCompileTimeoutMs(long compileTimeoutMs) {
        this.compileTimeoutMs = compileTimeoutMs;
    }

    public Map<Language, LanguageConfig> getLanguages() {
        return languages;
    }

    public void setLanguages(Map<Language, LanguageConfig> languages) {
        this.languages = languages;
    }

    /**
     * 单语言配置。
     */
    public static class LanguageConfig {

        private String displayName;

        private String sourceFile;

        /**
         * 编译命令，留空表示解释型语言无需编译。
         */
        private String compileCommand;

        /**
         * 运行命令，可包含 {memoryMb} / {memoryKb} 占位符。
         */
        private String runCommand;

        /**
         * 运行包装脚本，{command} 会被 runCommand 替换。默认直接 exec。
         */
        private String runWrapper = "exec {command}";

        /**
         * 时间放大系数，用于抵消 JVM 启动等固定开销。
         */
        private double timeMultiplier = 1.0;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getCompileCommand() {
            return compileCommand;
        }

        public void setCompileCommand(String compileCommand) {
            this.compileCommand = compileCommand;
        }

        public String getRunCommand() {
            return runCommand;
        }

        public void setRunCommand(String runCommand) {
            this.runCommand = runCommand;
        }

        public String getRunWrapper() {
            return runWrapper;
        }

        public void setRunWrapper(String runWrapper) {
            this.runWrapper = runWrapper;
        }

        public double getTimeMultiplier() {
            return timeMultiplier;
        }

        public void setTimeMultiplier(double timeMultiplier) {
            this.timeMultiplier = timeMultiplier;
        }
    }
}
