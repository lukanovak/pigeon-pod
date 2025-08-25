package top.asimov.pigeon.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Log4j2
@Configuration
public class AsyncConfig {

  @Bean(name = "downloadTaskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // 保证核心数至少为2，避免环境问题导致返回0或1
    int processors = Runtime.getRuntime().availableProcessors();
    int corePoolSize = Math.max(2, processors);

    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(Math.max(corePoolSize, 4)); // 保证maxPoolSize不小于corePoolSize
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("PP-Downloader-");
    executor.initialize();
    return executor;
  }
}
