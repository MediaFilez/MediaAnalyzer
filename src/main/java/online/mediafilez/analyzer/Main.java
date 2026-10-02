package online.mediafilez.analyzer;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Main <media_file_URL>");
            System.exit(1);
        }

        MediaAnalyzer analyzer = new MediaAnalyzer(args[0]);
        MediaInfo info = analyzer.analyze();
    
        if (info != null) {
            System.out.println(info);
        }
    }
}
