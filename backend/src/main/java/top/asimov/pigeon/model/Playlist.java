package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import top.asimov.pigeon.constant.FeedType;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("playlist")
public class Playlist extends Feed {

  private String ownerId;

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }
}
