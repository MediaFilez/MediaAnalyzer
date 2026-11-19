package online.mediafilez.analyzer;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        String input = null;
        boolean urlOnly = false;
        boolean details = false;

        // Options may be passed in any order. Existing one-URL and interactive modes still work.
        for (String argument : args) {
            if ("--url-only".equals(argument)) {
                urlOnly = true;
            } else if ("--details".equals(argument)) {
                details = true;
            } else if (input == null && !argument.startsWith("--")) {
                input = argument;
            } else {
                System.err.println("Usage: java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar "
                        + "[--url-only] [--details] \"<URL>\"");
                System.exit(1);
                return;
            }
        }

        if (input == null) {
            System.out.print(urlOnly ? "Enter a URL: " : "Enter a media URL: ");
            Scanner scanner = new Scanner(System.in);
            input = scanner.hasNextLine() ? scanner.nextLine() : "";

            scanner.close();
        }

        UrlAnalyzer urlAnalyzer = new UrlAnalyzer(input);
        String url = urlAnalyzer.analyze(!urlOnly, details);
        if (url == null) {
            System.exit(1);
            return;
        }
        if (urlOnly) {
            return;
        }

        // An HTML document or JSON API response is not a direct media resource.
        // Do not pass ordinary webpages (such as YouTube watch URLs) to FFmpeg.
        if (urlAnalyzer.isWebPage()) {
            System.out.println("\nMedia analysis skipped: this URL returns a webpage or data, not a direct media file.");
            return;
        }

        // Keep the original FFmpeg-based analysis for direct videos/audio and unknown content types.
        System.out.println("\nReading media information...");
        MediaAnalyzer mediaAnalyzer = new MediaAnalyzer(url);
        MediaInfo info = mediaAnalyzer.analyze();
        if (info == null) {
            System.exit(1);
            return;
        }
        System.out.print(info);
    }
}
