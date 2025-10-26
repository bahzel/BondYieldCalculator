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
     */
    public BondEntry isBondAndExtractData(String isin, BondEntry entry, double investmentAmount) {
        // Try to fetch from comdirect first
        ComdirectDataExtractor comdirectExtractor = new ComdirectDataExtractor(httpClient);
        yield.scraping.ExtractionResult comdirectResult = comdirectExtractor.fetchAndExtractBondData(isin, entry, investmentAmount);

        // Determine if we need to check Bondblox
        boolean needsBondblox = false;

        if (comdirectResult == yield.scraping.ExtractionResult.NOT_FOUND) {
            // Case 1: Comdirect returned 400/404 - always check Bondblox
            needsBondblox = true;
            System.out.println("Attempting fallback to bondblox.com for " + isin + "...");
        } else if (comdirectResult == yield.scraping.ExtractionResult.INCOMPLETE) {
            // Case 2: Comdirect returned partial data - check Bondblox for merge
            needsBondblox = true;
            System.out.println("Attempting to complete data from bondblox.com for " + isin + "...");
        }
        // Case 3: If comdirectResult is COMPLETE, we don't check Bondblox

        ExtractionResult bondbloxResult;
        BondEntry bondbloxEntry;

        if (needsBondblox) {
            // Save comdirect data if any
            String comdirectMaturity = entry.getMaturityDate();
            double comdirectCoupon = entry.getNominalInterestRate();
            String comdirectName = entry.getBondName();

            // Create a temporary entry for Bondblox to fill
            bondbloxEntry = new BondEntry(isin, entry.getTimestamp(), entry.getCurrency(),
                                         entry.getBidPrice(), entry.getAskPrice());
            BondbloxDataExtractor bondbloxExtractor = new BondbloxDataExtractor(httpClient);
            bondbloxResult = bondbloxExtractor.fetchAndExtractBondData(isin, bondbloxEntry, investmentAmount);

            if (comdirectResult == yield.scraping.ExtractionResult.NOT_FOUND) {
                // Case 1: Comdirect had 400/404
                if (bondbloxResult == yield.scraping.ExtractionResult.NOT_FOUND) {
                    // Case 1a: Both failed - cache as HTTP_400_ERROR and skip
                    System.out.println("  -> Both sources failed for " + isin + " - caching as HTTP_400_ERROR");
                    maturityCache.storeHttp400Error(isin);
                    return null;
                } else if (bondbloxResult == yield.scraping.ExtractionResult.COMPLETE ||
                           bondbloxResult == yield.scraping.ExtractionResult.INCOMPLETE) {
                    // Case 1b: Bondblox has data (even if incomplete) - use it
                    System.out.println("  -> Using Bondblox data for " + isin);
                    entry.setMaturityDate(bondbloxEntry.getMaturityDate());
                    entry.setRemainingDays(bondbloxEntry.getRemainingDays());
                    if (bondbloxEntry.getNominalInterestRate() >= 0) {
                        entry.setNominalInterestRate(bondbloxEntry.getNominalInterestRate());
                    }
                    if (bondbloxEntry.getBondName() != null && !bondbloxEntry.getBondName().isEmpty()) {
                        entry.setBondName(bondbloxEntry.getBondName());
                    }
                    entry.setYield(bondbloxEntry.getYield());
                }
            } else {
                // Case 2: Comdirect had partial data
                if (bondbloxResult == yield.scraping.ExtractionResult.NOT_FOUND) {
                    // Case 2a: Bondblox failed - use Comdirect data
                    System.out.println("  -> Bondblox failed, using Comdirect data for " + isin);
                    // Data is already in entry from comdirect
                } else if (bondbloxResult == yield.scraping.ExtractionResult.COMPLETE ||
                           bondbloxResult == yield.scraping.ExtractionResult.INCOMPLETE) {
                    // Case 2b: Bondblox has data - merge (fill in missing fields only)
                    System.out.println("  -> Merging Comdirect and Bondblox data for " + isin);

                    // Only override if comdirect didn't have it
                    if (comdirectMaturity == null || comdirectMaturity.isEmpty()) {
                        entry.setMaturityDate(bondbloxEntry.getMaturityDate());
                        entry.setRemainingDays(bondbloxEntry.getRemainingDays());
                    }
                    if (comdirectCoupon < 0 && bondbloxEntry.getNominalInterestRate() >= 0) {
                        entry.setNominalInterestRate(bondbloxEntry.getNominalInterestRate());
                    }
                    if ((comdirectName == null || comdirectName.isEmpty() || comdirectName.equals("Unknown Bond")) &&
                        bondbloxEntry.getBondName() != null && !bondbloxEntry.getBondName().isEmpty()) {
                        entry.setBondName(bondbloxEntry.getBondName());
                    }

                    // Recalculate yield with merged data
                    try {
                        double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                        entry.setAskPriceValue(askPrice);

                        if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                            double yield = YieldCalculator.calculateYieldForInvestment(
                                askPrice, entry.getNominalInterestRate(),
                                entry.getRemainingDays(), investmentAmount);
                            entry.setYield(yield);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing ask price for " + isin);
                    }
                }
            }
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

        return entry;
    }
}


