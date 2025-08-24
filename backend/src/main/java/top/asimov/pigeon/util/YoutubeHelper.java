package top.asimov.pigeon.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class YoutubeHelper {

  // 替换为你的 API 密钥
  public static final String API_KEY = "AIzaSyBNiC1-OwYusXLlXpkZzm5lfw3qjX3kgWY";
  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /**
   * 初始化 YouTube 服务.
   *
   * @return YouTube service 对象.
   * @throws GeneralSecurityException, IOException
   */
  public static YouTube getService() throws GeneralSecurityException, IOException {
    return new YouTube.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        null // 我们使用 API 密钥，所以不需要 HttpCredentials
    )
        .setApplicationName(APPLICATION_NAME)
        .build();
  }

  /**
   * 从 YouTube URL 中提取 handle. e.g., "https://www.youtube.com/@xxxx" -> "xxxx"
   *
   * @param channelUrl YouTube 频道的 URL.
   * @return 频道的 handle.
   */
  public static String getHandleFromUrl(String channelUrl) {
    if (channelUrl == null || !channelUrl.contains("@")) {
      return null;
    }
    int atIndex = channelUrl.lastIndexOf('@');
    return channelUrl.substring(atIndex + 1);
  }

  /**
   * 根据 handle 查找 Channel ID.
   *
   * @param youtubeService YouTube service 对象.
   * @param handle         频道的 handle.
   * @return Channel ID.
   * @throws IOException
   */
  public static String getChannelIdByHandle(YouTube youtubeService, String handle)
      throws IOException {
    YouTube.Search.List searchListRequest = youtubeService.search()
        .list("snippet")
        .setQ(handle) // 使用 handle 作为查询词
        .setType("channel") // 只搜索频道
        .setMaxResults(1L); // 我们只需要最相关的那个

    searchListRequest.setKey(API_KEY);
    SearchListResponse response = searchListRequest.execute();
    List<SearchResult> searchResults = response.getItems();

    if (searchResults != null && !searchResults.isEmpty()) {
      // 一个结果就是我们想要的频道
      return searchResults.get(0).getSnippet().getChannelId();
    }
    return null;
  }

  /**
   * 将DateTime转换为LocalDateTime
   *
   * @param dateTime Google API返回的DateTime
   * @return LocalDateTime
   */
  public static LocalDateTime convertToLocalDateTime(DateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(dateTime.getValue()),
        ZoneId.systemDefault()
    );
  }

}
