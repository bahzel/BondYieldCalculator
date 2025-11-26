package yield;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import yield.cache.MaturityCache;
import yield.scraping.BondbloxDataExtractor;
import yield.scraping.ComdirectDataExtractor;
import yield.scraping.ExtractionResult;
import yield.scraping.YieldCalculator;
import yield.util.SslUtils;

/**
 * Service for handling HTTP requests and filtering bonds from web sources
 */
public class BondDataService {

    private final HttpClient httpClient;
    private final MaturityCache maturityCache;

    // Corporate bond name filters
    private static final String[] CORPORATE_BOND_MARKERS = {"Corp.", "Inc.", "Co.", "S.A."};

    public BondDataService() {
        // Configure SSL to trust all certificates (needed for some bond data sources)
        SslUtils.configureTrustAllCertificates();

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
     * Returns the number of HTTP 400 error entries in the cache
     */
    public int getHttp400ErrorCount() {
        return maturityCache.getHttp400ErrorCount();
    }

    /**
     * Clears all HTTP 400 error entries from the cache
     * Returns the number of entries removed
     */
    public int clearHttp400Errors() {
        return maturityCache.clearHttp400Errors();
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
     * Uses multiple extractors in sequence until data is complete or all extractors are exhausted
     */
    public BondEntry isBondAndExtractData(String isin, BondEntry entry, double investmentAmount) {
        // Define the order of extractors to use
        java.util.List<yield.scraping.BondDataExtractor> extractors = java.util.Arrays.asList(
            new ComdirectDataExtractor(httpClient),
            new BondbloxDataExtractor(httpClient)
        );

        String[] extractorNames = {"Comdirect", "Bondblox"};

        boolean allExtractorsFailed = true;
        int extractorIndex = 0;

        for (yield.scraping.BondDataExtractor extractor : extractors) {
            String extractorName = extractorNames[extractorIndex];

            // Check if we already have complete data
            if (isDataComplete(entry)) {
                // Data is complete, no need to try more extractors
                break;
            }

            // Save current state before trying this extractor
            String currentMaturity = entry.getMaturityDate();
            double currentCoupon = entry.getNominalInterestRate();
            String currentName = entry.getBondName();

            // Create a temporary entry for this extractor to fill
            BondEntry tempEntry = new BondEntry(isin, entry.getTimestamp(), entry.getCurrency(),
                                               entry.getBidPrice(), entry.getAskPrice());

            // Try to extract data
            ExtractionResult result = extractor.fetchAndExtractBondData(isin, tempEntry, investmentAmount);

            if (result == ExtractionResult.NOT_FOUND) {
                // 400/404 error - try next extractor if available
                if (extractorIndex == 0) {
                    System.out.println("Attempting fallback to next source for " + isin + "...");
                }
                extractorIndex++;
                continue;
            } else if (result == ExtractionResult.ERROR) {
                // Other error - try next extractor if available
                extractorIndex++;
                continue;
            }

            // We got some data (COMPLETE or INCOMPLETE)

            allExtractorsFailed = false;

            if (extractorIndex > 0 && result == ExtractionResult.INCOMPLETE) {
                System.out.println("  -> " + extractorName + " provided partial data for " + isin);
            } else if (extractorIndex > 0 && result == ExtractionResult.COMPLETE) {
                System.out.println("  -> " + extractorName + " provided complete data for " + isin);
            }

            // Merge data: only fill in missing fields
            if (currentMaturity == null || currentMaturity.isEmpty()) {
                if (tempEntry.getMaturityDate() != null && !tempEntry.getMaturityDate().isEmpty()) {
                    entry.setMaturityDate(tempEntry.getMaturityDate());
                    entry.setRemainingDays(tempEntry.getRemainingDays());
                }
            }

            if (currentCoupon < 0) {
                if (tempEntry.getNominalInterestRate() >= 0) {
                    entry.setNominalInterestRate(tempEntry.getNominalInterestRate());
                }
            }

            if (currentName == null || currentName.isEmpty() || currentName.equals("Unknown Bond")) {
                if (tempEntry.getBondName() != null && !tempEntry.getBondName().isEmpty()) {
                    entry.setBondName(tempEntry.getBondName());
                }
            }

            // Recalculate yield with current data
            if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                try {
                    double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                    entry.setAskPriceValue(askPrice);
                    double yield = YieldCalculator.calculateYieldForInvestment(
                        askPrice, entry.getNominalInterestRate(),
                        entry.getRemainingDays(), investmentAmount);
                    entry.setYield(yield);
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing ask price for " + isin);
                }
            }

            extractorIndex++;
        }

        // Check results after trying all extractors
        if (allExtractorsFailed) {
            // All extractors returned NOT_FOUND - cache as HTTP_400_ERROR
            System.out.println("  -> All sources failed for " + isin + " - caching as HTTP_400_ERROR");
            maturityCache.storeHttp400Error(isin);
            return null;
        }

        // Check if we have the essential data after all attempts
        if (entry.getMaturityDate() == null || entry.getMaturityDate().isEmpty()) {
            // No maturity date means we can't use this bond
            return null;
        }

        // Store all bond data in cache for future use (even if it's a corporate bond)
        maturityCache.storeBondData(
            isin,
            entry.getMaturityDate(),
            entry.getNominalInterestRate() >= 0 ? entry.getNominalInterestRate() : null,
            entry.getBondName()
        );

        // Log if we're caching partial data
        boolean isPartialData = !isDataComplete(entry);
        if (isPartialData) {
            System.out.println("  -> Caching partial data for " + isin +
                             " (Maturity: " + (entry.getMaturityDate() != null && !entry.getMaturityDate().isEmpty()) +
                             ", Coupon: " + (entry.getNominalInterestRate() >= 0) +
                             ", Name: " + (entry.getBondName() != null && !entry.getBondName().isEmpty() && !entry.getBondName().equals("Unknown Bond")) + ")");
        }

        return entry;
    }

    /**
     * Checks if a BondEntry has complete data (maturity, coupon, and name)
     */
    private boolean isDataComplete(BondEntry entry) {
        boolean hasMaturity = entry.getMaturityDate() != null && !entry.getMaturityDate().isEmpty();
        boolean hasCoupon = entry.getNominalInterestRate() >= 0;
        boolean hasName = entry.getBondName() != null && !entry.getBondName().isEmpty()
                         && !entry.getBondName().equals("Unknown Bond");
        return hasMaturity && hasCoupon && hasName;
    }
}


