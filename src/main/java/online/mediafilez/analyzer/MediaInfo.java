package online.mediafilez.analyzer;

public class MediaInfo {
    private String format;
    private int width;
    private int height;
    private double duration;
    private double frameRate;
    private int videoBitrate;

    private String videoCodec;
    private String audioCodec;
    private int audioBitrate;
    private int sampleRate;
    private int audioChannels;

    public MediaInfo(String format, int width, int height, double duration, double frameRate, int videoBitrate,
                     String videoCodec, String audioCodec, int audioBitrate, int sampleRate, int audioChannels) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.frameRate = frameRate;
        this.videoBitrate = videoBitrate;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.audioBitrate = audioBitrate;
        this.sampleRate = sampleRate;
        this.audioChannels = audioChannels;
    }

    public String getFormat() {
        return format;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getDuration() {
        return duration;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public int getVideoBitrate() {
        return videoBitrate;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public int getAudioBitrate() {
        return audioBitrate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getAudioChannels() {
        return audioChannels;
    }

    @Override
    public String toString() {
        return "MediaInfo{" +
                "format='" + format + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", duration=" + duration +
                ", frameRate=" + frameRate +
                ", videoBitrate=" + videoBitrate +
                ", videoCodec='" + videoCodec + '\'' +
                ", audioCodec='" + audioCodec + '\'' +
                ", audioBitrate=" + audioBitrate +
                ", sampleRate=" + sampleRate +
                ", audioChannels=" + audioChannels +
                '}';
    }
}
