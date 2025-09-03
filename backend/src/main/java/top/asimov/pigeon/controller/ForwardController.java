package top.asimov.pigeon.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ForwardController {

  @GetMapping("/**")
  public String forward(HttpServletRequest request) {
    String uri = request.getRequestURI();

    // 排除 API 请求
    if (uri.startsWith("/api/")) {
      return null; // 交给后端 Controller 处理
    }

    // 排除静态资源（带扩展名的路径）
    if (uri.contains(".")) {
      return null; // 交给 Spring Boot 静态资源处理
    }

    // 其他情况一律转发到 index.html，让前端路由接管
    return "forward:/index.html";
  }
}
