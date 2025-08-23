package top.asimov.pigeonpod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.asimov.pigeonpod.model.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
