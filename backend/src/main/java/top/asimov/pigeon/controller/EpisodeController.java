package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.service.EpisodeService;

@SaCheckLogin
@RestController
@RequestMapping("/api/program")
public class EpisodeController {

  private final EpisodeService episodeService;

  public EpisodeController(EpisodeService episodeService) {
    this.episodeService = episodeService;
  }

  @GetMapping("/list/{channelId}")
  public SaResult programsOfChannel(@PathVariable(name = "channelId") String channelId,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer size) {
    Page<Episode> episodeList = episodeService.episodePage(channelId, new Page<>(page, size));
    return SaResult.data(episodeList);
  }

}
