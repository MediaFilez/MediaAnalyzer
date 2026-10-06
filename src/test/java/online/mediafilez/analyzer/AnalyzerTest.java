package online.mediafilez.analyzer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void startServer() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void mainCombinesBothReportsOnceAndKeepsAllStreams() {
        Main.main(new String[]{baseUrl + "/sample.mp4?download=1#preview"});

        String report = output.toString(StandardCharsets.UTF_8);
        assertEquals(1, occurrences(report, "URL analysis"));
        assertEquals(1, occurrences(report, "Media summary"));
        assertTrue(report.indexOf("HTTP response") < report.indexOf("Media summary"));
        assertTrue(report.contains("Resolution                : 96 x 64 px"));
        assertTrue(report.contains("Video streams             : 1"));
        assertTrue(report.contains("Audio streams             : 2"));
        assertTrue(report.contains("Subtitle streams          : 1"));
        assertTrue(report.contains("48000 Hz"));
        assertTrue(report.contains("yuv420p"));
        assertTrue(report.contains("8 bits/component"));
        assertTrue(report.contains("bt709"));
        assertTrue(report.contains("10 (reported)"));
        assertTrue(report.contains("Test fixture"));
        assertTrue(report.contains("eng"));
        assertTrue(report.contains("ara"));
        assertFalse(report.contains("MediaInfo{"));
        assertFalse(report.contains("null"));
    }

    @Test
    void mainAlsoReadsFromStandardInput() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream((baseUrl + "/sample.mp4\n").getBytes(StandardCharsets.UTF_8)));
            Main.main(new String[0]);
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("Media summary"));
        } finally {
            System.setIn(originalIn);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "video.mp4", "http://", "https://host:99999/video.mp4",
            "https://host:0/video.mp4", "https://host/bad space.mp4", "ftp://host/video.mp4", "file:///video.mp4"})
    void rejectsInvalidUrlsWithoutNetworkRequests(String url) {
        assertNull(new UrlAnalyzer(url).analyze());
        assertEquals(0, requests.get());
    }

    @Test
    void decodesFilenameWithoutConfusingQueryFragmentOrEncodedSlash() {
        String url = baseUrl + "/folder/name%2Fclip%20one.MP4?key=a%26b&key=2#preview";
        assertEquals(url.substring(0, url.indexOf('#')), new UrlAnalyzer("  " + url + "  ").analyze());
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Filename                  : name/clip one.MP4"));
        assertTrue(report.contains("Extension                 : mp4"));
        assertTrue(report.contains("Query parameter count     : 2"));
        assertTrue(report.contains("Fragment (raw)            : preview"));
    }

    @Test
    void reportsRedirectAndAnalyzesContentWithoutAnExtension() {
        Main.main(new String[]{baseUrl + "/redirect"});
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Type from extension       : unknown"));
        assertTrue(report.contains("Redirect count            : 1"));
        assertTrue(report.contains("Final URL                 : " + baseUrl + "/sample.mp4"));
        assertTrue(report.contains("Media summary"));
    }

    @Test
    void fallsBackToRangeGetAndReportsFullFileSize() throws IOException {
        assertNotNull(new UrlAnalyzer(baseUrl + "/nohead.mp4").analyze());
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Request method            : GET"));
        assertTrue(report.contains("Status code               : 206"));
        assertTrue(report.contains("(" + resource("sample.mp4").length + " bytes)"));
    }

    @Test
    void mediaStillWorksWhenTheHeadCheckFails() {
        Main.main(new String[]{baseUrl + "/head-error.mp4"});
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Status code               : 500"));
        assertTrue(report.contains("Media summary"));
    }

    @Test
    void audioOnlyDoesNotInventVideoInformation() {
        MediaInfo info = new MediaAnalyzer(baseUrl + "/audio.m4a").analyze();
        assertNotNull(info);
        assertEquals(1, info.getStreams().size());
        assertEquals("audio", info.getStreams().get(0).getType());
        assertTrue(info.toString().contains("Video streams             : 0"));
        assertFalse(info.toString().contains("Resolution"));
    }

    @Test
    void videoOnlyKeepsRotationAfterNativeResourcesAreReleased() {
        MediaInfo info = new MediaAnalyzer(baseUrl + "/rotated.mp4").analyze();
        assertNotNull(info);
        assertEquals(1, info.getStreams().size());
        assertTrue(info.toString().contains("Audio streams             : 0"));
        assertTrue(info.toString().contains("degrees"));
        assertFalse(info.toString().contains("Rotation                  : Not reported"));
    }

    @Test
    void unavailableFrameCountIsClearlyEstimatedOrUnknown() {
        MediaInfo info = new MediaAnalyzer(baseUrl + "/estimated.mkv").analyze();
        assertNotNull(info);
        String report = info.toString();
        assertTrue(report.contains("Frame count               : Unknown")
                || report.contains("Frame count               : 10 (estimated)"));
        assertFalse(report.contains("(reported)"));
    }

    @Test
    void nonMediaAndMissingResourcesFailWithoutReturningFakeInfo() {
        assertNull(new MediaAnalyzer(baseUrl + "/not-video.mp4").analyze());
        assertNull(new MediaAnalyzer(baseUrl + "/missing").analyze());
    }

    @Test
    void formattingHandlesUnknownValuesAndIgnoresSystemLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.50 kb/s", ReportFormatter.bitrate(12500));
            assertEquals("00:00:01.250 (1.250 s)", ReportFormatter.duration(1.25));
            assertEquals("Unknown", ReportFormatter.duration(Double.NaN));
            assertEquals("Unknown", ReportFormatter.bitrate(0));
            assertEquals("16:9", ReportFormatter.ratio(1920, 1080));
            String row = ReportFormatter.row("title", "first\nsecond\u001b[31m");
            assertFalse(row.contains("\u001b"));
            assertEquals(1, occurrences(row, System.lineSeparator()));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private void serve(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            // Even FFmpeg's separate request must preserve the query and omit the fragment.
            if (exchange.getRequestURI().toString().contains("#")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            boolean head = "HEAD".equals(exchange.getRequestMethod());
            if ("/redirect".equals(path)) {
                exchange.getResponseHeaders().set("Location", baseUrl + "/sample.mp4");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            if ("/missing".equals(path) || head && "/nohead.mp4".equals(path)
                    || head && "/head-error.mp4".equals(path)) {
                int status = "/missing".equals(path) ? 404 : "/nohead.mp4".equals(path) ? 405 : 500;
                exchange.sendResponseHeaders(status, -1);
                return;
            }

            String fixture = switch (path) {
                case "/audio.m4a" -> "audio.m4a";
                case "/rotated.mp4" -> "rotated.mp4";
                case "/estimated.mkv" -> "estimated.mkv";
                default -> "sample.mp4";
            };
            byte[] body = "/not-video.mp4".equals(path)
                    ? "This is not a video.".getBytes(StandardCharsets.UTF_8) : resource(fixture);
            String contentType = switch (path) {
                case "/not-video.mp4" -> "text/plain";
                case "/audio.m4a" -> "audio/mp4";
                case "/estimated.mkv" -> "video/x-matroska";
                default -> "video/mp4";
            };
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
            if (head) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            int start = 0;
            int end = body.length - 1;
            int status = 200;
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-", -1);
                start = Integer.parseInt(parts[0]);
                if (!parts[1].isEmpty()) {
                    end = Math.min(end, Integer.parseInt(parts[1]));
                }
                status = 206;
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + body.length);
            }
            int length = end - start + 1;
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(length));
            exchange.sendResponseHeaders(status, length);
            exchange.getResponseBody().write(body, start, length);
        }
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/" + name)) {
            assertNotNull(input, "Missing fixture: " + name);
            return input.readAllBytes();
        }
    }

    private int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
