package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.Channel;

@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

  @Select("SELECT c.id, c.handler, c.name, c.avatar_url, c.description, c.channel_source, " +
      "max(e.published_at) as last_published_at " +
      "FROM channel c JOIN episode e ON c.id = e.channel_id " +
      "GROUP BY c.id, c.handler, c.name, c.avatar_url, c.description, c.channel_source " +
      "ORDER BY last_published_at DESC")
  List<Channel> selectChannelWithLastUploadedAt();
}
