package online.mediafilez.analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MediaInfo {
    private final String format;
    private final double duration;

    private final Map<String, String> containerDetails = new LinkedHashMap<>();
    private final List<MediaStreamInfo> streams = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public MediaInfo(String format, double duration) {
        this.format = format;
        this.duration = duration;
    }

    public List<MediaStreamInfo> getStreams() {
        return List.copyOf(streams);
    }

    public void addContainerDetail(String label, String value) {
        containerDetails.put(label, value);
    }

    public void addStream(MediaStreamInfo stream) {
        streams.add(stream);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata.clear();
        this.metadata.putAll(metadata);
    }

    @Override
    public String toString() {
        StringBuilder report = new StringBuilder(ReportFormatter.section("Media summary"));
        report.append(ReportFormatter.row("Detected format", format));
        report.append(ReportFormatter.row("Duration", ReportFormatter.duration(duration)));
        report.append(ReportFormatter.row("Video streams", countStreams("video")));
        report.append(ReportFormatter.row("Audio streams", countStreams("audio")));
        report.append(ReportFormatter.row("Subtitle streams", countStreams("subtitle")));

        report.append(ReportFormatter.rows(containerDetails));

        for (MediaStreamInfo stream : streams) {
            report.append(stream);
        }

        report.append(ReportFormatter.section("File metadata"));
        if (metadata.isEmpty()) {
            report.append("  No file metadata reported.\n");
        } else {
            report.append(ReportFormatter.rows(metadata));
        }
        report.append("\nUnknown = not reported. Estimated values are labeled.\n");
        return report.toString();
    }

    private int countStreams(String type) {
        int count = 0;
        for (MediaStreamInfo stream : streams) {
            if (type.equals(stream.getType())) {
                count++;
            }
        }
        return count;
    }
}
