package yield;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Cache for storing and retrieving bond maturity dates to avoid unnecessary web requests
 */
public class MaturityCache {

    private static final String CACHE_DIR = System.getProperty("user.home") + File.separator + ".bondcache";
    private static final String CACHE_FILE = CACHE_DIR + File.separator + "maturity_cache.properties";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Map<String, LocalDate> cache;

    public MaturityCache() {
        this.cache = new HashMap<>();
        loadCache();
    }

    /**
     * Checks if an ISIN should be filtered out based on maturity date and max days
     * Returns true if the bond should be skipped (filtered out)
     */
    public boolean shouldFilterByMaturity(String isin, int maxDaysToMaturity) {
        if (maxDaysToMaturity <= 0) {
            return false; // No filter applied
        }

        LocalDate maturityDate = cache.get(isin);
        if (maturityDate == null) {
            return false; // Unknown maturity, don't filter - let web request determine
        }

        int daysToMaturity = DateCalculator.calculateRemainingDays(maturityDate.format(DATE_FORMAT));
        return daysToMaturity > maxDaysToMaturity;
    }

    /**
     * Stores the maturity date for an ISIN in the cache
     */
    public void storeMaturityDate(String isin, String maturityDateStr) {
        if (maturityDateStr == null || maturityDateStr.trim().isEmpty()) {
            return;
        }

        try {
            LocalDate maturityDate = LocalDate.parse(maturityDateStr, DATE_FORMAT);
            cache.put(isin, maturityDate);
            saveCache();
        } catch (DateTimeParseException e) {
            System.err.println("Warning: Could not parse maturity date '" + maturityDateStr + "' for ISIN " + isin);
        }
    }

    /**
     * Gets the cached maturity date for an ISIN
     */
    public LocalDate getMaturityDate(String isin) {
        return cache.get(isin);
    }

    /**
     * Returns the number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
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
                String dateStr = props.getProperty(isin);
                try {
                    LocalDate maturityDate = LocalDate.parse(dateStr, DATE_FORMAT);
                    cache.put(isin, maturityDate);
                } catch (DateTimeParseException e) {
                    System.err.println("Warning: Invalid cached date format for ISIN " + isin + ": " + dateStr);
                }
            }

            if (!cache.isEmpty()) {
                System.out.println("Loaded " + cache.size() + " maturity dates from cache.");
            }

        } catch (IOException e) {
            System.err.println("Warning: Could not load maturity cache: " + e.getMessage());
            createCacheDirectory();
        }
    }

    /**
     * Saves the cache to disk
     */
    private void saveCache() {
        createCacheDirectory();

        Properties props = new Properties();
        for (Map.Entry<String, LocalDate> entry : cache.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue().format(DATE_FORMAT));
        }

        try (OutputStream output = Files.newOutputStream(Paths.get(CACHE_FILE))) {
            props.store(output, "Bond Maturity Date Cache - Generated automatically");
        } catch (IOException e) {
            System.err.println("Warning: Could not save maturity cache: " + e.getMessage());
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
