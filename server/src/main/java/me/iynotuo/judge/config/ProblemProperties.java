package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 题目相关配置。
 */
@ConfigurationProperties(prefix = "ioj.problem")
public class ProblemProperties {

    /**
     * 普通用户上传的题目是否直接发布。false 时进入 PENDING_REVIEW，等待管理员审核。
     * 学校俱乐部内部使用建议 true，开放注册时建议 false。
     */
    private boolean autoPublish = true;

    /**
     * 单题测试点数量上限。
     */
    private int maxTestcases = 100;

    /**
     * 单个测试点输入/输出各自的最大字节数。
     */
    private int maxTestcaseBytes = 1024 * 1024;

    public boolean isAutoPublish() {
        return autoPublish;
    }

    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish = autoPublish;
    }

    public int getMaxTestcases() {
        return maxTestcases;
    }

    public void setMaxTestcases(int maxTestcases) {
        this.maxTestcases = maxTestcases;
    }

    public int getMaxTestcaseBytes() {
        return maxTestcaseBytes;
    }

    public void setMaxTestcaseBytes(int maxTestcaseBytes) {
        this.maxTestcaseBytes = maxTestcaseBytes;
    }
}
