package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.asimov.pigeon.model.Episode;

@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {
}
