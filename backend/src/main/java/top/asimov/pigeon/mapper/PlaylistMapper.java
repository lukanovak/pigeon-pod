package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import top.asimov.pigeon.model.Playlist;

public interface PlaylistMapper extends BaseMapper<Playlist> {

  @Select("SELECT p.id, p.owner_id, p.title, p.cover_url, p.description, p.playlist_source, "
      + "p.contain_keywords, p.exclude_keywords, p.minimum_duration, p.initial_episodes, "
      + "p.maximum_episodes, p.last_sync_video_id, p.last_sync_timestamp, p.subscribed_at, "
      + "MAX(pe.published_at) AS last_published_at "
      + "FROM playlist p "
      + "LEFT JOIN playlist_episode pe ON p.id = pe.playlist_id "
      + "GROUP BY p.id, p.owner_id, p.title, p.cover_url, p.description, p.playlist_source, "
      + "p.contain_keywords, p.exclude_keywords, p.minimum_duration, p.initial_episodes, "
      + "p.maximum_episodes, p.last_sync_video_id, p.last_sync_timestamp, p.subscribed_at "
      + "ORDER BY CASE WHEN last_published_at IS NULL THEN '9999' ELSE last_published_at END DESC")
  List<Playlist> selectPlaylistsByLastPublishedAt();
}

