package yield.scraping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import yield.BondEntry;

/**
 * Service for extracting bond data from bondblox.com HTML content
 */
public class BondbloxDataExtractor {

    private final HttpClient httpClient;

    public BondbloxDataExtractor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches and extracts bond data from bondblox.com
     * Returns true if successful, false otherwise
     */
    public boolean fetchAndExtractBondData(String isin, BondEntry entry, double investmentAmount) {
        try {
            // bondblox.com URL format: https://bondblox.com/bond-market/[ISIN]
            // Example: US06407FAH55 -> https://bondblox.com/bond-market/US06407FAH55
            String url = "https://bondblox.com/bond-market/" + isin;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String htmlContent = response.body();
                extractBondData(entry, htmlContent, investmentAmount);

                // Verify that we got the essential data
                boolean hasMaturity = entry.getMaturityDate() != null && !entry.getMaturityDate().isEmpty();
                boolean hasCoupon = entry.getNominalInterestRate() >= 0;

                return hasMaturity && hasCoupon;
            } else {
                System.out.println("  -> bondblox.com returned HTTP " + response.statusCode() + " for " + isin);
                return false;
            }

        } catch (Exception e) {
            System.err.println("  -> Error fetching from bondblox.com for " + isin + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Extracts bond data from bondblox.com HTML and calculates remaining time and yield
     */
    private void extractBondData(BondEntry entry, String htmlContent, double investmentAmount) {
        try {
            // Extract bond name - bondblox doesn't provide it directly, use ISIN as fallback
            // The page title or issuer info could be extracted if needed

            // Extract maturity date from "Maturity Date" field
            extractMaturityDate(entry, htmlContent);

            // Extract coupon rate from "Current Coupon" field
            extractCouponRate(entry, htmlContent);

            // Take ask price from original CSV data (askPrice)
            try {
                double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                entry.setAskPriceValue(askPrice);

                // Calculate yield based on investment amount
                if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                    double yield = YieldCalculator.calculateYieldForInvestment(askPrice, entry.getNominalInterestRate(),
                                                entry.getRemainingDays(), investmentAmount);
                    entry.setYield(yield);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
            }

        } catch (Exception e) {
            System.err.println("Error extracting bond data from bondblox.com for " + entry.getIsin() + ": " + e.getMessage());
        }
    }

    /**
     * Extracts maturity date from bondblox.com HTML
     */
    private void extractMaturityDate(BondEntry entry, String htmlContent) {
        try {
            // Pattern: <div>Maturity Date</div><div class="bondinfo_bondInfoVal__Qn2Uv">DD/MM/YYYY</div>
            String maturityPattern = "<div>Maturity Date</div>\\s*<div[^>]*class=\"[^\"]*bondinfo_bondInfoVal[^\"]*\"[^>]*>([0-9]{2}/[0-9]{2}/[0-9]{4})</div>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(maturityPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String maturityStr = matcher.group(1).trim();
                // Convert from DD/MM/YYYY to DD.MM.YYYY format
                String maturity = maturityStr.replace("/", ".");
                entry.setMaturityDate(maturity);

                // Calculate remaining days
                int remainingDays = DateCalculator.calculateRemainingDays(maturity);
                entry.setRemainingDays(remainingDays);
            }
        } catch (Exception e) {
            System.err.println("Error extracting maturity date from bondblox.com for " + entry.getIsin() + ": " + e.getMessage());
        }
    }

    /**
     * Extracts coupon rate from bondblox.com HTML
     */
    private void extractCouponRate(BondEntry entry, String htmlContent) {
        try {
            // Pattern: <div>Current Coupon</div><div class="bondinfo_bondInfoVal__Qn2Uv">X.XXX%</div>
            String couponPattern = "<div>Current Coupon</div>\\s*<div[^>]*class=\"[^\"]*bondinfo_bondInfoVal[^\"]*\"[^>]*>([0-9.,]+)%</div>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(couponPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String couponStr = matcher.group(1).replace(",", ".");
                // Remove thousand separators if present
                couponStr = couponStr.replace("'", "");
                double couponRate = Double.parseDouble(couponStr);
                entry.setNominalInterestRate(couponRate);
            }
        } catch (Exception e) {
            System.err.println("Error extracting coupon rate from bondblox.com for " + entry.getIsin() + ": " + e.getMessage());
        }
    }
}


