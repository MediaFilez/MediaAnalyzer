package online.mediafilez.analyzer;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacketSideData;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;
import org.bytedeco.ffmpeg.avutil.AVPixFmtDescriptor;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class MediaAnalyzer {
    private final String url;

    public MediaAnalyzer(String url) {
        this.url = url;
    }

    public MediaInfo analyze() {
        if (url == null || url.isBlank()) {
            System.err.println("Invalid URL: URL must not be empty or blank.");
            return null;
        }

        // try-with-resources also releases FFmpeg when opening or analysis fails.
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(url)) {
            av_log_set_level(AV_LOG_ERROR);
            grabber.setOption("rw_timeout", "15000000");
            grabber.setOption("user_agent", "MediaAnalyzer/0.1");
            grabber.start();
            return getInfo(grabber);
        } catch (Exception e) {
            System.err.println("Unable to analyze media: " + e.getMessage());
            return null;
        } catch (LinkageError e) {
            System.err.println("Unable to load FFmpeg. Make sure the runtime libraries are on the classpath.");
            return null;
        }
    }

    private MediaInfo getInfo(FFmpegFrameGrabber grabber) {
        AVFormatContext context = grabber.getFormatContext();
        double duration = context.duration() == AV_NOPTS_VALUE ? Double.NaN : context.duration() / 1_000_000.0;
        MediaInfo info = new MediaInfo(grabber.getFormat(), duration);

        info.addContainerDetail("Format description", text(context.iformat().long_name()));
        info.addContainerDetail("Size from container", ReportFormatter.size(
                context.pb() == null || context.pb().isNull() ? -1 : avio_size(context.pb())));
        info.addContainerDetail("Overall bitrate", ReportFormatter.bitrate(context.bit_rate()));
        info.addContainerDetail("Start time", timestamp(context.start_time(), 1.0 / 1_000_000));
        info.addContainerDetail("Total streams", Integer.toString(context.nb_streams()));
        info.addContainerDetail("Chapter count", Integer.toString(context.nb_chapters()));
        info.setMetadata(readMetadata(context.metadata()));

        // Copy every stream into Java objects before the grabber closes.
        for (int i = 0; i < context.nb_streams(); i++) {
            info.addStream(getStreamInfo(context, context.streams(i)));
        }
        return info;
    }

    private MediaStreamInfo getStreamInfo(AVFormatContext context, AVStream stream) {
        AVCodecParameters codec = stream.codecpar();
        String type = text(av_get_media_type_string(codec.codec_type()));
        MediaStreamInfo info = new MediaStreamInfo(stream.index(), type);
        info.addDetail("Codec", text(avcodec_get_name(codec.codec_id())));
        info.addDetail("Profile", text(avcodec_profile_name(codec.codec_id(), codec.profile())));
        info.addDetail("Codec level (raw)", codec.level() < 0 ? "Unknown" : Integer.toString(codec.level()));
        info.addDetail("Bitrate", ReportFormatter.bitrate(codec.bit_rate()));
        info.addDetail("Duration", ReportFormatter.duration(streamDuration(stream)));
        info.addDetail("Start time", timestamp(stream.start_time(), rationalValue(stream.time_base())));
        info.addDetail("Time base", fraction(stream.time_base()));
        info.addDetail("Default stream", (stream.disposition() & AV_DISPOSITION_DEFAULT) != 0 ? "Yes" : "No");
        info.addDetail("Forced stream", (stream.disposition() & AV_DISPOSITION_FORCED) != 0 ? "Yes" : "No");
        info.setMetadata(readMetadata(stream.metadata()));

        if (codec.codec_type() == AVMEDIA_TYPE_VIDEO) {
            addVideoDetails(info, context, stream, codec);
        } else if (codec.codec_type() == AVMEDIA_TYPE_AUDIO) {
            addAudioDetails(info, codec);
        }
        return info;
    }

    private void addVideoDetails(MediaStreamInfo info, AVFormatContext context, AVStream stream, AVCodecParameters codec) {
        int width = codec.width();
        int height = codec.height();
        AVRational sampleRatio = av_guess_sample_aspect_ratio(context, stream, null);
        AVRational averageRate = stream.avg_frame_rate();
        double frameRate = rationalValue(averageRate);
        AVPixFmtDescriptor pixel = av_pix_fmt_desc_get(codec.format());

        info.addDetail("Resolution", width > 0 && height > 0 ? width + " x " + height + " px" : "Unknown");
        info.addDetail("Stored orientation", orientation(width, height));
        info.addDetail("Sample aspect ratio", ReportFormatter.ratio(sampleRatio.num(), sampleRatio.den()));
        info.addDetail("Display aspect ratio", ReportFormatter.ratio(
                (long) width * sampleRatio.num(), (long) height * sampleRatio.den()));
        info.addDetail("Average frame rate", ReportFormatter.number(frameRate, "fps") + " (" + fraction(averageRate) + ")");
        info.addDetail("Nominal frame rate", ReportFormatter.number(rationalValue(stream.r_frame_rate()), "fps"));
        info.addDetail("Frame count", frameCount(stream, frameRate));
        // codec.format() is the stored pixel format, not a converted output format.
        info.addDetail("Pixel format", text(av_get_pix_fmt_name(codec.format())));
        info.addDetail("Bit depth", bitDepth(pixel));
        info.addDetail("Color range", colorName(av_color_range_name(codec.color_range())));
        info.addDetail("Color space", colorName(av_color_space_name(codec.color_space())));
        info.addDetail("Color primaries", colorName(av_color_primaries_name(codec.color_primaries())));
        info.addDetail("Transfer characteristic", colorName(av_color_transfer_name(codec.color_trc())));
        info.addDetail("Chroma location", colorName(av_chroma_location_name(codec.chroma_location())));
        info.addDetail("Field order", fieldOrder(codec.field_order()));
        info.addDetail("Rotation", rotation(codec));
        info.addDetail("Attached picture", (stream.disposition() & AV_DISPOSITION_ATTACHED_PIC) != 0 ? "Yes" : "No");
    }

    private void addAudioDetails(MediaStreamInfo info, AVCodecParameters codec) {
        info.addDetail("Sample rate", codec.sample_rate() > 0 ? codec.sample_rate() + " Hz" : "Unknown");
        info.addDetail("Channels", codec.ch_layout().nb_channels() > 0
                ? Integer.toString(codec.ch_layout().nb_channels()) : "Unknown");
        try (BytePointer layout = new BytePointer(256)) {
            int result = av_channel_layout_describe(codec.ch_layout(), layout, 256);
            info.addDetail("Channel layout", result < 0 ? "Unknown" : layout.getString());
        }
        info.addDetail("Sample format", text(av_get_sample_fmt_name(codec.format())));
        int bits = codec.bits_per_raw_sample() > 0 ? codec.bits_per_raw_sample() : codec.bits_per_coded_sample();
        info.addDetail("Reported bit depth", bits > 0 ? bits + " bits/sample" : "Unknown");
    }

    private String orientation(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "Unknown";
        }
        if (width == height) {
            return "Square";
        }
        return width > height ? "Landscape" : "Portrait";
    }

    private String bitDepth(AVPixFmtDescriptor pixel) {
        if (pixel == null || pixel.isNull() || pixel.nb_components() == 0) {
            return "Unknown";
        }
        int firstDepth = pixel.comp(0).depth();
        StringBuilder depths = new StringBuilder(Integer.toString(firstDepth));
        boolean sameDepth = true;
        for (int i = 1; i < pixel.nb_components(); i++) {
            int depth = pixel.comp(i).depth();
            depths.append("/").append(depth);
            if (depth != firstDepth) {
                sameDepth = false;
            }
        }
        return (sameDepth ? Integer.toString(firstDepth) : depths.toString()) + " bits/component";
    }

    private String rotation(AVCodecParameters codec) {
        for (int i = 0; i < codec.nb_coded_side_data(); i++) {
            AVPacketSideData sideData = codec.coded_side_data().getPointer(i);
            if (sideData.type() == AV_PKT_DATA_DISPLAYMATRIX && sideData.size() >= 9L * Integer.BYTES) {
                // FFmpeg stores rotation in a 3 x 3 matrix of 32-bit integers.
                double degrees = av_display_rotation_get(new IntPointer(sideData.data()));
                return Double.isFinite(degrees) ? String.format(Locale.ROOT, "%.2f degrees", degrees) : "Unknown";
            }
        }
        return "Not reported";
    }

    private String frameCount(AVStream stream, double frameRate) {
        if (stream.nb_frames() > 0) {
            return stream.nb_frames() + " (reported)";
        }
        double duration = streamDuration(stream);
        if (Double.isFinite(duration) && duration > 0 && Double.isFinite(frameRate) && frameRate > 0) {
            return Math.round(duration * frameRate) + " (estimated)";
        }
        return "Unknown";
    }

    private double streamDuration(AVStream stream) {
        return stream.duration() == AV_NOPTS_VALUE ? Double.NaN
                : stream.duration() * rationalValue(stream.time_base());
    }

    private String timestamp(long value, double timeBase) {
        if (value == AV_NOPTS_VALUE || !Double.isFinite(timeBase) || timeBase <= 0) {
            return "Unknown";
        }
        return String.format(Locale.ROOT, "%.3f s", value * timeBase);
    }

    private double rationalValue(AVRational value) {
        return value.den() == 0 ? Double.NaN : (double) value.num() / value.den();
    }

    private String fraction(AVRational value) {
        return value.num() <= 0 || value.den() <= 0 ? "Unknown" : value.num() + "/" + value.den();
    }

    private String fieldOrder(int order) {
        return switch (order) {
            case AV_FIELD_PROGRESSIVE -> "Progressive";
            case AV_FIELD_TT -> "Interlaced (top coded first, top displayed first)";
            case AV_FIELD_BB -> "Interlaced (bottom coded first, bottom displayed first)";
            case AV_FIELD_TB -> "Interlaced (top coded first, bottom displayed first)";
            case AV_FIELD_BT -> "Interlaced (bottom coded first, top displayed first)";
            default -> "Unknown";
        };
    }

    private String colorName(BytePointer value) {
        String name = text(value);
        return "unknown".equals(name) || "unspecified".equals(name) ? "Unknown" : name;
    }

    private String text(BytePointer value) {
        return value == null || value.isNull() ? "Unknown" : value.getString();
    }

    private Map<String, String> readMetadata(AVDictionary dictionary) {
        Map<String, String> metadata = new LinkedHashMap<>();
        AVDictionaryEntry entry = null;
        while ((entry = av_dict_get(dictionary, "", entry, AV_DICT_IGNORE_SUFFIX)) != null && !entry.isNull()) {
            metadata.put(entry.key().getString(), entry.value().getString());
        }
        return metadata;
    }

}
