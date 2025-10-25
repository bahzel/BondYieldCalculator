package yield.cache;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import yield.BondEntry;
import yield.scraping.DateCalculator;

/**
 * Cache for storing and retrieving bond data to avoid unnecessary web requests
 * Stores: maturity dates, nominal interest rates, bond names, and HTTP 400 errors
 * Auto-saves every 10 entries to prevent data loss on program interruption
 */
public class MaturityCache {

    private static final String CACHE_DIR = System.getProperty("user.home") + File.separator + ".bondcache";
    private static final String CACHE_FILE = CACHE_DIR + File.separator + "maturity_cache.properties";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String HTTP_400_MARKER = "HTTP_400_ERROR";
    private static final String DELIMITER = "|||"; // Delimiter for multiple fields
    private static final int BATCH_SAVE_THRESHOLD = 10; // Save every 10 entries

    private final Map<String, BondCacheEntry> cache;
    private int unsavedChanges = 0;

    public MaturityCache() {
        this.cache = new HashMap<>();
        loadCache();
    }

    /**
     * Inner class to hold all cached bond data
     */
    private static class BondCacheEntry {
        LocalDate maturityDate;
        Double nominalInterestRate;
        String bondName;
        boolean isHttp400Error;

        BondCacheEntry() {
            this.isHttp400Error = false;
        }
    }

    /**
     * Checks if an ISIN should be filtered out based on maturity date and max days
     * Returns true if the bond should be skipped (filtered out)
     */
    public boolean shouldFilterByMaturity(String isin, int maxDaysToMaturity) {
        BondCacheEntry entry = cache.get(isin);

        // Check if ISIN had HTTP 400 error before
        if (entry != null && entry.isHttp400Error) {
            return true; // Skip ISINs that previously returned HTTP 400
        }

        if (maxDaysToMaturity <= 0) {
            return false; // No filter applied
        }

        if (entry == null || entry.maturityDate == null) {
            return false; // Unknown maturity, don't filter - let web request determine
        }

        int daysToMaturity = DateCalculator.calculateRemainingDays(entry.maturityDate.format(DATE_FORMAT));
        return daysToMaturity > maxDaysToMaturity;
    }

    /**
     * Checks if we have complete cached data for an ISIN (can skip web request entirely)
     */
    public boolean hasCompleteData(String isin) {
        BondCacheEntry entry = cache.get(isin);
        return entry != null &&
               !entry.isHttp400Error &&
               entry.maturityDate != null &&
               entry.nominalInterestRate != null &&
               entry.bondName != null;
    }

    /**
     * Stores an ISIN as HTTP 400 error in the cache
     */
    public void storeHttp400Error(String isin) {
        BondCacheEntry entry = cache.computeIfAbsent(isin, k -> new BondCacheEntry());
        entry.isHttp400Error = true;
        incrementAndSave();
    }

    /**
     * Stores complete bond data in the cache
     */
    public void storeBondData(String isin, String maturityDateStr, Double nominalInterestRate, String bondName) {
        BondCacheEntry entry = cache.computeIfAbsent(isin, k -> new BondCacheEntry());

        boolean hasMaturityDate = false;
        boolean hasNominalRate = false;
        boolean hasBondName = false;

        if (maturityDateStr != null && !maturityDateStr.trim().isEmpty()) {
            try {
                entry.maturityDate = LocalDate.parse(maturityDateStr, DATE_FORMAT);
                hasMaturityDate = true;
            } catch (DateTimeParseException e) {
                System.err.println("Warning: Could not parse maturity date '" + maturityDateStr + "' for ISIN " + isin);
            }
        }

        if (nominalInterestRate != null && nominalInterestRate >= 0) {
            entry.nominalInterestRate = nominalInterestRate;
            hasNominalRate = true;
        }

        if (bondName != null && !bondName.trim().isEmpty() && !"Unknown Bond".equals(bondName)) {
            entry.bondName = bondName;
            hasBondName = true;
        }

        // Log incomplete data sets for later analysis
        if (!hasMaturityDate || !hasNominalRate || !hasBondName) {
            List<String> missingFields = new ArrayList<>();
            if (!hasMaturityDate) missingFields.add("maturity date");
            if (!hasNominalRate) missingFields.add("nominal interest rate");
            if (!hasBondName) missingFields.add("bond name");
            
            System.out.println();
            System.out.println("WARNING: Incomplete data cached for ISIN " + isin + 
                             " - Missing: " + String.join(", ", missingFields));
        }

        incrementAndSave();
    }

    /**
     * Increments unsaved changes counter and saves if threshold is reached
     */
    private void incrementAndSave() {
        unsavedChanges++;
        if (unsavedChanges >= BATCH_SAVE_THRESHOLD) {
            saveCache();
            unsavedChanges = 0;
        }
    }

    /**
     * Forces an immediate save of the cache (call at program end)
     */
    public void forceSave() {
        if (unsavedChanges > 0) {
            saveCache();
            unsavedChanges = 0;
        }
    }

    /**
     * Loads cached bond data into a BondEntry if available
     * Returns true if data was loaded from cache
     */
    public boolean loadCachedData(BondEntry bondEntry) {
        BondCacheEntry entry = cache.get(bondEntry.getIsin());
        if (entry == null || entry.isHttp400Error) {
            return false;
        }

        boolean dataLoaded = false;

        if (entry.maturityDate != null) {
            String maturityDateStr = entry.maturityDate.format(DATE_FORMAT);
            bondEntry.setMaturityDate(maturityDateStr);
            int remainingDays = DateCalculator.calculateRemainingDays(maturityDateStr);
            bondEntry.setRemainingDays(remainingDays);
            dataLoaded = true;
        }

        if (entry.nominalInterestRate != null) {
            bondEntry.setNominalInterestRate(entry.nominalInterestRate);
            dataLoaded = true;
        }

        if (entry.bondName != null) {
            bondEntry.setBondName(entry.bondName);
            dataLoaded = true;
        }

        return dataLoaded;
    }

    /**
     * Returns the number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Returns statistics about cached data
     */
    public String getCacheStats() {
        int http400Count = 0;
        int completeDataCount = 0;
        int partialDataCount = 0;

        for (BondCacheEntry entry : cache.values()) {
            if (entry.isHttp400Error) {
                http400Count++;
            } else if (entry.maturityDate != null && entry.nominalInterestRate != null && entry.bondName != null) {
                completeDataCount++;
            } else if (entry.maturityDate != null || entry.nominalInterestRate != null || entry.bondName != null) {
                partialDataCount++;
            }
        }

        return String.format("Complete bond data: %d, Partial data: %d, HTTP 400 errors: %d",
                           completeDataCount, partialDataCount, http400Count);
    }

    /**
     * Loads the cache from disk
     */
    private void loadCache() {
        Path cacheFile = Paths.get(CACHE_FILE);
        if (!Files.exists(cacheFile)) {
            createCacheDirectory();
            return;
        }

        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(cacheFile)) {
            props.load(input);

            for (String isin : props.stringPropertyNames()) {
                String value = props.getProperty(isin);
                BondCacheEntry entry = new BondCacheEntry();

                // Check if this is an HTTP 400 error marker (old format)
                if (HTTP_400_MARKER.equals(value)) {
                    entry.isHttp400Error = true;
                    cache.put(isin, entry);
                    continue;
                }

                // Check if this is old format (just a date)
                if (!value.contains(DELIMITER)) {
                    try {
                        entry.maturityDate = LocalDate.parse(value, DATE_FORMAT);
                        cache.put(isin, entry);
                    } catch (DateTimeParseException e) {
                        System.err.println("Warning: Invalid cached date format for ISIN " + isin + ": " + value);
                    }
                    continue;
                }

                // New format: HTTP_400|||maturityDate|||nominalRate|||bondName
                String[] parts = value.split("\\|\\|\\|", -1);

                if (parts.length >= 1 && HTTP_400_MARKER.equals(parts[0])) {
                    entry.isHttp400Error = true;
                } else {
                    // Parse maturity date (parts[0])
                    if (parts.length >= 1 && !parts[0].isEmpty()) {
                        try {
                            entry.maturityDate = LocalDate.parse(parts[0], DATE_FORMAT);
                        } catch (DateTimeParseException e) {
                            System.err.println("Warning: Invalid cached date format for ISIN " + isin + ": " + parts[0]);
                        }
                    }

                    // Parse nominal interest rate (parts[1])
                    if (parts.length >= 2 && !parts[1].isEmpty()) {
                        try {
                            entry.nominalInterestRate = Double.parseDouble(parts[1]);
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid nominal rate for ISIN " + isin + ": " + parts[1]);
                        }
                    }

                    // Parse bond name (parts[2])
                    if (parts.length >= 3 && !parts[2].isEmpty()) {
                        entry.bondName = parts[2];
                    }
                }

                cache.put(isin, entry);
            }

            if (!cache.isEmpty()) {
                System.out.println("Loaded cache: " + getCacheStats());
            }

        } catch (IOException e) {
            System.err.println("Warning: Could not load cache: " + e.getMessage());
            createCacheDirectory();
        }
    }

    /**
     * Saves the cache to disk
     */
    private void saveCache() {
        createCacheDirectory();

        Properties props = new Properties();

        for (Map.Entry<String, BondCacheEntry> mapEntry : cache.entrySet()) {
            String isin = mapEntry.getKey();
            BondCacheEntry entry = mapEntry.getValue();

            if (entry.isHttp400Error) {
                // Store HTTP 400 marker
                props.setProperty(isin, HTTP_400_MARKER);
            } else {
                // Store in new format: maturityDate|||nominalRate|||bondName
                String sb = (entry.maturityDate != null ? entry.maturityDate.format(DATE_FORMAT) : "") +
                        DELIMITER +
                        (entry.nominalInterestRate != null ? entry.nominalInterestRate.toString() : "") +
                        DELIMITER +
                        (entry.bondName != null ? entry.bondName : "");

                props.setProperty(isin, sb);
            }
        }

        try (OutputStream output = Files.newOutputStream(Paths.get(CACHE_FILE))) {
            props.store(output, "Bond Data Cache - Generated automatically - Auto-saved every " + BATCH_SAVE_THRESHOLD + " entries");
        } catch (IOException e) {
            System.err.println("Warning: Could not save cache: " + e.getMessage());
        }
    }

    /**
     * Creates the cache directory if it doesn't exist
     */
    private void createCacheDirectory() {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not create cache directory: " + e.getMessage());
        }
    }
}

