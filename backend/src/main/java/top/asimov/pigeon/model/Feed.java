package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import top.asimov.pigeon.constant.FeedType;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Feed {

  @TableId
  private String id;

  private String title;
  private String coverUrl;
  private String source;
  private String description;
  private String containKeywords;
  private String excludeKeywords;
  private Integer minimumDuration;
  private Integer initialEpisodes;
  private Integer maximumEpisodes;
  private String lastSyncVideoId;
  private LocalDateTime lastSyncTimestamp;
  private LocalDateTime subscribedAt;

  @TableField(exist = false)
  private transient String originalUrl;

  @TableField(exist = false)
  private transient LocalDateTime lastPublishedAt;

  public abstract FeedType getType();
}
