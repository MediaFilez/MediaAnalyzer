# Media Analyzer

A small Java command-line project for [MediaFilez](https://github.com/MediaFilez).
It reads one URL from standard input and prints its host, media type, extension,
filename, protocol, validity, and original value.

## Build and run

Install JDK 17 or newer and Apache Maven, then run these commands from the project directory:

```sh
mvn package
java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar
```

Enter a URL and press Enter. For `https://example.com/video.mp4`, the output is:

```text
Domain Name: example.com
Type: video
Extension: mp4
Filename: video.mp4
Protocol: https
Valid: true
URL: https://example.com/video.mp4
```

You can also build without Maven:

```sh
javac --release 17 -d target/classes src/main/java/online/mediafilez/analyzer/Main.java src/main/java/online/mediafilez/analyzer/UrlAnalyzer.java
java -cp target/classes online.mediafilez.analyzer.Main
```

## Supported extensions

| Type | Extensions |
| --- | --- |
| image | jpg, jpeg, png, gif |
| document | pdf, doc, docx |
| music | mp3 |
| video | mp4, avi |

Matching ignores case. Unrecognized extensions print `Unknown type`.
The filename and extension come from the URI path, so query parameters and
fragments are excluded. Java's URI parser decodes percent-encoded path characters.

`Valid` means the URI is absolute. HTTP and HTTPS URLs must also have a parsed
host. Relative paths print `Valid: false`; malformed URI syntax prints
`Invalid URL` with the parser's reason. Inputs without a path have an empty
filename and extension.

The program does not make network requests, check whether a URL exists, inspect
file contents, or detect platforms. The reported media type is based only on the
extension.

## Source

Both classes live in `online.mediafilez.analyzer`:

- `Main` reads the input and runs the analyzer.
- `UrlAnalyzer.analyze()` prints the analysis and returns the original URL.

Keep changes small and include an input and expected output when reporting a bug.

## License

Released under the [MIT License](LICENSE).
