package de.example.rendite;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class for reading and processing bond CSV files
 */
public class CsvReader {

    /**
     * Data class for a bond entry
     */
    public static class BondEntry {
        private final String isin;
        private final String timestamp;
        private final String currency;
        private final String bidPrice;
        private final String askPrice;

        // New fields for extended calculation
        private int remainingDays = -1;
        private double yield = -1.0;
        private double askPriceValue = -1.0;
        private double nominalInterestRate = -1.0;
        private String maturityDate = "";

        public BondEntry(String isin, String timestamp, String currency,
                        String bidPrice, String askPrice) {
            this.isin = isin;
            this.timestamp = timestamp;
            this.currency = currency;
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
        }

        // Getters
        public String getIsin() { return isin; }
        public String getTimestamp() { return timestamp; }
        public String getAskPrice() { return askPrice; }

        // New Getters/Setters
        public int getRemainingDays() { return remainingDays; }
        public void setRemainingDays(int remainingDays) { this.remainingDays = remainingDays; }

        public double getYield() { return yield; }
        public void setYield(double yield) { this.yield = yield; }

        public double getAskPriceValue() { return askPriceValue; }
        public void setAskPriceValue(double askPriceValue) { this.askPriceValue = askPriceValue; }

        public double getNominalInterestRate() { return nominalInterestRate; }
        public void setNominalInterestRate(double nominalInterestRate) { this.nominalInterestRate = nominalInterestRate; }

        public String getMaturityDate() { return maturityDate; }
        public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }

        @Override
        public String toString() {
            return String.format("%-15s %-8s %-10s %-10s %-12s %-8d %-10.3f%%",
                    isin, currency, bidPrice, askPrice, maturityDate,
                    Math.max(remainingDays, 0),
                    yield >= 0 ? yield : 0.0);
        }
    }

    /**
     * Reads GZIP-compressed CSV file and returns the latest entries per ISIN
     * Ultra-aggressively optimized for sub-10-second performance
     */
    public static Map<String, BondEntry> readCsvFile(String filePath) throws IOException {
        System.out.println("Starting ULTRA-AGGRESSIVE CSV processing...");
        long startTime = System.currentTimeMillis();

        // Maximum buffers for extreme I/O performance
        final int GZIP_BUFFER = 262144; // 256KB - Maximum!
        final int READ_BUFFER = 524288; // 512KB - Extremely large!

        // Pre-size HashMap for better performance (no rehashing)
        Map<String, BondEntry> latestEntries = new java.util.concurrent.ConcurrentHashMap<>(100000);

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
    private static void processMegaBatchParallel(List<String> batch, Map<String, BondEntry> latestEntries) {
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
    private static BondEntry parseLineUltraFast(String line) {
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
    private static String extractFieldUltraFast(char[] chars, int start, int end) {
        // Skip whitespace at beginning
        while (start < end && chars[start] <= ' ') start++;
        // Skip whitespace at end
        while (end > start && chars[end - 1] <= ' ') end--;

        // Direct string construction from char array (faster than substring)
        return start < end ? new String(chars, start, end - start) : "";
    }

    /**
     * Executes HTTP request with retry mechanism for timeouts
     */
    public static HttpResponse<String> sendRequestWithRetry(HttpClient httpClient, HttpRequest request, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                attempt++;
                System.out.println("  -> Attempt " + attempt + "/" + maxRetries);
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException e) {
                System.out.println("  -> Timeout at attempt " + attempt + "/" + maxRetries);
                if (attempt >= maxRetries) {
                    System.err.println("  -> All " + maxRetries + " attempts failed due to timeout");
                    throw new RuntimeException("HTTP timeout after " + maxRetries + " attempts", e);
                }
                // Wait before next attempt (exponential backoff)
                try {
                    int waitTime = 2000 * attempt; // 2s, 4s, 6s, etc.
                    System.out.println("  -> Waiting " + waitTime + "ms before next attempt...");
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry wait time", ie);
                }
            } catch (Exception e) {
                // Other errors (not timeout) throw immediately
                throw new RuntimeException("HTTP error: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Unexpected error in retry mechanism");
    }

    /**
     * Checks if an ISIN is a bond and extracts bond data
     */
    public static BondEntry isBondAndExtractData(String isin, HttpClient httpClient, BondEntry entry) {
        try {
            // First visit main page to get cookies/session
            String mainUrl = "https://www.comdirect.de/";
            System.out.println("Visiting main page for session: " + mainUrl);

            HttpRequest mainRequest = HttpRequest.newBuilder()
                    .uri(URI.create(mainUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            httpClient.send(mainRequest, HttpResponse.BodyHandlers.ofString());

            // Wait briefly
            Thread.sleep(500);

            // Now visit the bond page
            String url = "https://www.comdirect.de/inf/anleihen/" + isin;
            System.out.println("Checking ISIN: " + isin + " -> URL: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.comdirect.de/")
                    .GET()
                    .build();

            HttpResponse<String> response = sendRequestWithRetry(httpClient, request, 3);

            int statusCode = response.statusCode();
            boolean isBond = statusCode == 200;

            System.out.println("  -> HTTP Status: " + statusCode + " -> " + (isBond ? "IS bond" : "NOT a bond"));

            if (isBond) {
                // Parse HTML content and extract data
                String htmlContent = response.body();
                extractBondData(entry, htmlContent);

                System.out.println("  -> Maturity: " + entry.getMaturityDate());
                System.out.println("  -> Remaining days: " + entry.getRemainingDays() + " days");
                System.out.println("  -> Nominal interest rate: " + entry.getNominalInterestRate() + "%");
                System.out.println("  -> Ask price (from CSV): " + entry.getAskPriceValue());
                System.out.println("  -> Calculated yield: " + String.format("%.3f", entry.getYield()) + "%");

                return entry;
            }

            // Debug: Show response headers for 401
            if (statusCode == 401) {
                System.out.println("  -> 401 Response Headers:");
                response.headers().map().forEach((key, value) ->
                    System.out.println("     " + key + ": " + String.join(", ", value)));
            }

            return null;

        } catch (Exception e) {
            // On errors, assume it's not a bond
            System.err.println("Error checking ISIN " + isin + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Filters entries and keeps only bonds
     */
    public static List<BondEntry> filterBonds(Map<String, BondEntry> entries) {
        List<BondEntry> bonds = new ArrayList<>();

        // HTTP client with cookie management
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new java.net.CookieManager()) // Automatic cookie management
                .build();

        int total = entries.size();
        int current = 0;

        System.out.println("Checking " + total + " ISINs for bonds...");

        for (BondEntry entry : entries.values()) {
            current++;
            System.out.println(); // New line for better readability
            System.out.println("=== " + current + "/" + total + " (" +
                           String.format("%.1f", (current * 100.0) / total) + "%) ===");

            if (isBondAndExtractData(entry.getIsin(), httpClient, entry) != null) {
                bonds.add(entry);
            }

            // Longer pause to avoid overloading server
            try {
                Thread.sleep(3000); // 3 second pause - longer due to 401 issues
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println(); // New line after progress display
        return bonds;
    }

    /**
     * Extracts bond data from HTML and calculates remaining time and yield
     */
    public static void extractBondData(BondEntry entry, String htmlContent) {
        try {
            // DEBUG: Output relevant HTML part for maturity
            int maturityStart = htmlContent.indexOf("lligkeit"); // Search for "lligkeit" to find both "Fälligkeit" and "F&auml;lligkeit"
            if (maturityStart >= 0) {
                int contextStart = Math.max(0, maturityStart - 100);
                int contextEnd = Math.min(htmlContent.length(), maturityStart + 300);
                String context = htmlContent.substring(contextStart, contextEnd);
                System.out.println("DEBUG - HTML around maturity:");
                System.out.println(context);
                System.out.println("---");
            }

            // Extract maturity - considers both "Fälligkeit" and "F&auml;lligkeit"
            String maturityPattern = "<th[^>]*>F(?:ä|&auml;)lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(maturityPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String maturity = matcher.group(1).trim();
                entry.setMaturityDate(maturity);
                System.out.println("DEBUG - Maturity found: " + maturity);

                // Calculate remaining days (precise via date)
                int remainingDays = calculateRemainingDays(maturity);
                entry.setRemainingDays(remainingDays);
                System.out.println("DEBUG - Remaining days calculated: " + remainingDays + " days");
            } else {
                System.out.println("DEBUG - Maturity NOT found with pattern: " + maturityPattern);

                // Extended fallback patterns for different HTML variants
                String[] fallbackPatterns = {
                    "<th[^>]*>F&auml;lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    "<th[^>]*>Fälligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    ">[^<]*(?:F&auml;lligkeit|Fälligkeit)[^<]*</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>"
                };

                for (String fallbackPattern : fallbackPatterns) {
                    pattern = java.util.regex.Pattern.compile(fallbackPattern);
                    matcher = pattern.matcher(htmlContent);
                    if (matcher.find()) {
                        String maturity = matcher.group(1).trim();
                        entry.setMaturityDate(maturity);
                        System.out.println("DEBUG - Maturity found with fallback: " + maturity + " (Pattern: " + fallbackPattern + ")");

                        int remainingDays = calculateRemainingDays(maturity);
                        entry.setRemainingDays(remainingDays);
                        System.out.println("DEBUG - Remaining days calculated: " + remainingDays + " days");
                        break;
                    }
                }
            }

            // DEBUG: Output relevant HTML part for nominal interest rate
            int nominalStart = htmlContent.indexOf("Nominalzinssatz");
            if (nominalStart >= 0) {
                int contextStart = Math.max(0, nominalStart - 100);
                int contextEnd = Math.min(htmlContent.length(), nominalStart + 300);
                String context = htmlContent.substring(contextStart, contextEnd);
                System.out.println("DEBUG - HTML around nominal interest rate:");
                System.out.println(context);
                System.out.println("---");
            }

            // Extract nominal interest rate (with &#160; HTML entity)
            String nominalRatePattern = "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,.]+)\\s*&#160;\\s*%</td>";
            pattern = java.util.regex.Pattern.compile(nominalRatePattern);
            matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String nominalRateStr = matcher.group(1).replace(",", ".");
                double nominalRate = Double.parseDouble(nominalRateStr);
                entry.setNominalInterestRate(nominalRate);
                System.out.println("DEBUG - Nominal interest rate found: " + nominalRate + "%");
            } else {
                System.out.println("DEBUG - Nominal interest rate NOT found with pattern: " + nominalRatePattern);

                // Fallback: Try other variants
                String[] fallbackPatterns = {
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*&nbsp;\\s*%</td>",
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*%</td>",
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)[^0-9]*%</td>"
                };

                for (String fallbackPattern : fallbackPatterns) {
                    pattern = java.util.regex.Pattern.compile(fallbackPattern);
                    matcher = pattern.matcher(htmlContent);
                    if (matcher.find()) {
                        String nominalRateStr = matcher.group(1).replace(",", ".");
                        double nominalRate = Double.parseDouble(nominalRateStr);
                        entry.setNominalInterestRate(nominalRate);
                        System.out.println("DEBUG - Nominal interest rate found with fallback: " + nominalRate + "% (Pattern: " + fallbackPattern + ")");
                        break;
                    }
                }
            }

            // Take ask price from original CSV data (askPrice)
            try {
                double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                entry.setAskPriceValue(askPrice);

                // Calculate yield
                if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() > 0) {
                    double yield = calculateYield(askPrice, entry.getNominalInterestRate(),
                                                entry.getRemainingDays(), 2.50);
                    entry.setYield(yield);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
            }

        } catch (Exception e) {
            System.err.println("Error extracting bond data for " + entry.getIsin() + ": " + e.getMessage());
        }
    }

    /**
     * Calculates remaining days based on maturity date
     */
    public static int calculateRemainingDays(String maturityDate) {
        try {
            // Format: "24.01.2052"
            String[] parts = maturityDate.split("\\.");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);

                java.time.LocalDate maturity = java.time.LocalDate.of(year, month, day);
                java.time.LocalDate today = java.time.LocalDate.now();

                return (int) java.time.temporal.ChronoUnit.DAYS.between(today, maturity);
            }
        } catch (Exception e) {
            System.err.println("Error parsing maturity date: " + maturityDate);
        }
        return -1;
    }

    /**
     * Calculates yield to maturity (Yield to Maturity)
     * considering transaction costs
     */
    public static double calculateYield(double askPrice, double nominalRate, int remainingDays, double costs) {
        try {
            // Simple yield calculation (without complex YTM iteration)
            double yearsToMaturity = remainingDays / 365.0;

            // Total cost of purchase (ask price + transaction costs)
            double totalCosts = askPrice + costs;

            // Assumption: Nominal value = 100, annual interest payments
            double nominalValue = 100.0;
            double annualInterest = nominalValue * (nominalRate / 100.0);

            // Total interest payments until maturity
            double totalInterest = annualInterest * yearsToMaturity;

            // Total return = interest payments + repayment of nominal value
            double totalReturn = totalInterest + nominalValue;

            // Yield = (Total return / Total costs)^(1/years) - 1
            double yield = Math.pow(totalReturn / totalCosts, 1.0 / yearsToMaturity) - 1.0;

            return yield * 100.0; // In percent

        } catch (Exception e) {
            System.err.println("Error calculating yield: " + e.getMessage());
            return -1.0;
        }
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        String csvFilePath = "C:\\tmp\\anleihen\\pretrade.20251013.14.45.mund.csv.gz";

        try {
            System.out.println("Reading CSV file: " + csvFilePath);
            Map<String, BondEntry> latestEntries = readCsvFile(csvFilePath);

            System.out.println("Total " + latestEntries.size() + " unique ISINs found.");

            // Filter only ISINs with "GR" (Greek bonds)
            Map<String, BondEntry> grEntries = new HashMap<>();
            for (Map.Entry<String, BondEntry> entry : latestEntries.entrySet()) {
                if (entry.getKey().startsWith("GR")) {
                    grEntries.put(entry.getKey(), entry.getValue());
                }
            }

            System.out.println("Of which " + grEntries.size() + " ISINs start with 'GR'.");

            if (grEntries.isEmpty()) {
                System.out.println("No ISINs starting with 'GR' found.");
                return;
            }

            // Filter bonds (only GR-ISINs)
            System.out.println();
            List<BondEntry> bonds = filterBonds(grEntries);

            // Output found bonds
            System.out.println();
            System.out.println("=== Greek Government Bonds (GR) ===");
            System.out.printf("Found bonds: %d%n", bonds.size());
            System.out.println();
            System.out.printf("%-15s %-8s %-10s %-10s %-12s %-8s %-10s%n",
                    "ISIN", "Currency", "Bid Price", "Ask Price", "Maturity", "Days", "Yield");
            System.out.println("-".repeat(85));
            bonds.stream()
                    .sorted(Comparator.comparing(BondEntry::getYield).reversed())
                    .forEach(System.out::println);

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
