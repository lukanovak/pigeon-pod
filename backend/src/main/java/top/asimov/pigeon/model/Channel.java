package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
  private String channelSource;


  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private transient String channelUrl;
}
