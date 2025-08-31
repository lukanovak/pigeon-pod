package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.Channel;

@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

  @Select("SELECT c.id, c.handler, c.name, c.avatar_url, c.description, " +
      "max(e.published_at) as last_published_at, c.registered_at, c.video_count, " +
      "c.subscriber_count, c.view_count, c.channel_source " +
      "FROM channel c JOIN episode e ON c.id = e.channel_id ")
  List<Channel> selectChannelWithLastUploadedAt();
}
