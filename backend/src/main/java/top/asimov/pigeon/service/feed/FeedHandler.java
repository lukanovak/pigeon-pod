package top.asimov.pigeon.service.feed;

import java.util.List;
import java.util.Map;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.model.Feed;
import top.asimov.pigeon.model.FeedConfigUpdateResult;
import top.asimov.pigeon.model.FeedPack;
import top.asimov.pigeon.model.FeedSaveResult;

public interface FeedHandler<T extends Feed> {

  FeedType getType();

  List<T> list();

  T detail(String id);

  String getSubscribeUrl(String id);

  FeedConfigUpdateResult updateConfig(String id, Map<String, Object> payload);

  FeedPack<T> fetch(Map<String, ?> payload);

  FeedPack<T> preview(Map<String, Object> payload);

  FeedSaveResult<T> add(Map<String, Object> payload);

  void delete(String id);
}
