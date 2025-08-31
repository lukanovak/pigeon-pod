package top.asimov.pigeon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.service.ChannelService;

@SaCheckLogin
@RestController
@RequestMapping("/api/channel")
public class ChannelController {

  private final ChannelService channelService;

  public ChannelController(ChannelService channelService) {
    this.channelService = channelService;
  }

  @GetMapping("/list")
  public SaResult channelList() {
    List<Channel> channels = channelService.selectChannelList();
    return SaResult.data(channels);
  }

  @GetMapping("/detail/{id}")
  public SaResult channelDetail(@PathVariable(name = "id") String id) {
    Channel channel = channelService.channelDetail(id);
    return SaResult.data(channel);
  }

  @GetMapping("/subscribe")
  public SaResult getSubscribeUrl(@RequestParam(name = "handler") String handler) {
    String feedUrl = channelService.getChannelRssFeedUrl(handler);
    return SaResult.data(feedUrl);
  }

  @PostMapping("/add")
  public SaResult addChannel(@RequestBody Channel channel) {
    return SaResult.data(channelService.fetchAndSaveChannel(channel));
  }

  @DeleteMapping("/delete/{id}")
  public SaResult deleteChannel(@PathVariable(name = "id") String id) {
    channelService.deleteChannel(id);
    return SaResult.ok();
  }
}
