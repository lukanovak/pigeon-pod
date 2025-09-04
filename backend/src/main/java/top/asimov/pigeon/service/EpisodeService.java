package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

@Log4j2
@Service
public class EpisodeService {

  protected final EpisodeMapper episodeMapper;

  public EpisodeService(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  public Page<Episode> episodePage(String channelId, Page<Episode> page) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    queryWrapper.orderByDesc(Episode::getPublishedAt);
    return episodeMapper.selectPage(page, queryWrapper);
  }

  public List<Episode> findByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectList(queryWrapper);
  }

  public List<Episode> getEpisodeOrderByPublishDateDesc(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId).orderByDesc(Episode::getPublishedAt);
    return episodeMapper.selectList(queryWrapper);
  }

  @Transactional
  public void saveEpisodes(List<Episode> episodes) {
    episodes.forEach(episodeMapper::insert);
  }

  @Transactional
  public int deleteEpisodeById(String id) {
    Episode episode = episodeMapper.selectById(id);
    String audioFilePath = episode.getAudioFilePath();
    if (StringUtils.hasText(audioFilePath)) {
      try {
        Files.deleteIfExists(Paths.get(audioFilePath));
      } catch (Exception e) {
        log.error("Failed to delete audio file: " + audioFilePath, e);
        throw new BusinessException("Failed to delete audio file: " + audioFilePath);
      }
    }
    return episodeMapper.deleteById(id);
  }

  public int deleteEpisodesByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.delete(wrapper);
  }

}
