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
     * Reads GZIP-compressed CSV file and returns the latest entries per ISIN
     * Ultra-aggressively optimized for sub-10-second performance
     */
    public Map<String, BondEntry> readCsvFile(String filePath) throws IOException {
        System.out.println("Starting ULTRA-AGGRESSIVE CSV processing...");
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

        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis, GZIP_BUFFER);
             InputStreamReader isr = new InputStreamReader(gzis, java.nio.charset.StandardCharsets.UTF_8);
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
                    if (batchCount % 5 == 1) { // Less output for speed
                        System.out.println("Mega-Batch " + batchCount + " (Lines: " + lineCount + ", ISINs: " + latestEntries.size() + ")");
                    }
                    processMegaBatchParallel(batch, latestEntries);
                    batch.clear();
                }
            }

            // Process last batch
            if (!batch.isEmpty()) {
                batchCount++;
                processMegaBatchParallel(batch, latestEntries);
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("ULTRA-AGGRESSIVE CSV processing completed:");
        System.out.println("  - Processed lines: " + lineCount);
        System.out.println("  - Mega-batches: " + batchCount);
        System.out.println("  - Unique ISINs: " + latestEntries.size());
        System.out.println("  - Processing time: " + (endTime - startTime) + "ms");
        System.out.println("  - Lines/second: " + (lineCount * 1000L / Math.max(1, endTime - startTime)));
        System.out.println("  - SPEED BOOST ACHIEVED!");

        return latestEntries;
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
