package online.mediafilez.analyzer;

import java.util.LinkedHashMap;
import java.util.Map;

public class MediaStreamInfo {
    private final int index;
    private final String type;
    private final Map<String, String> details = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public MediaStreamInfo(int index, String type) {
        this.index = index;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void addDetail(String label, String value) {
        details.put(label, value);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata.clear();
        this.metadata.putAll(metadata);
    }

    @Override
    public String toString() {
        StringBuilder report = new StringBuilder(ReportFormatter.section(type + " stream #" + index));
        report.append(ReportFormatter.rows(details));
        if (!metadata.isEmpty()) {
            report.append("  Stream metadata:\n");
            report.append(ReportFormatter.rows(metadata));
        }
        return report.toString();
    }
}
