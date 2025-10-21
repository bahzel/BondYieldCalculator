package yield;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.regex.*;
import java.util.zip.*;

public class CsvDownloadService {
    private static final String PAGE_URL = "https://www.gettex.de/handel/delayed-data/pretrade-data/";
    private static final Pattern LINK_PATTERN = Pattern.compile(
        "<a href=\"(https://erdk\\.bayerische-boerse\\.de/\\?u=edd-MUNCD&amp;p=public&amp;path=/pretrade/pretrade\\.[^\"]+?mund\\.csv\\.gz)\"", Pattern.CASE_INSENSITIVE);

    public String fetchLatestCsvUrl() throws IOException {
        URL url = new URL(PAGE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = LINK_PATTERN.matcher(line);
                if (matcher.find()) {
                    String rawUrl = matcher.group(1);
                    // HTML Entities ersetzen
                    return rawUrl.replace("&amp;", "&");
                }
            }
        }
        throw new IOException("No CSV download link found on page");
    }

    public Path downloadLatestCsv(String targetPath) throws IOException {
        String csvUrl = fetchLatestCsvUrl();
        URL url = new URL(csvUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = conn.getInputStream()) {
            Path outPath = Paths.get(targetPath);
            Files.copy(in, outPath, StandardCopyOption.REPLACE_EXISTING);
            return outPath;
        }
    }

    /**
     * Downloads the latest CSV file from gettex.de and extracts it
     * @return Path to the extracted CSV file
     */
    public String downloadLatestCsvFile() throws IOException {
        System.out.println("Fetching latest CSV file from gettex.de...");

        // Create temp file for the gzipped download
        Path tempGzFile = Files.createTempFile("pretrade-", ".csv.gz");

        // Download the gzipped file
        downloadLatestCsv(tempGzFile.toString());
        System.out.println("Downloaded CSV file (gzipped)");

        // Extract the CSV file
        Path tempCsvFile = Files.createTempFile("pretrade-", ".csv");
        try (GZIPInputStream gzipIn = new GZIPInputStream(new FileInputStream(tempGzFile.toFile()));
             FileOutputStream out = new FileOutputStream(tempCsvFile.toFile())) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }

        // Delete the gzipped file
        Files.delete(tempGzFile);
        System.out.println("Extracted CSV file");

        return tempCsvFile.toString();
    }

    /**
     * Deletes a temporary file
     */
    public void deleteTempFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            // Ignore errors during cleanup
        }
    }

    public static void main(String[] args) {
        CsvDownloadService service = new CsvDownloadService();
        try {
            String file = service.downloadLatestCsvFile();
            System.out.println("Downloaded and extracted to: " + file);
            service.deleteTempFile(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

