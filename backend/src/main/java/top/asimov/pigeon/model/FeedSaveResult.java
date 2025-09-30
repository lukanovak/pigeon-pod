package top.asimov.pigeon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedSaveResult<T extends Feed> {

  private T feed;
  private boolean async;
  private String message;
}
