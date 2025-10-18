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
     * Filters entries and keeps only bonds
     */
    public List<BondEntry> filterBonds(Map<String, BondEntry> entries, double investmentAmount) {
        List<BondEntry> bonds = new ArrayList<>();
        int total = entries.size();
        int current = 0;

        System.out.println("Checking " + total + " ISINs for bonds with investment amount: €" + String.format("%.2f", investmentAmount) + "...");

        for (BondEntry entry : entries.values()) {
            current++;
            System.out.println(); // New line for better readability
            System.out.println("=== " + current + "/" + total + " (" +
                           String.format("%.1f", (current * 100.0) / total) + "%) ===");

            if (isBondAndExtractData(entry.getIsin(), entry, investmentAmount) != null) {
                bonds.add(entry);
            }

            // No more delay - requests are sent immediately for faster execution
        }

        System.out.println(); // New line after progress display
        return bonds;
    }

    /**
     * Checks if an ISIN is a bond and extracts bond data
     */
    public BondEntry isBondAndExtractData(String isin, BondEntry entry, double investmentAmount) {
        try {
            // Direct visit to the bond page
            String url = "https://www.comdirect.de/inf/anleihen/" + isin;
            System.out.println("Checking ISIN: " + isin + " -> URL: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.comdirect.de/")
                    .GET()
                    .build();

            HttpResponse<String> response = sendRequestWithRetry(request);

            int statusCode = response.statusCode();
            boolean isBond = statusCode == 200;

            System.out.println("  -> HTTP Status: " + statusCode + " -> " + (isBond ? "IS bond" : "NOT a bond"));

            if (isBond) {
                // Parse HTML content and extract data with investment amount
                String htmlContent = response.body();
                BondDataExtractor extractor = new BondDataExtractor();
                extractor.extractBondData(entry, htmlContent, investmentAmount);

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
     * Executes HTTP request with retry mechanism for timeouts
     */
    private HttpResponse<String> sendRequestWithRetry(HttpRequest request) {
        var attempt = 0;
        while (true) {
            try {
                attempt++;
                System.out.println("  -> Attempt " + attempt);
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException e) {
                System.out.println("  -> Timeout at attempt " + attempt);
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
    }
}
