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
public class BondbloxDataExtractor extends BondDataExtractor {

    public BondbloxDataExtractor(HttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Fetches and extracts bond data from bondblox.com
     * Returns ExtractionResult indicating the outcome
     */
    @Override
    public ExtractionResult fetchAndExtractBondData(String isin, BondEntry entry, double investmentAmount) {
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
                boolean isComplete = hasMaturity && hasCoupon;

                if (isComplete) {
                    System.out.println("  -> Bondblox: Data complete for " + isin);
                    return ExtractionResult.COMPLETE;
                } else {
                    System.out.println("  -> Bondblox: Data incomplete for " + isin +
                                     " (Maturity: " + hasMaturity + ", Coupon: " + hasCoupon + ")");
                    return ExtractionResult.INCOMPLETE;
                }
            } else if (response.statusCode() == 404) {
                System.out.println("  -> Bondblox: Request failed for " + isin + " (HTTP 404)");
                return ExtractionResult.NOT_FOUND;
            } else {
                System.out.println("  -> Bondblox: Request failed for " + isin + " (HTTP " + response.statusCode() + ")");
                return ExtractionResult.ERROR;
            }

        } catch (Exception e) {
            System.out.println("  -> Bondblox: Request failed for " + isin + " (" + e.getMessage() + ")");
            return ExtractionResult.ERROR;
        }
    }

    /**
     * Extracts bond data from bondblox.com HTML and calculates remaining time and yield
     */
    private void extractBondData(BondEntry entry, String htmlContent, double investmentAmount) {
        // Extract bond name - bondblox doesn't provide it directly, use ISIN as fallback
        // The page title or issuer info could be extracted if needed

        // Extract maturity date from "Maturity Date" field
        extractMaturityDate(entry, htmlContent);

        // Extract coupon rate from "Current Coupon" field
        extractCouponRate(entry, htmlContent);

        // Calculate and set yield using common method from base class
        calculateAndSetYield(entry, investmentAmount);
    }

    /**
     * Extracts maturity date from bondblox.com HTML
     */
    private void extractMaturityDate(BondEntry entry, String htmlContent) {
        // Pattern: <div>Maturity Date</div><div class="bondinfo_bondInfoVal__Qn2Uv">DD/MM/YYYY</div>
        String maturityPattern = "<div>Maturity Date</div>\\s*<div[^>]*class=\"[^\"]*bondinfo_bondInfoVal[^\"]*\"[^>]*>([0-9]{2}/[0-9]{2}/[0-9]{4})</div>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(maturityPattern);
        java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

        if (matcher.find()) {
            String maturityStr = matcher.group(1).trim();
            // Convert from DD/MM/YYYY to DD.MM.YYYY format
            String maturity = maturityStr.replace("/", ".");

            // Use common method from base class
            setMaturityAndCalculateRemainingDays(entry, maturity);
        }
    }

    /**
     * Extracts coupon rate from bondblox.com HTML
     */
    private void extractCouponRate(BondEntry entry, String htmlContent) {
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
    }
}


