package online.mediafilez.analyzer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class UrlAnalyzer {
    private final String url;

    public UrlAnalyzer(String url) {
        this.url = url;
    }

    public String analyze() {
        if (url == null || url.isBlank()) {
            System.err.println("Invalid URL: URL must not be empty or blank.");
            return null;
        }

        try {
            URI uri = new URI(url.strip());
            if (!isValid(uri)) {
                System.err.println("Invalid URL: enter an HTTP or HTTPS URL with a host and a valid port.");
                return null;
            }

            printResult(uri);
            printHttpResult(uri);
            // A fragment identifies a position in a resource; it is not sent to the server.
            String mediaUrl = uri.toASCIIString();
            int fragment = mediaUrl.indexOf('#');
            return fragment == -1 ? mediaUrl : mediaUrl.substring(0, fragment);
        } catch (URISyntaxException e) {
            System.err.println("Invalid URL: " + e.getMessage());
            return null;
        }
    }

    private boolean isValid(URI uri) {
        String protocol = uri.getScheme();
        return ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                && uri.getHost() != null
                && (uri.getPort() == -1 || uri.getPort() >= 1 && uri.getPort() <= 65535);
    }

    private String getFilename(URI uri) {
        // Split before decoding, so an encoded slash stays part of the filename.
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            return "";
        }
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return URI.create("/" + filename).getPath().substring(1);
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String getType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff", "avif", "svg" -> "image";
            case "pdf", "doc", "docx", "txt", "odt" -> "document";
            case "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "aiff" -> "audio";
            case "mp4", "avi", "mkv", "mov", "webm", "m4v", "mpeg", "mpg", "ts", "flv", "wmv" -> "video";
            case "m3u8", "mpd" -> "streaming playlist";
            default -> "unknown";
        };
    }

    private void printResult(URI uri) {
        String filename = getFilename(uri);
        String extension = getExtension(filename);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();

        StringBuilder report = new StringBuilder(ReportFormatter.section("URL analysis"));
        report.append(ReportFormatter.row("URL", uri.toASCIIString()));
        report.append(ReportFormatter.row("Valid HTTP(S) syntax", "Yes"));
        report.append(ReportFormatter.row("Domain name", uri.getHost()));
        report.append(ReportFormatter.row("Protocol", uri.getScheme().toLowerCase(Locale.ROOT)));
        report.append(ReportFormatter.row("Uses HTTPS", https ? "Yes" : "No"));
        report.append(ReportFormatter.row("Port", port));
        report.append(ReportFormatter.row("Path", uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath()));
        report.append(ReportFormatter.row("Filename", filename.isEmpty() ? "None" : filename));
        report.append(ReportFormatter.row("Extension", extension.isEmpty() ? "None" : extension));
        report.append(ReportFormatter.row("Type from extension", getType(extension)));
        report.append(ReportFormatter.row("Query (raw)", uri.getRawQuery() == null ? "None" : uri.getRawQuery()));
        report.append(ReportFormatter.row("Query parameter count", getQueryParameterCount(uri)));
        report.append(ReportFormatter.row("Fragment (raw)", uri.getRawFragment() == null ? "None" : uri.getRawFragment()));
        System.out.print(report);
    }

    private int getQueryParameterCount(URI uri) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String parameter : uri.getRawQuery().split("&")) {
            if (!parameter.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void printHttpResult(URI uri) {
        System.out.print(ReportFormatter.section("HTTP response"));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            long started = System.nanoTime();
            HttpResponse<InputStream> response = sendRequest(client, uri, "HEAD");
            // Some servers only allow GET. Ask for one byte and close the body.
            if (response.statusCode() == 405 || response.statusCode() == 501) {
                response = sendRequest(client, uri, "GET");
            }

            System.out.print(ReportFormatter.row("Request method", response.request().method()));
            System.out.print(ReportFormatter.row("Status code", response.statusCode()));
            System.out.print(ReportFormatter.row("Final URL", response.uri()));
            System.out.print(ReportFormatter.row("Redirect count", getRedirectCount(response)));
            System.out.print(ReportFormatter.row("HTTP version", response.version()));
            System.out.print(ReportFormatter.row("Header request time", ReportFormatter.number(
                    (System.nanoTime() - started) / 1_000_000.0, "ms")));
            System.out.print(ReportFormatter.row("Content type", header(response, "Content-Type")));
            System.out.print(ReportFormatter.row("Size from HTTP", ReportFormatter.size(getContentSize(response))));
            System.out.print(ReportFormatter.row("Accept ranges", header(response, "Accept-Ranges")));
            System.out.print(ReportFormatter.row("Content encoding", header(response, "Content-Encoding")));
            System.out.print(ReportFormatter.row("Content disposition", header(response, "Content-Disposition")));
            System.out.print(ReportFormatter.row("Last modified", header(response, "Last-Modified")));
            System.out.print(ReportFormatter.row("ETag", header(response, "ETag")));
            System.out.print(ReportFormatter.row("Server", header(response, "Server")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.print(ReportFormatter.row("HTTP check", "Interrupted"));
        } catch (IOException | IllegalArgumentException e) {
            System.out.print(ReportFormatter.row("HTTP check failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            System.out.println("  Media analysis will still be attempted.");
        }
    }

    private HttpResponse<InputStream> sendRequest(HttpClient client, URI uri, String method)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "MediaAnalyzer/0.1")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if ("GET".equals(method)) {
            request.header("Range", "bytes=0-0");
        }

        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        // Reading headers is enough; do not download the response body here.
        try (InputStream body = response.body()) {
            return response;
        }
    }

    private String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("Unknown");
    }

    private long getContentSize(HttpResponse<?> response) {
        String size = header(response, "Content-Length");
        if (response.statusCode() == 206) {
            // Content-Length is only the range length, not the full file size.
            String range = header(response, "Content-Range");
            size = range.substring(range.lastIndexOf('/') + 1);
        }
        try {
            return Long.parseLong(size);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int getRedirectCount(HttpResponse<?> response) {
        int count = 0;
        while (response.previousResponse().isPresent()) {
            response = response.previousResponse().get();
            count++;
        }
        return count;
    }

}
