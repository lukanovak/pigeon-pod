package top.asimov.pigeon.service;

import java.io.File;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

@Log4j2
@Service
public class MediaService {

  @Autowired
  private EpisodeMapper episodeMapper;

  @Autowired
  private MessageSource messageSource;

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;

  /**
   * 根据episode ID获取音频文件
   *
   * @param episodeId episode的ID
   * @return 音频文件对象
   * @throws BusinessException 当文件不存在或无法访问时抛出
   */
  public File getAudioFile(String episodeId) throws BusinessException {
    log.info("获取音频文件，episode ID: {}", episodeId);

    // 根据episode ID查询数据库获取文件路径
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.warn("未找到episode: {}", episodeId);
      throw new BusinessException(messageSource.getMessage("episode.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    String audioFilePath = episode.getAudioFilePath();
    if (!StringUtils.hasText(audioFilePath)) {
      log.warn("Episode {} 没有关联的音频文件路径", episodeId);
      throw new BusinessException(messageSource.getMessage("media.file.not.found",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    // 创建文件对象并验证
    File audioFile = new File(audioFilePath);
    if (!audioFile.exists() || !audioFile.isFile()) {
      log.warn("音频文件不存在: {}", audioFilePath);
      throw new BusinessException(messageSource.getMessage("media.file.not.exists",
          new Object[]{audioFilePath}, LocaleContextHolder.getLocale()));
    }

    // 安全检查：确保文件在允许的目录内
    if (!isFileInAllowedDirectory(audioFile)) {
      log.error("尝试访问不被允许的文件路径: {}", audioFilePath);
      throw new BusinessException(messageSource.getMessage("media.file.access.denied",
          new Object[]{episodeId}, LocaleContextHolder.getLocale()));
    }

    log.info("找到音频文件: {}", audioFilePath);
    return audioFile;
  }

  /**
   * 安全检查：确保请求的文件在允许的目录内
   */
  private boolean isFileInAllowedDirectory(File file) {
    try {
      String canonicalFilePath = file.getCanonicalPath();
      String canonicalAllowedPath = new File(audioStoragePath).getCanonicalPath();
      return canonicalFilePath.startsWith(canonicalAllowedPath);
    } catch (IOException e) {
      log.error("安全检查时发生错误", e);
      return false;
    }
  }
}
