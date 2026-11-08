package online.mediafilez.analyzer;

import com.google.common.net.InternetDomainName;

import java.net.URI;
import java.util.Locale;

/** Explains URI components and separates a hostname using the Public Suffix List. */
public final class UrlExplainer {
    private UrlExplainer() {
    }

    public record DomainParts(String host, String registeredDomain, String publicSuffix, String subdomain) {
    }

    /**
     * A "registered domain" here is the PSL-based top private domain (eTLD+1),
     * not proof of actual ownership or domain registration.
     */
    public static DomainParts domainParts(String host) {
        if (host == null || host.isBlank()) {
            return new DomainParts("Unknown", "Unknown", "Unknown", "Unknown");
        }
        // Numeric addresses are not DNS names, and localhost/unknown TLDs have no public suffix.
        if (host.contains(":") || host.matches("[0-9.]+") || host.equalsIgnoreCase("localhost")) {
            return new DomainParts(host, "Not applicable", "Not applicable", "Not applicable");
        }
        try {
            InternetDomainName domain = InternetDomainName.from(host);
            if (!domain.hasPublicSuffix()) {
                return new DomainParts(host, "Unknown", "Unknown", "Unknown");
            }
            String suffix = domain.publicSuffix().toString();
            if (!domain.isUnderPublicSuffix()) {
                return new DomainParts(host, "Not applicable", suffix, "Not applicable");
            }
            String registered = domain.topPrivateDomain().toString();
            String normalizedHost = domain.toString();
            String subdomain = normalizedHost.equals(registered)
                    ? "None"
                    : normalizedHost.substring(0, normalizedHost.length() - registered.length() - 1);
            return new DomainParts(host, registered, suffix, subdomain);
        } catch (IllegalArgumentException e) {
            // An unusual hostname should not prevent the rest of the URL analysis.
            return new DomainParts(host, "Unknown", "Unknown", "Unknown");
        }
    }

    public static String explain(URI uri, DomainParts domain) {
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        String protocol = uri.getScheme().toUpperCase(Locale.ROOT);
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();

        StringBuilder report = new StringBuilder(ReportFormatter.section("URL components explained"));
        report.append(ReportFormatter.row("Protocol", https
                ? "HTTPS uses HTTP over TLS to encrypt traffic; it does not guarantee a trustworthy website."
                : "HTTP transfers web requests and responses without TLS encryption."));
        report.append(ReportFormatter.row("Host", "The network hostname (or IP) to contact: " + domain.host()));
        report.append(ReportFormatter.row("Registered domain", "PSL-based top private domain (eTLD+1): "
                + domain.registeredDomain() + "; this is not a check of ownership."));
        report.append(ReportFormatter.row("Public suffix", "The suffix controlled by a registry or provider: "
                + domain.publicSuffix() + " (for example, co.uk)."));
        report.append(ReportFormatter.row("Subdomain", "Host labels preceding the registered domain: "
                + domain.subdomain() + "; their purpose is site-specific."));
        report.append(ReportFormatter.row("Port", "Network service port " + port
                + (uri.getPort() == -1 ? " (default inferred for " + protocol + ")." : " (explicit in the URL).")));
        report.append(ReportFormatter.row("Path", "Identifies the requested resource path; its segments are interpreted by the server."));
        String rawPath = uri.getRawPath();
        int segmentNumber = 0;
        if (rawPath != null) {
            // Split the raw path BEFORE decoding so an encoded slash stays inside its segment.
            for (String segment : rawPath.split("/", -1)) {
                if (segment.isEmpty()) {
                    continue;
                }
                segmentNumber++;
                String decodedSegment = URI.create("/" + segment).getPath().substring(1);
                report.append(ReportFormatter.row("Path segment " + segmentNumber,
                        decodedSegment + " - ordered path component; its purpose is server-defined."));
            }
        }
        if (segmentNumber == 0) {
            report.append(ReportFormatter.row("Path segments", "None (root or empty path)."));
        }
        report.append(ReportFormatter.row("Filename", "Last path segment, if any; not proof that the resource is a file."));
        report.append(ReportFormatter.row("Extension", "Filename suffix used as a type hint; the actual content can differ."));
        report.append(ReportFormatter.row("Query", "Optional key/value data after '?'; meanings depend on the website."));
        report.append(ReportFormatter.row("Fragment", "Optional reference after '#'; not sent in an HTTP request."));
        if (uri.getRawUserInfo() != null) {
            report.append(ReportFormatter.row("User info", "Credentials/user-info in the authority are hidden; avoid URLs with passwords."));
        }
        return report.toString();
    }
}
