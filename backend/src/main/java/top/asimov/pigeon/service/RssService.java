package top.asimov.pigeon.service;

import com.rometools.modules.itunes.EntryInformation;
import com.rometools.modules.itunes.EntryInformationImpl;
import com.rometools.modules.itunes.FeedInformation;
import com.rometools.modules.itunes.FeedInformationImpl;
import com.rometools.modules.itunes.types.Duration;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;

@Log4j2
@Service
public class RssService {

  private final ChannelService channelService;
  private final EpisodeService episodeService;
  private final MessageSource messageSource;

  // 从 application.properties 读取应用基础 URL
  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;

  public RssService(ChannelService channelService, EpisodeService episodeService, MessageSource messageSource) {
    this.channelService = channelService;
    this.episodeService = episodeService;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 appBaseUrl 值
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  /**
   * 根据 channel handler 生成 RSS Feed XML 字符串。
   */
  //@Cacheable(value = "rssFeeds", key = "#channelHandler")
  public String generateRssFeed(String channelHandler) throws MalformedURLException {
    // 1. 获取频道信息
    Channel channel = channelService.findChannelByIdentification(channelHandler);
    if (ObjectUtils.isEmpty(channel)) {
      throw new BusinessException(messageSource.getMessage("channel.not.found.handler", new Object[]{channelHandler}, LocaleContextHolder.getLocale()));
    }

    // 2. 创建 SyndFeed (RSS 的顶层对象)
    SyndFeed feed = new SyndFeedImpl();
    feed.setFeedType("rss_2.0");
    feed.setTitle(channel.getName());
    feed.setLink(channel.getChannelUrl());
    feed.setDescription(channel.getDescription());
    feed.setPublishedDate(new Date()); // 设置为当前时间或最新一期节目的时间

    // 3. 添加 iTunes 频道级信息
    FeedInformation feedInfo = new FeedInformationImpl();
    feedInfo.setAuthor(channel.getName());
    feedInfo.setSummary(channel.getDescription());
    feedInfo.setImage(new URL(channel.getAvatarUrl()));
    feed.getModules().add(feedInfo);

    // 4. 获取已完成下载的节目列表，按position排序
    List<Episode> episodes = episodeService.getEpisodeOrderByPublishDateDesc(channel.getId());

    // 5. 为每个 Episode 创建一个 SyndEntry (RSS item)
    List<SyndEntry> entries = new ArrayList<>();
    for (Episode episode : episodes) {
      SyndEntry entry = new SyndEntryImpl();
      entry.setTitle(episode.getTitle());
      entry.setLink("https://www.youtube.com/watch?v=" + episode.getId()); // 链接指向 YouTube 视频页
      entry.setPublishedDate(Date.from(episode.getPublishedAt().toInstant(java.time.ZoneOffset.UTC)));

      // 描述信息
      SyndContent description = new SyndContentImpl();
      description.setType("text/html");
      description.setValue(episode.getDescription().replaceAll("\n", "<br/>"));
      entry.setDescription(description);

      // **关键：添加附件 (Enclosure)，即音频文件**
      try {
        SyndEnclosure enclosure = new SyndEnclosureImpl();
        
        // 使用episode ID生成URL
        String audioUrl = appBaseUrl + "/media/" + episode.getId() + ".mp3";
        
        enclosure.setUrl(audioUrl);
        enclosure.setType("audio/mpeg");
        // 获取文件大小
        long fileSize = Files.size(Paths.get(episode.getAudioFilePath()));
        enclosure.setLength(fileSize);
        entry.setEnclosures(Collections.singletonList(enclosure));
      } catch (Exception e) {
        // 如果文件不存在或无法读取大小，跳过这个 enclosure
        log.error("无法为 episode {} 创建 enclosure: {}", episode.getId(), e.getMessage());
        continue; 
      }

      // 添加 iTunes 节目级信息
      EntryInformation entryInfo = new EntryInformationImpl();
      entryInfo.setSummary(episode.getDescription());
      entryInfo.setDuration(convertToRomeDuration(episode.getDuration())); // 格式化时长
      if (episode.getMaxCoverUrl() != null) {
        entryInfo.setImage(new java.net.URL(episode.getMaxCoverUrl()));
      }
      entry.getModules().add(entryInfo);

      entries.add(entry);
    }

    feed.setEntries(entries);

    // 6. 将 SyndFeed 对象转换为 XML 字符串
    try (StringWriter writer = new StringWriter()) {
      SyndFeedOutput output = new SyndFeedOutput();
      output.output(feed, writer);
      return writer.toString();
    } catch (Exception e) {
      throw new RuntimeException(messageSource.getMessage("system.generate.rss.failed", 
          null, LocaleContextHolder.getLocale()), e);
    }
  }

  private Duration convertToRomeDuration(String isoDuration) {
    if (isoDuration == null || isoDuration.isBlank()) {
      return new Duration();
    }
    try {
      // 1. 解析为 Java Duration
      java.time.Duration javaDuration = java.time.Duration.parse(isoDuration);
      // 2. 获取总毫秒数
      long millis = javaDuration.toMillis();
      // 3. 创建 ROME Duration
      return new Duration(millis);
    } catch (Exception e) {
      // 如果解析失败，返回一个0时长的对象并记录日志
      log.warn("无法解析时长字符串: '{}', 将返回0时长.", isoDuration, e);
      return new Duration();
    }
  }
}
