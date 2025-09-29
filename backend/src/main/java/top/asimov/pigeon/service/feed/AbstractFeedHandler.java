package top.asimov.pigeon.service.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Feed;

public abstract class AbstractFeedHandler<T extends Feed> implements FeedHandler<T> {

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  protected AbstractFeedHandler(ObjectMapper objectMapper, MessageSource messageSource) {
    this.objectMapper = objectMapper;
    this.messageSource = messageSource;
  }

  protected MessageSource getMessageSource() {
    return messageSource;
  }

  protected <R> R convert(Map<String, Object> payload, Class<R> targetType) {
    return objectMapper.convertValue(payload, targetType);
  }

  protected String resolveSourceUrl(Map<String, ?> request, String fallbackKey) {
    String sourceUrl = asText(request.get("originalUrl"));
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request.get("sourceUrl"));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = asText(request.get(fallbackKey));
    }
    if (!StringUtils.hasText(sourceUrl)) {
      throw new BusinessException(messageSource
          .getMessage("feed.source.url.missing", null, LocaleContextHolder.getLocale()));
    }
    return sourceUrl;
  }

  private String asText(Object value) {
    return value instanceof String ? (String) value : null;
  }
}
