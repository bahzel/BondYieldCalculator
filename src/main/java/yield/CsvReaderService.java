package yield;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Service class for reading and parsing CSV files containing bond data
 */
public class CsvReaderService {

    /**
     * Reads CSV file (automatically detects if GZIP-compressed) and returns the latest entries per ISIN
     * Ultra-aggressively optimized for sub-10-second performance
     */
    public Map<String, BondEntry> readCsvFile(String filePath) throws IOException {
        System.out.println("Starting CSV processing...");
        long startTime = System.currentTimeMillis();

        // Maximum buffers for extreme I/O performance
        final int GZIP_BUFFER = 262144; // 256KB - Maximum!
        final int READ_BUFFER = 524288; // 512KB - Extremely large!

        // Pre-size HashMap for better performance (no rehashing)
        Map<String, BondEntry> latestEntries = new ConcurrentHashMap<>(100000);

        // Much larger batches for less overhead
        final int BATCH_SIZE = 50000; // 5x larger!
        List<String> batch = new ArrayList<>(BATCH_SIZE);
        int lineCount = 0;
        int batchCount = 0;
        long lastProgressUpdate = 0;

        // Check if file is GZIP compressed by reading the magic number
        boolean isGzipped = isGzipFile(filePath);

        try (FileInputStream fis = new FileInputStream(filePath);
             InputStream inputStream = isGzipped ? new GZIPInputStream(fis, GZIP_BUFFER) : fis;
             InputStreamReader isr = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr, READ_BUFFER)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;

                // Even more aggressive early filtering
                if (line.length() < 20 || !line.contains(",")) continue;

                batch.add(line);

                // Process larger batches less frequently
                if (batch.size() >= BATCH_SIZE) {
                    batchCount++;
                    processMegaBatchParallel(batch, latestEntries);
                    batch.clear();

                    // Update progress every 10 batches or every 2 seconds
                    long currentTime = System.currentTimeMillis();
                    if (batchCount % 10 == 0 || currentTime - lastProgressUpdate > 2000) {
                        long elapsed = currentTime - startTime;
                        double linesPerSec = lineCount * 1000.0 / Math.max(1, elapsed);
                        System.out.printf("\rProcessing: %,d lines, %,d ISINs, %,d batches | %.1f lines/sec | %,d ms elapsed",
                                        lineCount, latestEntries.size(), batchCount, linesPerSec, elapsed);
                        System.out.flush();
                        lastProgressUpdate = currentTime;
                    }
                }
            }

            // Process last batch
            if (!batch.isEmpty()) {
                batchCount++;
                processMegaBatchParallel(batch, latestEntries);
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Print final newline to end the progress line, then show summary
        System.out.println();
        System.out.println();
        System.out.println("=== CSV Processing Complete ===");
        System.out.printf("  Total lines processed: %,d%n", lineCount);
        System.out.printf("  Unique ISINs found: %,d%n", latestEntries.size());
        System.out.printf("  Processing batches: %,d%n", batchCount);
        System.out.printf("  Total processing time: %,d ms (%.2f seconds)%n", totalTime, totalTime / 1000.0);
        System.out.printf("  Average speed: %,.0f lines/second%n", lineCount * 1000.0 / Math.max(1, totalTime));
        System.out.println();

        return latestEntries;
    }

    /**
     * Checks if a file is GZIP compressed by reading the magic number
     */
    private boolean isGzipFile(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] header = new byte[2];
            int bytesRead = fis.read(header);
            if (bytesRead < 2) {
                return false;
            }
            // GZIP magic number: 0x1f 0x8b
            return (header[0] == (byte) 0x1f && header[1] == (byte) 0x8b);
        }
    }

    /**
     * Processes mega-batches with maximum parallelization
     */
    private void processMegaBatchParallel(List<String> batch, Map<String, BondEntry> latestEntries) {
        // Maximum parallelization with ForkJoin
        batch.parallelStream()
             .unordered() // Important for performance!
             .forEach(line -> {
                 BondEntry entry = parseLineUltraFast(line);
                 if (entry != null) {
                     // Optimized thread-safe update
                     latestEntries.merge(entry.getIsin(), entry,
                         (existing, newEntry) -> newEntry.getTimestamp().compareTo(existing.getTimestamp()) > 0 ? newEntry : existing
                     );
                 }
             });
    }

    /**
     * ULTRA-FAST Line Parsing - eliminates all unnecessary operations
     */
    private BondEntry parseLineUltraFast(String line) {
        // Direct char-array access (faster than charAt)
        char[] chars = line.toCharArray();
        int len = chars.length;

        // Fastest comma search with array access
        int[] commas = new int[6];
        int commaCount = 0;

        for (int i = 0; i < len && commaCount < 6; i++) {
            if (chars[i] == ',') {
                commas[commaCount++] = i;
            }
        }

        if (commaCount < 6) return null;

        // Ultra-fast field extraction WITHOUT string operations where possible
        String isin = extractFieldUltraFast(chars, 0, commas[0]);
        if (isin.isEmpty()) return null;

        String timestamp = extractFieldUltraFast(chars, commas[0] + 1, commas[1]);
        String currency = extractFieldUltraFast(chars, commas[1] + 1, commas[2]);
        String bidPrice = extractFieldUltraFast(chars, commas[2] + 1, commas[3]);
        String askPrice = extractFieldUltraFast(chars, commas[4] + 1, commas[5]);

        return new BondEntry(isin, timestamp, currency, bidPrice, askPrice);
    }

    /**
     * Ultra-fast field extraction directly from char array
     */
    private String extractFieldUltraFast(char[] chars, int start, int end) {
        // Skip whitespace at beginning
        while (start < end && chars[start] <= ' ') start++;
        // Skip whitespace at end
        while (end > start && chars[end - 1] <= ' ') end--;

        // Direct string construction from char array (faster than substring)
        return start < end ? new String(chars, start, end - start) : "";
    }
}
