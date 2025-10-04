package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.Channel;

public interface ChannelMapper extends BaseMapper<Channel> {

  @Select("SELECT c.id, c.handler, c.title, c.cover_url, c.description, c.source, c.audio_quality, " +
      "max(e.published_at) as last_published_at " +
      "FROM channel c LEFT JOIN episode e ON c.id = e.channel_id " +
      "GROUP BY c.id, c.handler, c.title, c.cover_url, c.description, c.source, c.audio_quality " +
      "ORDER BY (CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END) DESC")
  List<Channel> selectChannelsByLastUploadedAt();
}
