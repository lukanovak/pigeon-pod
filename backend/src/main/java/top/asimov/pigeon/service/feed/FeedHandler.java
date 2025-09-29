package top.asimov.pigeon.service.feed;

import java.util.List;
import java.util.Map;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.model.Feed;

public interface FeedHandler<T extends Feed> {

  FeedType getType();

  List<T> list();

  T detail(String id);

  String getSubscribeUrl(String id);

  Object updateConfig(String id, Map<String, Object> payload);

  Object fetch(Map<String, ?> payload);

  Object preview(Map<String, Object> payload);

  Object add(Map<String, Object> payload);

  void delete(String id);
}
