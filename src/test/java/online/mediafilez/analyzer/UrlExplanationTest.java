package online.mediafilez.analyzer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UrlExplanationTest {
    private final QueryParameterAnalyzer analyzer = new QueryParameterAnalyzer();

    @Test
    void breaksDownMultiPartPublicSuffixAndSubdomains() {
        UrlExplainer.DomainParts parts = UrlExplainer.domainParts("media.storage.example.co.uk");
        assertEquals("example.co.uk", parts.registeredDomain());
        assertEquals("co.uk", parts.publicSuffix());
        assertEquals("media.storage", parts.subdomain());
        assertEquals("None", UrlExplainer.domainParts("example.co.uk").subdomain());
    }

    @Test
    void doesNotInventPublicSuffixForIpOrLocalHost() {
        assertEquals("Not applicable", UrlExplainer.domainParts("127.0.0.1").publicSuffix());
        assertEquals("Not applicable", UrlExplainer.domainParts("::1").registeredDomain());
        assertEquals("Not applicable", UrlExplainer.domainParts("localhost").subdomain());
    }

    @Test
    void decodesEncodedValuesPreservesDuplicateNamesAndEqualsSigns() {
        List<QueryParameterAnalyzer.Parameter> params = analyzer.parse(
                "q=java+language&key=a%26b%3Dc&key=two&download=1&flag&empty=&=no-name");
        assertEquals(7, params.size());
        assertEquals("java language", params.get(0).value());
        assertEquals("a&b=c", params.get(1).value());
        assertEquals("two", params.get(2).value());
        assertFalse(params.get(4).hasEquals());
        assertTrue(params.get(5).hasEquals());
        assertEquals("", params.get(5).value());
        assertEquals("", params.get(6).name());
        assertEquals("no-name", params.get(6).value());
    }

    @Test
    void malformedEncodingIsReportedWithoutCrashing() {
        QueryParameterAnalyzer.Parameter param = analyzer.parse("quality=%ZZ").get(0);
        assertEquals("%ZZ", param.value());
        assertTrue(param.invalidEncoding());
    }

    @Test
    void interpretsExamplesButUnknownKeysRemainUnknown() {
        assertEquals(ParameterExplainer.Confidence.LIKELY,
                ParameterExplainer.explain("quality", "720p", "example.com").confidence());
        assertTrue(ParameterExplainer.explain("download", "1", "example.com")
                .description().contains("enabled"));
        assertTrue(ParameterExplainer.explain("lang", "en", "example.com")
                .description().contains("English"));
        assertTrue(ParameterExplainer.explain("page", "3", "example.com")
                .description().contains("3"));
        assertTrue(ParameterExplainer.explain("sort", "asc", "example.com")
                .description().contains("ascending"));
        assertEquals(ParameterExplainer.Confidence.KNOWN,
                ParameterExplainer.explain("utm_source", "google", "example.com").confidence());
        assertEquals(ParameterExplainer.Confidence.UNKNOWN,
                ParameterExplainer.explain("unfamiliar", "x", "example.com").confidence());
        assertEquals(ParameterExplainer.Confidence.KNOWN,
                ParameterExplainer.explain("v", "hello", "www.youtube.com").confidence());
        assertEquals(ParameterExplainer.Confidence.UNKNOWN,
                ParameterExplainer.explain("v", "hello", "youtube.com.evil.test").confidence());
    }

    @Test
    void doesNotPrintTokenInQueryReportRawUrlOrUsernamePassword() {
        URI uri = URI.create("https://user:password@media.example.com/a?quality=720p&"
                + "token=secret-value&api_key=another-secret&%61ccess_token=third-secret#intro");
        String visible = QueryParameterAnalyzer.redactUrl(uri) + analyzer.report(uri);
        assertFalse(visible.contains("secret-value"));
        assertFalse(visible.contains("another-secret"));
        assertFalse(visible.contains("third-secret"));
        assertFalse(visible.contains("user:password"));
        assertTrue(visible.contains("quality=720p"));
        assertTrue(visible.contains("[REDACTED]"));
    }

    @Test
    void mainUrlOnlyModeDoesNotOpenMediaOrMakeNetworkRequests() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--url-only", "https://example.com/not-a-media-page?lang=en"});
        } finally {
            System.setOut(before);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Parameter 1"));
        assertFalse(report.contains("HTTP response"));
        assertFalse(report.contains("Media summary"));
        assertFalse(report.contains("Reading media information"));
    }

    @Test
    void detailedModeRemainsAvailableWithoutNetwork() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--url-only", "--details", "https://example.com/a?quality=720p"});
        } finally {
            System.setOut(before);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("URL components explained"));
        assertTrue(report.contains("Query parameters explained"));
        assertFalse(report.contains("HTTP response"));
    }

    @Test
    void compactModePreservesKnownYouTubeParameterAndNoLongSections() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--url-only", "https://www.youtube.com/watch?v=LIIDh-qI9oI"});
        } finally {
            System.setOut(before);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("v = LIIDh-qI9oI | YouTube video ID"));
        assertFalse(report.contains("Query parameters explained"));
        assertFalse(report.contains("URL components explained"));
    }

    @Test
    void offlineUrlAnalysisPrintsExplanationsWithoutHttpRequests() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            String original = "https://media.storage.example.co.uk/movie.mp4?"
                    + "quality=720p&download=1&lang=en&page=3&q=java&sort=asc&utm_source=google&token=hidden#preview";
            String uri = new UrlAnalyzer(original).analyze(false);
            assertEquals(original.substring(0, original.indexOf('#')), uri);
        } finally {
            System.setOut(before);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Registered domain"));
        assertTrue(report.contains("example.co.uk"));
        assertTrue(report.contains("Subdomain"));
        assertTrue(report.contains("media.storage"));
        assertFalse(report.contains("URL components explained"));
        assertFalse(report.contains("Query parameters explained"));
        assertTrue(report.contains("quality = 720p"));
        assertTrue(report.contains("Query parameter count     : 8"));
        assertTrue(report.contains("Parameter 8"));
        assertTrue(report.contains("[REDACTED]"));
        assertFalse(report.contains("token=hidden"));
        assertFalse(report.contains("HTTP response"));
    }
}
