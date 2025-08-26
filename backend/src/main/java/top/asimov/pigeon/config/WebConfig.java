package top.asimov.pigeon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // 从 application.properties 读取音频文件存储的物理路径
  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 将 URL 路径 /media/** 映射到文件系统的 audioStoragePath 目录
    // 例如，一个请求 http://your-domain.com/media/video123.mp3
    // 将会去文件系统 D:/podcast-audio/video123.mp3 寻找文件
    registry.addResourceHandler("/media/**")
        .addResourceLocations("file:" + audioStoragePath);
  }
}
