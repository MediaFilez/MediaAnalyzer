package online.mediafilez.analyzer;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        String input;

        if (args.length == 0) {
            System.out.print("Enter a media URL: ");
            Scanner scanner = new Scanner(System.in);
            input = scanner.hasNextLine() ? scanner.nextLine() : "";
        } else if (args.length == 1) {
            input = args[0];
        } else {
            System.err.println("Usage: java -jar target/media-analyzer-0.1.0-SNAPSHOT.jar \"<media_URL>\"");
            System.exit(1);
            return;
        }

        // Validate and print the URL before opening its media content.
        UrlAnalyzer urlAnalyzer = new UrlAnalyzer(input);
        String url = urlAnalyzer.analyze();

        if (url == null) {
            System.exit(1);
            return;
        }

        // Try the content even when the URL has no recognizable extension.
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
