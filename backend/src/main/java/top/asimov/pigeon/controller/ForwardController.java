package top.asimov.pigeon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ForwardController {
  // Match all paths except those containing a dot (static files) and /api
  @RequestMapping(value = {"/{path:^(?!api$)[^.]*}/**", "/{path:^(?!api$)[^.]*}"})
  public String forward(@PathVariable String path) {
    return "forward:/index.html";
  }
}
