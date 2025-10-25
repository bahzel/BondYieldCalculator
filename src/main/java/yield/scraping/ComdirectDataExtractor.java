package yield.scraping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import yield.BondEntry;

/**
 * Service for extracting bond data from comdirect.de
 */
public class ComdirectDataExtractor {

    private final HttpClient httpClient;

    public ComdirectDataExtractor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches and extracts bond data from comdirect.de
     * Returns true if data extraction was successful and complete, false if incomplete or failed
     */
    public boolean fetchAndExtractBondData(String isin, BondEntry entry, double investmentAmount) {
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

            // Only log if status is not 200 (OK), 400 (Bad Request), or 404 (Not Found)
            if (statusCode != 200 && statusCode != 400 && statusCode != 404) {
                System.out.println(); // New line before error message
                System.out.println("Checking ISIN: " + isin + " -> HTTP Status: " + statusCode);
            }

            if (statusCode == 200) {
                // Parse HTML content and extract data with investment amount
                String htmlContent = response.body();
                return extractBondData(entry, htmlContent, investmentAmount);
            } else if (statusCode == 400) {
                // Return false to indicate HTTP 400 error (will be cached)
                return false;
            }

            // Log headers for unexpected status codes (not 400 or 404)
            if (statusCode != 404) {
                System.out.println("  -> " + statusCode + " Response Headers:");
                response.headers().map().forEach((key, value) ->
                    System.out.println("     " + key + ": " + String.join(", ", value)));
            }

            return false;

        } catch (Exception e) {
            System.out.println(); // New line before error message
            System.err.println("Error checking ISIN " + isin + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Extracts bond data from HTML and calculates remaining time and yield
     * Returns true if data extraction was successful and complete, false if incomplete
     */
    private boolean extractBondData(BondEntry entry, String htmlContent, double investmentAmount) {
        try {
            // Extract bond name from headline
            extractBondName(entry, htmlContent);

            // Extract maturity - considers both "Fälligkeit" and "F&auml;lligkeit"
            String maturityPattern = "<th[^>]*>F(?:ä|&auml;)lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(maturityPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String maturity = matcher.group(1).trim();
                entry.setMaturityDate(maturity);

                // Calculate remaining days (precise via date)
                int remainingDays = DateCalculator.calculateRemainingDays(maturity);
                entry.setRemainingDays(remainingDays);
            } else {
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

                        int remainingDays = DateCalculator.calculateRemainingDays(maturity);
                        entry.setRemainingDays(remainingDays);
                        break;
                    }
                }
            }

            // Extract nominal interest rate
            extractNominalInterestRate(entry, htmlContent);

            // Take ask price from original CSV data (askPrice)
            try {
                double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                entry.setAskPriceValue(askPrice);

                // Calculate yield based on investment amount - now also works for zero-coupon bonds (0% nominal rate)
                if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                    double yield = YieldCalculator.calculateYieldForInvestment(askPrice, entry.getNominalInterestRate(),
                                                entry.getRemainingDays(), investmentAmount);
                    entry.setYield(yield);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
            }

        } catch (Exception e) {
            System.err.println("Error extracting bond data for " + entry.getIsin() + ": " + e.getMessage());
        }
        
        // Check if we have complete data (maturity, coupon rate, and bond name)
        boolean hasMaturity = entry.getMaturityDate() != null && !entry.getMaturityDate().isEmpty();
        boolean hasCoupon = entry.getNominalInterestRate() >= 0;
        boolean hasName = entry.getBondName() != null && !entry.getBondName().isEmpty() && !entry.getBondName().equals("Unknown Bond");
        
        boolean isComplete = hasMaturity && hasCoupon && hasName;
        
        if (!isComplete) {
            System.out.println(); // New line for clarity
            System.out.println("Incomplete data from comdirect for " + entry.getIsin() + 
                             " (Maturity: " + hasMaturity + ", Coupon: " + hasCoupon + ", Name: " + hasName + ")");
        }
        
        return isComplete;
    }

    /**
     * Extracts bond name from HTML h1 headline
     */
    private void extractBondName(BondEntry entry, String htmlContent) {
        try {
            // Pattern for h1 headline with bond name
            String namePattern = "<h1[^>]*class=\"[^\"]*headline[^\"]*\"[^>]*>\\s*([^<]+?)(?:<span[^>]*>[^<]*</span>)?\\s*</h1>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(namePattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String bondName = matcher.group(1).trim();
                // Clean up the name - remove extra whitespace
                bondName = bondName.replaceAll("\\s+", " ");
                entry.setBondName(bondName);
            } else {
                // Fallback: try simpler pattern
                String fallbackPattern = "<h1[^>]*>([^<]+)</h1>";
                pattern = java.util.regex.Pattern.compile(fallbackPattern);
                matcher = pattern.matcher(htmlContent);

                if (matcher.find()) {
                    String bondName = matcher.group(1).trim();
                    bondName = bondName.replaceAll("\\s+", " ");
                    entry.setBondName(bondName);
                } else {
                    entry.setBondName("Unknown Bond");
                }
            }
        } catch (Exception e) {
            entry.setBondName("Unknown Bond");
        }
    }

    /**
     * Extracts nominal interest rate from HTML content
     */
    private void extractNominalInterestRate(BondEntry entry, String htmlContent) {
        // Extract nominal interest rate (with &#160; HTML entity)
        String nominalRatePattern = "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,.]+)\\s*&#160;\\s*%</td>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(nominalRatePattern);
        java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

        if (matcher.find()) {
            String nominalRateStr = matcher.group(1).replace(",", ".");
            double nominalRate = Double.parseDouble(nominalRateStr);
            entry.setNominalInterestRate(nominalRate);
        } else {
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
                    break;
                }
            }
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

