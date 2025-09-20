package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("episode")
public class Episode {
    @TableId
    private String id;
    private String channelId;
    private Integer position;
    private String title;
    private String description;
    private LocalDateTime publishedAt;
    private String defaultCoverUrl;
    private String maxCoverUrl;
    private String duration; // in ISO 8601 format
    private String downloadStatus;
    private String audioFilePath;
    private String errorLog;
    private LocalDateTime createdAt;
}
