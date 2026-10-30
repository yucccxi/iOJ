package me.iynotuo.judge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * 启动时做一次数据库连通性检查。
 *
 * <p>目的：把"环境变量没生效 / 账号密码错误"这类配置问题提前到启动阶段暴露，
 * 并打印**实际生效**的 JDBC URL 与用户名（不含密码），而不是等到第一个请求才抛
 * {@code CannotCreateTransactionException: Could not open JDBC Connection for transaction}。</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.datasource-check", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DataSourceHealthCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthCheck.class);

    private final DataSource dataSource;
    private final Environment environment;

    public DataSourceHealthCheck(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "(未配置)");
        String username = environment.getProperty("spring.datasource.username", "(未配置)");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            log.info("数据库连接正常: {} {}，当前库={}，账号={}",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion(),
                    connection.getCatalog(), meta.getUserName());
        } catch (SQLException ex) {
            log.error("""
                    数据库连接失败，应用无法启动。
                      实际使用的 JDBC URL: {}
                      实际使用的用户名  : {}
                      原始错误          : {}
                    请检查以下环境变量是否在**当前进程**中生效（注意不要带引号、空格或分号）：
                      IOJ_DB_HOST / IOJ_DB_PORT / IOJ_DB_NAME / IOJ_DB_USER / IOJ_DB_PASSWORD
                      bash 写法（每行一个 export，不要用分号写在一行）：
                        export IOJ_DB_USER=your_user
                        export IOJ_DB_PASSWORD='your_password'
                      IDEA 写法（Run Configuration → Environment variables，用分号分隔且等号两边不留空格）：
                        IOJ_DB_USER=your_user;IOJ_DB_PASSWORD=your_password;IOJ_DB_NAME=ioj
                    """, url, username, ex.getMessage());
            throw new IllegalStateException("数据库连接失败: " + ex.getMessage(), ex);
        }
    }
}
