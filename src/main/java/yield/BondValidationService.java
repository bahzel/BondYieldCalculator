package yield;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for handling HTTP requests and filtering bonds from web sources
 */
public class BondValidationService {

    private final HttpClient httpClient;

    public BondValidationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new java.net.CookieManager()) // Automatic cookie management
                .build();
    }

    /**
     * Filters entries and keeps only bonds with optional maximum days to maturity filter
     */
    public List<BondEntry> filterBonds(Map<String, BondEntry> entries, double investmentAmount, int maxDaysToMaturity) {
        List<BondEntry> bonds = new ArrayList<>();
        int total = entries.size();
        int current = 0;

        System.out.println("Checking " + total + " ISINs for bonds with investment amount: €" + String.format("%.2f", investmentAmount) +
                          (maxDaysToMaturity > 0 ? " and max " + maxDaysToMaturity + " days to maturity" : "") + "...");

        for (BondEntry entry : entries.values()) {
            current++;

            // Update progress line in-place
            System.out.printf("\rAnalyzing ISIN %d/%d (%.1f%%) - Current: %s",
                            current, total, (current * 100.0) / total, entry.getIsin());
            System.out.flush();

            BondEntry bondEntry = isBondAndExtractData(entry.getIsin(), entry, investmentAmount);
            if (bondEntry != null) {
                // Apply maturity filter if specified
                if (maxDaysToMaturity > 0 && bondEntry.getDaysToMaturity() > maxDaysToMaturity) {
                    // Skip this bond - it exceeds the maximum days to maturity
                    continue;
                }
                bonds.add(bondEntry);
                // No output for successful bonds - just continue with next ISIN
            }
        }

        // Print final newline to end the progress line
        System.out.println();
        System.out.println();
        return bonds;
    }

    /**
     * Checks if an ISIN is a bond and extracts bond data
     */
    public BondEntry isBondAndExtractData(String isin, BondEntry entry, double investmentAmount) {
        try {
            String url = "https://www.comdirect.de/inf/anleihen/" + isin;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.comdirect.de/")
                    .GET()
                    .build();

            HttpResponse<String> response = sendRequestWithRetry(request, isin);

            int statusCode = response.statusCode();
            boolean isBond = statusCode == 200;

            // Only log if status is not 200 (OK), 400 (Bad Request), or 404 (Not Found)
            if (statusCode != 200 && statusCode != 400 && statusCode != 404) {
                System.out.println(); // New line before error message
                System.out.println("Checking ISIN: " + isin + " -> HTTP Status: " + statusCode);
            }

            if (isBond) {
                // Parse HTML content and extract data with investment amount
                String htmlContent = response.body();
                BondDataExtractor extractor = new BondDataExtractor();
                extractor.extractBondData(entry, htmlContent, investmentAmount);
                return entry;
            }

            // Only log headers for unexpected status codes (not 400 or 404)
            if (statusCode != 400 && statusCode != 404) {
                System.out.println("  -> " + statusCode + " Response Headers:");
                response.headers().map().forEach((key, value) ->
                    System.out.println("     " + key + ": " + String.join(", ", value)));
            }

            return null;

        } catch (Exception e) {
            System.out.println(); // New line before error message
            System.err.println("Error checking ISIN " + isin + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Executes HTTP request with retry mechanism for timeouts
     */
    private HttpResponse<String> sendRequestWithRetry(HttpRequest request, String isin) {
        var attempt = 0;
        while (true) {
            try {
                attempt++;
                // Only log retry attempts (not the first attempt)
                if (attempt > 1) {
                    System.out.println(); // New line before retry message
                    System.out.println("  -> Retry attempt " + attempt + " for ISIN: " + isin);
                }
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException e) {
                System.out.println(); // New line before timeout message
                System.out.println("  -> Timeout at attempt " + attempt + " for ISIN: " + isin);
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
                System.out.println(); // New line before error message
                System.err.println("  -> HTTP error for ISIN " + isin + ": " + e.getMessage());
                throw new RuntimeException("HTTP error: " + e.getMessage(), e);
            }
        }
    }
}
