package online.mediafilez.analyzer;

import java.util.Locale;
import java.util.Map;

// Shared formatting keeps the URL and media reports consistent.
public class ReportFormatter {
    private ReportFormatter() {
    }

    public static String section(String title) {
        return "\n" + title + "\n" + "-".repeat(60) + "\n";
    }

    public static String row(String label, Object value) {
        return String.format(Locale.ROOT, "  %-25s : %s%n", clean(label), clean(value));
    }

    public static String rows(Map<String, String> values) {
        StringBuilder report = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            report.append(row(entry.getKey(), entry.getValue()));
        }
        return report.toString();
    }

    private static String clean(Object value) {
        if (value == null || value.toString().isBlank()) {
            return "Unknown";
        }
        // Metadata comes from outside the program. Keep it on one console line.
        return value.toString().replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ");
    }

    public static String number(double value, String unit) {
        if (!Double.isFinite(value) || value <= 0) {
            return "Unknown";
        }
        return String.format(Locale.ROOT, "%.2f %s", value, unit);
    }

    public static String bitrate(long bitsPerSecond) {
        return number(bitsPerSecond / 1000.0, "kb/s");
    }

    public static String size(long bytes) {
        if (bytes < 0) {
            return "Unknown";
        }
        return String.format(Locale.ROOT, "%.2f MiB (%d bytes)", bytes / 1_048_576.0, bytes);
    }

    public static String duration(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return "Unknown";
        }
        long milliseconds = Math.round(seconds * 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d (%.3f s)",
                milliseconds / 3_600_000, milliseconds / 60_000 % 60,
                milliseconds / 1000 % 60, milliseconds % 1000, seconds);
    }

    public static String ratio(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            return "Unknown";
        }
        long a = numerator;
        long b = denominator;
        while (b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return numerator / a + ":" + denominator / a;
    }
}
