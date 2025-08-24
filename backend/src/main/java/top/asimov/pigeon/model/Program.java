package top.asimov.pigeon.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("program")
public class Program {
    @TableId
    private String id;
    private String channelId;
    private Integer position;
    private String title;
    private String description;
    private LocalDateTime publishedAt;
    private String coverUrl;
}
