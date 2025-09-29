package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.asimov.pigeon.constant.FeedSource;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("channel")
public class Feed {

  private String id;
  private String title;
  private String coverUrl;
  private String description;

  private FeedSource feedSource;

  private LocalDateTime lastPublishedAt;

}
