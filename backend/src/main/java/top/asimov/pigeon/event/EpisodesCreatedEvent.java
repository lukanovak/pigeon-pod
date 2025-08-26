package top.asimov.pigeon.event;

import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EpisodesCreatedEvent extends ApplicationEvent {

  private final List<String> episodeIds;

  public EpisodesCreatedEvent(Object source, List<String> episodeIds) {
    super(source);
    this.episodeIds = episodeIds;
  }

}
