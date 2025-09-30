package top.asimov.pigeon.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPack<T extends Feed> {

  private T feed;
  private List<Episode> episodes;
}
