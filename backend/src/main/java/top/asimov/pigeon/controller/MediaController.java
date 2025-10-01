package top.asimov.pigeon.controller;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.service.MediaService;

@Log4j2
@RestController
@RequestMapping("/media")
public class MediaController {

  @Autowired
  private MediaService mediaService;

  /**
   * 处理媒体文件请求 URL格式：/media/{episodeId}.mp3
   */
  @GetMapping("/{episodeId}.mp3")
  public ResponseEntity<Resource> getMediaFile(@PathVariable String episodeId) {
    try {
      log.info("请求媒体文件，episode ID: {}", episodeId);

      // 通过MediaService获取音频文件
      File audioFile = mediaService.getAudioFile(episodeId);

      // 创建文件资源
      Resource resource = new FileSystemResource(audioFile);

      // 设置响应头
      HttpHeaders headers = new HttpHeaders();
      // 对文件名进行URL编码以支持中文字符
      String encodedFileName = URLEncoder.encode(audioFile.getName(), StandardCharsets.UTF_8)
          .replace("+", "%20"); // 将+替换为%20，符合RFC标准
      headers.add(HttpHeaders.CONTENT_DISPOSITION,
          "inline; filename*=UTF-8''" + encodedFileName);

      // 根据文件扩展名设置Content-Type
      MediaType mediaType = getMediaTypeByFileName(audioFile.getName());

      return ResponseEntity.ok()
          .headers(headers)
          .contentLength(audioFile.length())
          .contentType(mediaType)
          .body(resource);

    } catch (BusinessException e) {
      log.error("业务异常: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("处理媒体文件请求时发生错误", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * 根据文件名获取MediaType
   */
  private MediaType getMediaTypeByFileName(String fileName) {
    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    return switch (extension) {
      case "mp3" -> MediaType.valueOf("audio/mpeg");
      case "m4a" -> MediaType.valueOf("audio/mp4");
      case "wav" -> MediaType.valueOf("audio/wav");
      case "ogg" -> MediaType.valueOf("audio/ogg");
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}
