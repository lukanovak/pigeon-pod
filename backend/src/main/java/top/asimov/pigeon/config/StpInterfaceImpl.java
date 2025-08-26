package top.asimov.pigeon.config;

import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StpInterfaceImpl implements StpInterface {

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    return List.of();
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    return List.of();
  }

  @Override
  public SaDisableWrapperInfo isDisabled(Object loginId, String service) {
    return StpInterface.super.isDisabled(loginId, service);
  }
}
