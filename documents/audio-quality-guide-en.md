# Understanding the Audio Quality Setting

This guide explains what the `Audio quality` option in PigeonPod does, why it might not always change the size of downloaded files, and how to choose a value when you want smaller downloads.

## What the Setting Actually Controls

- PigeonPod downloads the best audio stream that YouTube provides, then uses `yt-dlp` and ffmpeg to convert it to MP3 so every episode is stored in the same format.
- The `Audio quality` slider passes `--audio-quality` to ffmpeg. Lower numbers mean "keep as much detail as possible", higher numbers tell ffmpeg to compress the audio more aggressively.
- The scale comes from LAME (the MP3 encoder). `0` is the best quality (largest files), `10` is the lowest quality (smallest files).

## Why File Size Sometimes Stays the Same

It is normal to see little or no difference across values like 1–5. YouTube usually serves audio around 128–160 kbps. If you ask ffmpeg for a higher-quality output (for example `1` or `2`), it cannot add detail that does not exist in the source. The encoder notices that the audio is already "simple enough" and sticks close to the original bitrate, so the file size barely changes.

You will only see a significant reduction when you pick a value that is far lower than the original bitrate. For example, `9` or `10` often drops the result to about 64 kbps, which can almost halve the size of spoken-word podcasts.

## Tips for Choosing a Value

| Goal | Suggested value | What to expect |
| ---- | --------------- | -------------- |
| Keep the best possible quality | Leave empty (default) or `0` | Largest files, closest to YouTube's original stream |
| Slight size reduction with minimal quality loss | `6` or `7` | Noticeable but moderate compression |
| Maximum size reduction | `9` or `10` | Strong compression (~64 kbps); best for speech, not ideal for music |

Remember: smaller files always come with a drop in fidelity. If you are archiving music, prefer values between `0` and `4`. For talk-heavy podcasts, `8`–`10` usually sounds fine on small speakers while saving space.

## Frequently Asked Questions

**Q: Why does the bitrate shown by my player not match the number I entered?**  
Because the setting is a quality level, not a fixed bitrate target. The encoder chooses the final bitrate automatically based on the audio content.

**Q: I set the value to `10` and speech sounds OK but music feels flat. Why?**  
High compression removes subtle details (especially in music). Increase the value toward `5` or lower to preserve more detail.

