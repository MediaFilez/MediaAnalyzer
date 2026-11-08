package online.mediafilez.analyzer;

import java.util.Locale;
import java.util.Set;

/** Heuristics, NOT a universal specification for query parameter semantics. */
public final class ParameterExplainer {
    private ParameterExplainer() {
    }

    public enum Confidence {
        KNOWN("Known convention"), LIKELY("Likely (site-dependent)"), UNKNOWN("Unknown");
        private final String label;
        Confidence(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Interpretation(String description, Confidence confidence) {
    }

    // Keep sensitive query values out of console reports. These names are not exhaustive.
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "token", "access_token", "id_token", "refresh_token", "auth_token",
            "authorization", "api_key", "apikey", "client_secret", "secret",
            "password", "passwd", "session", "sessionid", "session_id",
            "signature", "sig", "oauth_token", "auth_code", "code_verifier"
    );

    public static boolean isSensitive(String parameterName) {
        return parameterName != null && SENSITIVE_NAMES.contains(parameterName.trim().toLowerCase(Locale.ROOT));
    }

    public static Interpretation explain(String name, String value, String host) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String val = value == null ? "" : value.trim();
        String normalizedValue = val.toLowerCase(Locale.ROOT);

        if (isSensitive(key)) {
            return new Interpretation("Potential authentication, session, or API secret; value hidden from the report.",
                    Confidence.LIKELY);
        }
        return switch (key) {
            case "quality", "resolution" -> new Interpretation(
                    normalizedValue.matches("[0-9]{3,4}p")
                            ? "Likely requests a video quality around " + normalizedValue
                                    + "; the actual stream quality depends on the server."
                            : "Likely selects a media quality or resolution; exact values are site-specific.",
                    Confidence.LIKELY);
            case "download" -> new Interpretation(switch (normalizedValue) {
                case "1", "true", "yes" -> "May request or enable download behavior (enabled value).";
                case "0", "false", "no" -> "May disable download behavior (disabled value).";
                default -> "May control download behavior; accepted values depend on the server.";
            }, Confidence.LIKELY);
            case "lang", "language", "locale" -> new Interpretation(
                    "Likely requests a language/locale" + switch (normalizedValue) {
                        case "en" -> " (English).";
                        case "ar" -> " (Arabic).";
                        default -> " (" + (val.isBlank() ? "not specified" : val) + ").";
                    }, Confidence.LIKELY);
            case "page" -> new Interpretation(
                    normalizedValue.matches("[1-9][0-9]*") ? "Likely requests page number " + val + "."
                            : "Likely selects a page in paginated results; page value may be invalid.",
                    Confidence.LIKELY);
            case "q", "query", "search" -> new Interpretation(
                    "Likely contains the user's search terms.", Confidence.LIKELY);
            case "sort", "order" -> new Interpretation(switch (normalizedValue) {
                case "asc" -> "Likely requests ascending order.";
                case "desc" -> "Likely requests descending order.";
                default -> "Likely controls sorting; available ordering options depend on the website.";
            }, Confidence.LIKELY);
            case "utm_source" -> new Interpretation(
                    "UTM campaign-tagging convention: identifies the traffic source (e.g., google).",
                    Confidence.KNOWN);
            case "utm_medium" -> new Interpretation(
                    "UTM convention: identifies the marketing medium (e.g., email or cpc).", Confidence.KNOWN);
            case "utm_campaign" -> new Interpretation(
                    "UTM convention: identifies the marketing campaign.", Confidence.KNOWN);
            case "utm_term" -> new Interpretation(
                    "UTM convention: often identifies paid search keywords.", Confidence.KNOWN);
            case "utm_content" -> new Interpretation(
                    "UTM convention: distinguishes ads/links in a campaign.", Confidence.KNOWN);
            case "v" -> isYouTube(host)
                    ? new Interpretation("On a standard YouTube watch URL, identifies the video.", Confidence.KNOWN)
                    : new Interpretation("Could identify a version or resource; meaning is site-specific.",
                            Confidence.UNKNOWN);
            default -> new Interpretation("No general meaning is known; the website determines what it does.",
                    Confidence.UNKNOWN);
        };
    }

    /** Short hints for the default URL report; meanings are not server-verified. */
    public static String shortDescription(String name, String value, String host) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String val = value == null ? "" : value.trim();
        String lowerVal = val.toLowerCase(Locale.ROOT);
        if (isSensitive(key)) {
            return "Sensitive value";
        }
        return switch (key) {
            case "quality", "resolution" -> "Media quality (possible)";
            case "download" -> switch (lowerVal) {
                case "1", "true", "yes" -> "Download option: on (possible)";
                case "0", "false", "no" -> "Download option: off (possible)";
                default -> "Download option (possible)";
            };
            case "lang", "language", "locale" -> switch (lowerVal) {
                case "en" -> "Language: English (possible)";
                case "ar" -> "Language: Arabic (possible)";
                default -> "Language selection (possible)";
            };
            case "page" -> "Page number (possible)";
            case "q", "query", "search" -> "Search terms (possible)";
            case "sort", "order" -> switch (lowerVal) {
                case "asc" -> "Ascending sort (possible)";
                case "desc" -> "Descending sort (possible)";
                default -> "Sort order (possible)";
            };
            case "utm_source" -> "Campaign traffic source";
            case "utm_medium" -> "Campaign medium";
            case "utm_campaign" -> "Campaign name";
            case "utm_term" -> "Campaign search term";
            case "utm_content" -> "Campaign content";
            case "v" -> isYouTube(host) ? "YouTube video ID" : "Site-specific meaning";
            default -> "Site-specific meaning";
        };
    }

    private static boolean isYouTube(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("youtube.com") || lower.endsWith(".youtube.com");
    }
}
