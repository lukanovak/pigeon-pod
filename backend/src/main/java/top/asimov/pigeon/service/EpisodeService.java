package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
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

  public List<Episode> saveEpisodes(List<Episode> episodes) {
    episodes.forEach(episodeMapper::insert);
    return episodes;
  }

  public int removeEpisodes(LambdaQueryWrapper<Episode> wrapper) {
    return episodeMapper.delete(wrapper);
  }
}
