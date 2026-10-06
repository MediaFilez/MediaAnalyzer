# Generated test media

These small fixtures are synthetic. They contain a solid blue image, sine tones,
and a short subtitle. No downloaded media is used.

- `sample.mp4`: one second, 96 x 64 H.264 video at 10 fps, two mono AAC tracks at
  48 kHz (English/Arabic language tags), one subtitle stream, and a title.
- `rotated.mp4`: video only, with a reported display rotation of 90 degrees.
- `audio.m4a`: the first audio track only.
- `estimated.mkv`: video only in Matroska, without a reported frame count or
  stream duration. The report must leave the count unknown when it cannot estimate it.

Tests use the checked-in files and do not need the FFmpeg command-line tool.
To regenerate them, use FFmpeg 6.1.1 from the project root. The commands below
use PowerShell.

```powershell
@'
1
00:00:00,000 --> 00:00:00,800
Fixture subtitle
'@ | Set-Content -LiteralPath target/subtitle.srt -Encoding utf8

ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=blue:s=96x64:r=10:d=1" -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=1" -f lavfi -i "sine=frequency=880:sample_rate=48000:duration=1" -i target/subtitle.srt -map 0:v -map 1:a -map 2:a -map 3:s -c:v libx264 -pix_fmt yuv420p -colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:a aac -c:s mov_text -metadata title="Test fixture" -metadata:s:a:0 language=eng -metadata:s:a:1 language=ara -metadata:s:s:0 language=eng -movflags +faststart src/test/resources/sample.mp4

ffmpeg -hide_banner -loglevel error -y -display_rotation:v:0 90 -i src/test/resources/sample.mp4 -map 0:v -c copy src/test/resources/rotated.mp4
ffmpeg -hide_banner -loglevel error -y -i src/test/resources/sample.mp4 -map 0:a:0 -c copy src/test/resources/audio.m4a
ffmpeg -hide_banner -loglevel error -y -i src/test/resources/sample.mp4 -map 0:v -c copy src/test/resources/estimated.mkv
```
