package top.asimov.pigeon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.asimov.pigeon.model.Program;

@Mapper
public interface ProgramMapper extends BaseMapper<Program> {
}
