package online.mediafilez.analyzer;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlAnalyzer {
    private String url;

    public UrlAnalyzer(String url) {
        this.url = url;
    }

    public String analyze() {
        if (url == null) {
            System.out.println("Invalid URL: URL must not be null");
            return null;
        }

        try {
            URI uri = parseUrl();

            if (!isValid(uri)) {
                System.out.println("Invalid URL: " + url);
                return null;
            }

            boolean valid = isValid(uri);
            String protocol = uri.getScheme();
            String host = uri.getHost();
            String filename = getFilename(uri);
            String extension = getExtension(filename);
            String type = getType(extension);


            printResult(uri, valid, protocol, host, filename, extension, type);
            return uri.toString();
        } catch (URISyntaxException e) {
            System.out.println("Invalid URL: " + e.getMessage());
        }

        return url;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');

        if (dot == -1 || dot == filename.length() - 1) {
            return "";
        }

        return filename.substring(dot + 1);
    }

    private URI parseUrl() throws URISyntaxException {
        return new URI(url);
    }

    private boolean isValid(URI uri) {
        if (!uri.isAbsolute()) {
            return false;
        }

        String protocol = uri.getScheme();

        if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
            return uri.getHost() != null;
        }

        return true;
    }

    private String getType(String extension) {
        String[] extensions = {"jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "mp3", "mp4", "avi"};
        String[] types = {"image", "image", "image", "image", "document", "document", "document", "music", "video", "video"};

        for (int i = 0; i < extensions.length; i++) {
            if (extension.equalsIgnoreCase(extensions[i])) {
                return types[i];
            }
        }

        return "unknown";
    }

    private String getFilename(URI uri) {
        String path = uri.getPath();

        if (path == null || path.isEmpty()) {
            return "";
        }

        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String getPath(URI uri) {
        return uri.getPath();
    }

    private void printResult(
            URI uri,
            boolean valid,
            String protocol,
            String host,
            String filename,
            String extension,
            String type
    ) {
        System.out.println("Domain Name: " + host);
        System.out.println("Type: " + type);
        System.out.println("Extension: " + extension);
        System.out.println("Filename: " + filename);
        System.out.println("Protocol: " + protocol);
        System.out.println("Valid: " + valid);
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
}
