package yield;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import yield.cache.MaturityCache;
import yield.scraping.BondbloxDataExtractor;
import yield.scraping.ComdirectDataExtractor;
import yield.scraping.YieldCalculator;

/**
 * Service for handling HTTP requests and filtering bonds from web sources
 */
public class BondDataService {

    private final HttpClient httpClient;
    private final MaturityCache maturityCache;

    // Corporate bond name filters
    private static final String[] CORPORATE_BOND_MARKERS = {"Corp.", "Inc.", "Co."};

    public BondDataService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new java.net.CookieManager()) // Automatic cookie management
                .build();
        this.maturityCache = new MaturityCache();
    }

    /**
     * Checks if a bond name contains corporate bond markers
     */
    private boolean isCorporateBond(String bondName) {
        if (bondName == null || bondName.isEmpty()) {
            return false;
        }

        for (String marker : CORPORATE_BOND_MARKERS) {
            if (bondName.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forces an immediate save of the cache (call at program end)
     */
    public void saveCache() {
        maturityCache.forceSave();
    }

    /**
     * Filters entries and keeps only bonds with optional maximum days to maturity filter
     */
    public List<BondEntry> filterBonds(Map<String, BondEntry> entries, double investmentAmount, int maxDaysToMaturity) {
        List<BondEntry> bonds = new ArrayList<>();
        int total = entries.size();
        int current = 0;
        int skippedByCache = 0;
        int loadedFromCache = 0;
        int skippedCorporate = 0;

        System.out.println("Checking " + total + " ISINs for bonds with investment amount: €" + String.format("%.2f", investmentAmount) +
                          (maxDaysToMaturity > 0 ? " and max " + maxDaysToMaturity + " days to maturity" : "") + "...");
        System.out.println("Filtering out corporate bonds (containing: Corp., Inc., Co.)");

        if (maturityCache.getCacheSize() > 0) {
            System.out.println("Using cache with " + maturityCache.getCacheSize() + " entries.");
            System.out.println("Cache statistics: " + maturityCache.getCacheStats());
        }

        for (BondEntry entry : entries.values()) {
            current++;
            String isin = entry.getIsin();

            // Update progress line in-place
            System.out.printf("\rAnalyzing ISIN %d/%d (%.1f%%) - Current: %s (Skipped: %d, Cached: %d, Corporate: %d)",
                            current, total, (current * 100.0) / total, isin, skippedByCache, loadedFromCache, skippedCorporate);
            System.out.flush();

            // Check cache first to avoid unnecessary web requests
            if (maturityCache.shouldFilterByMaturity(isin, maxDaysToMaturity)) {
                skippedByCache++;
                continue; // Skip this ISIN - it exceeds max maturity based on cached data or had HTTP 400 error
            }

            // Check if we have complete cached data - if yes, use it without web request
            if (maturityCache.hasCompleteData(isin)) {
                maturityCache.loadCachedData(entry);

                // Filter out corporate bonds by name
                if (isCorporateBond(entry.getBondName())) {
                    skippedCorporate++;
                    continue;
                }

                // Calculate yield with cached data
                try {
                    double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                    entry.setAskPriceValue(askPrice);

                    if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                        double yield = YieldCalculator.calculateYieldForInvestment(askPrice, entry.getNominalInterestRate(),
                                    entry.getRemainingDays(), investmentAmount);
                        entry.setYield(yield);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
                }

                // Apply maturity filter if specified
                if (maxDaysToMaturity > 0 && entry.getDaysToMaturity() > maxDaysToMaturity) {
                    continue;
                }

                bonds.add(entry);
                loadedFromCache++;
                continue;
            }

            // Need to fetch from web
            BondEntry bondEntry = isBondAndExtractData(isin, entry, investmentAmount);
            if (bondEntry != null) {
                // Filter out corporate bonds by name (after caching the data)
                if (isCorporateBond(bondEntry.getBondName())) {
                    skippedCorporate++;
                    continue;
                }

                // Apply maturity filter if specified (double-check after web request)
                if (maxDaysToMaturity > 0 && bondEntry.getDaysToMaturity() > maxDaysToMaturity) {
                    // Skip this bond - it exceeds the maximum days to maturity
                    continue;
                }
                bonds.add(bondEntry);
            }
        }

        // Print final newline to end the progress line
        System.out.println();
        if (skippedByCache > 0) {
            System.out.println("Cache helped skip " + skippedByCache + " web requests (maturity filter + HTTP 400 errors).");
        }
        if (loadedFromCache > 0) {
            System.out.println("Loaded " + loadedFromCache + " bonds completely from cache (no web request needed).");
        }
        if (skippedCorporate > 0) {
            System.out.println("Filtered out " + skippedCorporate + " corporate bonds (containing Corp., Inc., or Co.).");
        }

        // Auto-save any remaining unsaved changes
        maturityCache.forceSave();

        System.out.println();
        return bonds;
    }

    /**
     * Checks if an ISIN is a bond and extracts bond data
     */
    public BondEntry isBondAndExtractData(String isin, BondEntry entry, double investmentAmount) {
        // Try to fetch from comdirect first
        ComdirectDataExtractor comdirectExtractor = new ComdirectDataExtractor(httpClient);
        boolean isComplete = comdirectExtractor.fetchAndExtractBondData(isin, entry, investmentAmount);

        // If data is incomplete, try fallback to bondblox.com
        if (!isComplete) {
            System.out.println(); // New line for clarity
            System.out.println("Attempting fallback to bondblox.com for " + isin + "...");

            BondbloxDataExtractor bondbloxExtractor = new BondbloxDataExtractor(httpClient);
            boolean fallbackSuccess = bondbloxExtractor.fetchAndExtractBondData(isin, entry, investmentAmount);

            if (!fallbackSuccess) {
                System.out.println("Fallback to bondblox.com failed for " + isin);
                // Continue with incomplete data from comdirect
            } else {
                System.out.println("Successfully retrieved data from bondblox.com for " + isin);
            }
        }

        // Check if we still don't have the essential data (after both comdirect and bondblox attempts)
        if (entry.getMaturityDate() == null || entry.getMaturityDate().isEmpty()) {
            // No data at all - likely HTTP 400 or 404
            maturityCache.storeHttp400Error(isin);
            return null;
        }

        // Store all bond data in cache for future use (even if it's a corporate bond)
        maturityCache.storeBondData(
            isin,
            entry.getMaturityDate(),
            entry.getNominalInterestRate() >= 0 ? entry.getNominalInterestRate() : null,
            entry.getBondName()
        );

        return entry;
    }
}


