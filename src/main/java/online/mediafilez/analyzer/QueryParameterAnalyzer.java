package online.mediafilez.analyzer;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parses query pairs in order (including duplicates) and provides a readable explanation. */
public final class QueryParameterAnalyzer {
    public record Parameter(String name, String value, boolean hasEquals, boolean invalidEncoding) {
    }

    private record Decoded(String text, boolean invalid) {
    }

    /**
     * Uses common application/x-www-form-urlencoded decoding: '+' means space.
     * This is a convention, not a rule for every URI query string.
     */
    public List<Parameter> parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return List.of();
        }
        List<Parameter> parameters = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int equalSign = pair.indexOf('=');
            Decoded name = decode(equalSign == -1 ? pair : pair.substring(0, equalSign));
            Decoded value = decode(equalSign == -1 ? "" : pair.substring(equalSign + 1));
            parameters.add(new Parameter(name.text(), value.text(), equalSign != -1,
                    name.invalid() || value.invalid()));
        }
        return List.copyOf(parameters);
    }

    /** One line per key/value pair, placed inside the existing URL analysis section. */
    public String compactReport(URI uri) {
        List<Parameter> parameters = parse(uri.getRawQuery());
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String value = ParameterExplainer.isSensitive(parameter.name()) ? "[REDACTED]"
                    : !parameter.hasEquals() ? "(flag)"
                    : parameter.value().isEmpty() ? "(empty)" : parameter.value();
            String key = parameter.name().isEmpty() ? "(empty name)" : parameter.name();
            String description = ParameterExplainer.shortDescription(
                    parameter.name(), parameter.value(), uri.getHost());
            output.append(ReportFormatter.row("Parameter " + (i + 1),
                    key + " = " + value + " | " + description));
            if (parameter.invalidEncoding()) {
                output.append(ReportFormatter.row("  Encoding", "Invalid percent encoding"));
            }
        }
        return output.toString();
    }

    public String report(URI uri) {
        List<Parameter> parameters = parse(uri.getRawQuery());
        StringBuilder output = new StringBuilder(ReportFormatter.section("Query parameters explained"));
        if (parameters.isEmpty()) {
            return output.append(ReportFormatter.row("Parameters", "None")).toString();
        }
        output.append(ReportFormatter.row("Decoding", "UTF-8 form-style: '+' is treated as a space."));
        output.append(ReportFormatter.row("Important", "Descriptions are conventions or guesses, not server verification."));
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            boolean sensitive = ParameterExplainer.isSensitive(parameter.name());
            ParameterExplainer.Interpretation meaning = ParameterExplainer.explain(
                    parameter.name(), parameter.value(), uri.getHost());
            output.append(ReportFormatter.row("Parameter " + (i + 1),
                    parameter.name().isEmpty() ? "(empty name)" : parameter.name()));
            output.append(ReportFormatter.row("  Value", sensitive ? "[REDACTED]"
                    : !parameter.hasEquals() ? "(no '=' / no value)"
                    : parameter.value().isEmpty() ? "(empty string)" : parameter.value()));
            output.append(ReportFormatter.row("  Meaning", meaning.description()));
            output.append(ReportFormatter.row("  Confidence", meaning.confidence()));
            if (parameter.invalidEncoding()) {
                output.append(ReportFormatter.row("  Encoding warning",
                        "Malformed percent encoding; raw text was preserved."));
            }
        }
        return output.toString();
    }

    /** Redacts secrets for presentation ONLY; the original URI is preserved for HTTP/media requests. */
    public static String redactRawQuery(String rawQuery) {
        if (rawQuery == null) {
            return "None";
        }
        String[] pairs = rawQuery.split("&", -1);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                output.append('&');
            }
            String pair = pairs[i];
            int equalSign = pair.indexOf('=');
            String rawName = equalSign == -1 ? pair : pair.substring(0, equalSign);
            if (ParameterExplainer.isSensitive(decode(rawName).text())) {
                output.append(rawName).append("=[REDACTED]");
            } else {
                output.append(pair);
            }
        }
        return output.toString();
    }

    public static String redactUrl(URI uri) {
        String raw = uri.toASCIIString();
        int fragmentIndex = raw.indexOf('#');
        String fragment = fragmentIndex == -1 ? "" : raw.substring(fragmentIndex);
        if (fragment.matches("(?i).*?(access_token|id_token|token|password|api_key|code)=.*")) {
            fragment = "#[REDACTED]";
        }
        String noFragment = fragmentIndex == -1 ? raw : raw.substring(0, fragmentIndex);
        int queryIndex = noFragment.indexOf('?');
        String answer = queryIndex == -1 ? noFragment
                : noFragment.substring(0, queryIndex + 1) + redactRawQuery(uri.getRawQuery());
        // URI user-info can embed a username/password; never echo it to the console.
        if (uri.getRawUserInfo() != null) {
            int schemeEnd = answer.indexOf("://");
            int authorityEnd = answer.indexOf('/', schemeEnd + 3);
            int queryStart = answer.indexOf('?', schemeEnd + 3);
            if (authorityEnd == -1 || queryStart >= 0 && queryStart < authorityEnd) {
                authorityEnd = queryStart;
            }
            if (authorityEnd == -1) {
                authorityEnd = answer.length();
            }
            int at = answer.lastIndexOf('@', authorityEnd);
            if (schemeEnd >= 0 && at >= 0) {
                answer = answer.substring(0, schemeEnd + 3) + "[REDACTED]@" + answer.substring(at + 1);
            }
        }
        return answer + fragment;
    }

    private static Decoded decode(String raw) {
        try {
            return new Decoded(URLDecoder.decode(raw, StandardCharsets.UTF_8), false);
        } catch (IllegalArgumentException e) {
            return new Decoded(raw, true);
        }
    }
}
