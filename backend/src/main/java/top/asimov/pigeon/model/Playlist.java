package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("playlist")
public class Playlist {

  @TableId
  private String id;
  private String ownerId;
  private String title;
  private String coverUrl;
  private String description;
  private String playlistSource;

  private String containKeywords;
  private String excludeKeywords;
  private Integer minimumDuration;

  private Integer initialEpisodes;
  private Integer maximumEpisodes;
  private String lastSyncVideoId;
  private LocalDateTime lastSyncTimestamp;
  private LocalDateTime subscribedAt;

  private transient LocalDateTime lastPublishedAt;
  private transient String playlistUrl;
}
