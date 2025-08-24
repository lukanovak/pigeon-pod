package top.asimov.pigeon.controller;

import cn.dev33.satoken.util.SaResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.service.ChannelService;

@RestController
@RequestMapping("/api/channel")
public class ChannelController {

  private final ChannelService channelService;

  public ChannelController(ChannelService channelService) {
    this.channelService = channelService;
  }

  @GetMapping("/list")
  public SaResult channelList(@RequestParam(name = "name", required = false) String name,
      @RequestParam(name = "description", required = false) String description) {
    Channel channel = Channel.builder().name(name).description(description).build();
    List<Channel> channels = channelService.listAllChannels(channel);
    return SaResult.data(channels);
  }

  @PostMapping("/add")
  public SaResult addChannel(@RequestBody Channel channel) {
    return SaResult.data(channelService.fetchAndSaveChannel(channel));
  }
}
