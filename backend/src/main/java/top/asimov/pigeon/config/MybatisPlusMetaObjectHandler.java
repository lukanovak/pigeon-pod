package top.asimov.pigeon.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import lombok.extern.log4j.Log4j2;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

  @Override
  public void insertFill(MetaObject metaObject) {
    log.debug("开始插入填充...");
    this.strictInsertFill(metaObject, "subscribedAt", LocalDateTime::now, LocalDateTime.class);
    this.strictInsertFill(metaObject, "lastUpdatedAt", LocalDateTime::now, LocalDateTime.class);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    log.debug("开始更新填充...");
    this.setFieldValByName("lastUpdatedAt", LocalDateTime.now(), metaObject);
  }
}
