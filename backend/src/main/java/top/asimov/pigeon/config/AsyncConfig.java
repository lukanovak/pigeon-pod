package top.asimov.pigeon.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Log4j2
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "downloadTaskExecutor")
  public ThreadPoolTaskExecutor downloadTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // 启用 SQLite WAL 模式后，可以支持适度的并发
    // 设置为 3 个核心线程，平衡并发性能和资源使用
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(3);  // 最大 3 个线程
    executor.setQueueCapacity(10); // 设置队列容量
    executor.setThreadNamePrefix("PP-Downloader-");
    executor.setKeepAliveSeconds(60); // 空闲线程保持时间
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // 拒绝策略
    executor.initialize();
    
    log.info("下载线程池已配置: 核心线程数={}, 最大线程数={}, 队列容量={}", 
        executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
    
    return executor;
  }

  @Bean(name = "channelSyncTaskExecutor")
  public Executor channelSyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(3);
    executor.setThreadNamePrefix("PP-ChannelAsync-");
    executor.setKeepAliveSeconds(60);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    
    log.info("频道同步线程池已配置: 核心线程数={}, 最大线程数={}, 队列容量={}",
        executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
    
    return executor;
  }
}
