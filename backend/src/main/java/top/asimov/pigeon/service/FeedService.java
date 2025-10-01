package top.asimov.pigeon.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Feed;
import top.asimov.pigeon.model.FeedConfigUpdateResult;
import top.asimov.pigeon.model.FeedPack;
import top.asimov.pigeon.model.FeedSaveResult;
import top.asimov.pigeon.service.feed.FeedHandler;

@Log4j2
@Service
public class FeedService {

  private final Map<FeedType, FeedHandler<? extends Feed>> handlerRegistry;
  private final MessageSource messageSource;

  public FeedService(List<FeedHandler<? extends Feed>> feedHandlers,
      MessageSource messageSource) {
    Map<FeedType, FeedHandler<? extends Feed>> registry = new EnumMap<>(FeedType.class);
    feedHandlers.forEach(handler -> registry.put(handler.getType(), handler));
    this.handlerRegistry = Collections.unmodifiableMap(registry);
    this.messageSource = messageSource;
  }

  public FeedType resolveType(String rawType) {
    if (!StringUtils.hasText(rawType)) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
    try {
      return FeedType.valueOf(rawType.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
  }

  public List<Feed> listAll() {
    List<Feed> result = new ArrayList<>();
    for (FeedType type : FeedType.values()) {
      FeedHandler<? extends Feed> handler = handlerRegistry.get(type);
      if (handler != null) {
        result.addAll(handler.list());
      }
    }
    return result;
  }

  public Feed detail(FeedType type, String id) {
    return resolveHandler(type).detail(id);
  }

  public String getSubscribeUrl(FeedType type, String id) {
    return resolveHandler(type).getSubscribeUrl(id);
  }

  public FeedConfigUpdateResult updateConfig(FeedType type, String id,
      Map<String, Object> payload) {
    return resolveHandler(type).updateConfig(id, payload);
  }

  public FeedPack<? extends Feed> fetch(Map<String, String> payload) {
    String source = payload == null ? null : payload.getOrDefault("source", null);
    List<FeedType> detectionOrder = buildDetectionOrder(source);
    BusinessException lastError = null;
    for (FeedType type : detectionOrder) {
      FeedHandler<? extends Feed> handler = handlerRegistry.get(type);
      if (handler == null) {
        continue;
      }
      Map<String, Object> handlerPayload = buildFetchPayload(type, source);
      try {
        return handler.fetch(handlerPayload);
      } catch (BusinessException ex) {
        lastError = ex;
      }
    }

    if (lastError != null) {
      throw lastError;
    }

    throw new BusinessException(messageSource
        .getMessage("feed.type.invalid", new Object[]{source},
            LocaleContextHolder.getLocale()));
  }

  public FeedPack<? extends Feed> preview(FeedType type, Map<String, Object> payload) {
    return resolveHandler(type).preview(payload);
  }

  private List<FeedType> buildDetectionOrder(String source) {
    List<FeedType> order = new ArrayList<>();
    FeedType primary = guessFeedType(source);
    order.add(primary);
    for (FeedType candidate : FeedType.values()) {
      if (!order.contains(candidate)) {
        order.add(candidate);
      }
    }
    return order;
  }

  private FeedType guessFeedType(String source) {
    String normalized = source == null ? "" : source.trim().toLowerCase();
    if (normalized.contains("list=") || normalized.contains("playlist")
        || normalized.startsWith("pl") || normalized.startsWith("uu")
        || normalized.startsWith("ol") || normalized.startsWith("ll")) {
      return FeedType.PLAYLIST;
    }
    return FeedType.CHANNEL;
  }

  private Map<String, Object> buildFetchPayload(FeedType type, String source) {
    Map<String, Object> handlerPayload = new HashMap<>();
    if (type == FeedType.PLAYLIST) {
      handlerPayload.put("playlistUrl", source);
    } else if (type == FeedType.CHANNEL) {
      handlerPayload.put("channelUrl", source);
    } else {
      handlerPayload.put("source", source);
    }
    return handlerPayload;
  }

  public FeedSaveResult<? extends Feed> add(FeedType type, Map<String, Object> payload) {
    return resolveHandler(type).add(payload);
  }

  public void delete(FeedType type, String id) {
    resolveHandler(type).delete(id);
  }

  private <T extends Feed> FeedHandler<T> resolveHandler(FeedType type) {
    FeedHandler<? extends Feed> handler = handlerRegistry.get(type);
    if (handler == null) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{type.name()},
              LocaleContextHolder.getLocale()));
    }
    @SuppressWarnings("unchecked")
    FeedHandler<T> typedHandler = (FeedHandler<T>) handler;
    return typedHandler;
  }
}
