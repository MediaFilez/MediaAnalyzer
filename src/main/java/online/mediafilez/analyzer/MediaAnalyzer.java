package online.mediafilez.analyzer;

import org.bytedeco.javacv.FFmpegFrameGrabber;

public class MediaAnalyzer {
    private String url;

    public MediaAnalyzer(String url) {
        this.url = url;
    }

    public MediaInfo analyze() {
        if (url == null || url.isBlank()) {
            System.out.println("Invalid URL: URL must not be empty or blank.");
            return null;
        }

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(url);

        try {
            start(grabber);
            MediaInfo info = getInfo(grabber);

            printResult(info);
            return info;
        } catch (Exception e) {
            System.out.println("Unable to analyze media: " + e.getMessage());
            return null;
        } finally {
            release(grabber);
        }
    }

    private MediaInfo getInfo(FFmpegFrameGrabber grabber) throws Exception {
        double duration = getDuration(grabber);
        double frameRate = getFrameRate(grabber);

        String format = getFormat(grabber);
        String videoCodec = getVideoCodec(grabber);
        String audioCodec = getAudioCodec(grabber);

        int width = getImageWidth(grabber);
        int height = getImageHeight(grabber);
        int videoBitrate = getVideoBitrate(grabber);
        int audioBitrate = getAudioBitrate(grabber);
        int sampleRate = getSampleRate(grabber);
        int audioChannels = getAudioChannels(grabber);

        return new MediaInfo(format, width, height, duration, frameRate, videoBitrate,
                videoCodec, audioCodec, audioBitrate, sampleRate, audioChannels);
    }

    private void start(FFmpegFrameGrabber grabber) throws Exception {
        grabber.start();
    }

    private String getFormat(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getFormat();
    }

    private String getVideoCodec(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getVideoCodecName();
    }

    private String getAudioCodec(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getAudioCodecName();
    }

    private int getVideoBitrate(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getVideoBitrate();
    }

    private int getAudioBitrate(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getAudioBitrate();
    }

    private int getSampleRate(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getSampleRate();
    }

    private int getAudioChannels(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getAudioChannels();
    }

    private double getFrameRate(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getFrameRate();
    }

    private double getDuration(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getLengthInTime() / 1_000_000.0;
    }

    private int getImageWidth(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getImageWidth();
    }

    private int getImageHeight(FFmpegFrameGrabber grabber) throws Exception {
        return grabber.getImageHeight();
    }

    private void printResult(MediaInfo info) {
        if (info == null) {
            System.out.println("No media information found.");
        } else {
            System.out.println(info);
        }
    }

    private void release(FFmpegFrameGrabber grabber) {
        try {
            grabber.release();
        } catch (Exception e) {
            System.out.println("Error releasing grabber: " + e.getMessage());
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }
}