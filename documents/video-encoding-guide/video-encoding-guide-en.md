When downloading videos, you can choose a specific video encoding format. This guide explains the options available.

[![视频编码格式说明 中文](https://img.shields.io/badge/视频编码格式说明-%E4%B8%AD%E6%96%87-blue)](video-encoding-guide-zh.md)

### Default

- **Description**: This option uses the video encoding format provided by YouTube itself (usually AV1 or VP9 for better quality videos). It does not perform any extra encoding or decoding processing.
- **Pros**: Fastest download speed.
- **Cons**: The AV1 or VP9 video encoding formats may not be compatible with all devices, especially older ones.

### H.264

- **Description**: This option ensures that the final saved file is always encoded in H.264. H.264 is the most widely used video encoding format and can be played smoothly on almost any device.
- **Pros**: Best compatibility.
- **Cons**: May require additional encoding and decoding processing, which can take significantly longer.

### H.265 (HEVC)

- **Description**: This is a trade-off option that offers a good balance between video quality and file size. It can be played smoothly on most modern devices.
- **Pros**: Better quality-to-file-size ratio than H.264.
- **Cons**: Since YouTube rarely provides H.265 encoded video streams, the program usually needs to re-encode the video when selecting this option, which will take longer. Not as widely compatible as H.264, especially on older devices.

---

### ⚠️ Important: Performance Impact

When you choose H.264 or H.265 and the original video from YouTube is in a different format (like AV1 or VP9), the application must **re-encode** the video.

- **This process is CPU-intensive and can be very time-consuming**, especially for high-resolution (e.g., 4K) and long-duration videos.
- During re-encoding, you may notice high CPU usage on your machine. This is normal behavior for software-based video processing.

### Future Improvements: Hardware Acceleration

We are aware that hardware acceleration (using dedicated chips in Apple M-series, Intel, Nvidia, or AMD hardware) can dramatically speed up re-encoding. However, supporting this across different platforms is a complex task.

Therefore, implementing hardware acceleration is currently a **low-priority task**. It may be considered in the future based on user demand, but it will require significant development time for platform-specific adaptation.

### ⭐ Best Practice Recommendation

If videos you download using the default settings don't play, or you want to ensure smooth playback on all devices, we recommend that you choose H.264 encoding.

For the best balance of **compatibility, quality, and processing speed**, we recommend the following combination:

> **Video Quality: 1080p** + **Video Encoding: H.264**

**Why?** At 1080p resolution, YouTube almost always provides a native H.264 video stream. By choosing this combination, the application can **download the file directly without needing to re-encode**, ensuring both fast processing and excellent compatibility for playback on any device.