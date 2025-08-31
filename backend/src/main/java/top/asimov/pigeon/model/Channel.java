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
@TableName("channel")
public class Channel {

  @TableId
  private String id;
  private String handler;
  private String name;
  private String avatarUrl;
  private String description;
  private LocalDateTime registeredAt;
  private Integer videoCount;
  private Integer subscriberCount;
  private Integer viewCount;
  private String channelUrl;
  private String channelSource;
  private Integer updateFrequency;
  private String lastSyncVideoId;
  private LocalDateTime lastSyncTimestamp;
  private LocalDateTime subscribedAt;

  private transient LocalDateTime lastPublishedAt;
}
