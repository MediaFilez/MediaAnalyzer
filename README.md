# Media Analyzer

A Java command-line tool for [MediaFilez](https://github.com/MediaFilez).
Give it a direct media URL to inspect the URL, HTTP response, and file contents.

## Build and run

Requires JDK 17 or newer and Apache Maven. Run these commands from the project directory.

On Windows x64:

```powershell
mvn package "-Djavacpp.platform=windows-x86_64"
java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar "https://i.imgur.com/n3euJ1C.mp4"
```

To inspect any HTTP(S) URL **without opening a network connection or inspecting media**, use:

```powershell
java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar --url-only "https://media.storage.example.co.uk/movie.mp4?quality=720p&download=1"
```

The default report is compact: one URL analysis section with concise parameter hints.
For the previous full "URL components explained" and "Query parameters explained"
sections, optionally use `--details` (also works with `--url-only`):

```powershell
java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar --url-only --details "https://www.youtube.com/watch?v=LIIDh-qI9oI"
```

When HTTP explicitly reports an HTML webpage or JSON/text document (for example a
YouTube watch page), the program prints the URL and HTTP information and skips FFmpeg.
Direct media URLs, and URLs whose content type is unknown, keep the original media
analysis behavior. `--url-only` always skips HTTP and media requests entirely.

Run without an argument to enter a URL at the prompt:

```powershell
java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar
```

The build copies runtime libraries to `target/lib`. Keep that directory beside the
JAR when moving the program. JavaCV includes FFmpeg, so you don't need a separate
FFmpeg installation.

On other systems, use `mvn package` to include the platforms supported by JavaCV,
or set `javacpp.platform` to your platform.

## Report

The URL section shows the host, protocol, port, path, filename, extension, query
parameters, and fragment. The compact default prints a single short line for each
parameter. `--details` enables the previous expanded component and query reports,
which interpret query parameters individually (including repeated keys, percent-encoded data,
and empty values). These interpretations are **heuristics**, not a guarantee of
what the website actually does. They are marked "Known convention", "Likely",
or "Unknown". The `download=1` and `quality=720p` meanings are **site-specific**.
The HTTP section reports the status, final URL,
redirects, content type, size, request time, and available response headers.

Domain breakdown (registered domain, public suffix, subdomain) uses Google's
Guava library and the Public Suffix List. "Registered domain" means an
approximate eTLD+1 based on that list; it does **not** verify registration or
ownership. IP addresses and localhost do not have a public suffix.

For safety, common secret parameter names such as `token`, `api_key`,
`access_token`, and `password` are redacted in the URL, query and HTTP final-URL
console output. The full original URL is still passed to HTTP and FFmpeg in the
regular mode. Do not share logs containing credentials inside URL paths or
unrecognized secret parameters. The query parser follows form-style decoding:
`+` means a space, although some sites use a literal plus sign.

Media information comes from FFmpeg. The report includes the container, duration,
bitrate, size, chapter count, and every stream, including additional audio tracks
and subtitles.

Video details include resolution, aspect ratios, average and nominal frame rates,
frame count, pixel format, bit depth, color information, and rotation. Audio
details include sample rate, channel layout, sample format, and reported bit depth.
File and stream metadata appear in their own sections.

Missing values print `Unknown`. Calculated frame counts are marked `estimated`.
Sizes use `MiB`; bitrates use `kb/s`.

The filename extension is only a hint. FFmpeg reads the actual content, so URLs
without extensions can work too. Use a direct media URL: the program doesn't
extract videos from webpages.

The HTTP check starts with `HEAD`. If the server returns 405 or 501, it retries
with a range `GET` and closes the body without reading it. Query parameters stay
intact; fragments are removed before passing the URL to FFmpeg.

Dimensions and aspect ratios describe stored pixels before rotation. Average
and nominal frame rates are reported separately. The program reads technical
metadata, but doesn't check every frame for corruption. Live streams may omit
duration or size. Network timeouts apply to individual operations, not the
total analysis time.

## Code

- `Main.java` reads input, runs both analyzers, and prints the media report once.
- `UrlAnalyzer.java` validates the URL, orchestrates the URL report, and checks HTTP.
- `UrlExplainer.java` uses Guava to split the domain, and explains URL components.
- `QueryParameterAnalyzer.java` parses/query-decodes every key/value pair in order.
- `ParameterExplainer.java` provides cautious interpretations and secret-name redaction.
- `MediaAnalyzer.java` opens the media and reads container and stream properties.
- `MediaInfo.java` stores the file summary, streams, and file metadata.
- `MediaStreamInfo.java` stores one stream's details and metadata.
- `ReportFormatter.java` formats report sections, rows, and units.

Start with `Main.java` to follow the program. Add video properties in
`addVideoDetails()` or audio properties in `addAudioDetails()`. Change the
printed layout in `ReportFormatter`.

Details use `LinkedHashMap` to preserve their display order. FFmpeg data is copied
into Java values before the grabber closes. `try-with-resources` releases native
resources even when analysis fails.

## Tests

```powershell
mvn test "-Djavacpp.platform=windows-x86_64"
```

Tests use a local HTTP server and small generated media files. They cover combined
output, URL parsing, redirects, HEAD fallback, multiple tracks, audio-only files,
rotation, missing values, and failed requests. `UrlExplanationTest.java` also covers Public Suffix List
domains, encoded and duplicate query values, example meanings, redaction, and
offline URL-only analysis. Tests don't depend on an external media server.

Fixture generation commands are in [src/test/resources/README.md](src/test/resources/README.md).

## License

[MIT](LICENSE).
