package yield;

import java.io.IOException;
import java.util.*;

import yield.csv.CsvDownloadService;
import yield.csv.CsvReaderService;

/**
 * Main class for analyzing bond yields from CSV data
 * Orchestrates the process of reading CSV files, filtering Greek bonds, and displaying yield results
 */
public class BondYieldAnalyzer {

    private final CsvReaderService csvReaderService;
    private final CsvDownloadService csvDownloadService;
    private final BondDataService bondDataService;

    public BondYieldAnalyzer() {
        this.csvReaderService = new CsvReaderService();
        this.csvDownloadService = new CsvDownloadService();
        this.bondDataService = new BondDataService();
    }

    /**
     * Filters entries to keep only government bonds (ISINs starting with supported prefixes)
     */
    private static final Set<String> SUPPORTED_ISIN_PREFIXES = new HashSet<>(Arrays.asList(
        "BE", "FR", "NL", "AT", "PT", "US", "XS", "CA", "AU", "CH", "ES", "DK", "EU", "GB", "HK", "IE", "IT", "LU", "MT", "MX", "NZ", "NO", "PL", "RO", "SE", "SG", "SK", "SI", "CZ", "HU", "GR"
    ));

    private Map<String, BondEntry> filterGovernmentBonds(Map<String, BondEntry> allEntries, boolean onlyEuro) {
        Map<String, BondEntry> filteredEntries = new HashMap<>();
        for (Map.Entry<String, BondEntry> entry : allEntries.entrySet()) {
            String isin = entry.getKey();
            BondEntry bondEntry = entry.getValue();

            // Check if ISIN starts with supported prefix
            boolean matchesPrefix = false;
            for (String prefix : SUPPORTED_ISIN_PREFIXES) {
                if (isin.startsWith(prefix)) {
                    matchesPrefix = true;
                    break;
                }
            }

            // Apply currency filter if needed
            if (matchesPrefix) {
                if (!onlyEuro || "EUR".equals(bondEntry.getCurrency())) {
                    filteredEntries.put(isin, bondEntry);
                }
            }
        }
        return filteredEntries;
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        String csvFilePath = null;
        boolean isDownloadedFile = false;
        BondYieldAnalyzer analyzer = null;

        try {
            analyzer = new BondYieldAnalyzer();

            // Get investment amount from user
            double investmentAmount = analyzer.getInvestmentAmountFromUser();

            // Get maximum days to maturity from user
            int maxDaysToMaturity = analyzer.getMaxDaysToMaturityFromUser();

            // Ask user if they want only EUR or all currencies
            boolean onlyEuro = analyzer.askCurrencyFilter();

            // Ask user if they want to recheck cached HTTP 400 errors
            analyzer.askRecheckHttp400Errors();

            // Get CSV file path (download or local)
            csvFilePath = analyzer.getCsvFilePath();
            // Check if it's a temporary file (downloaded files are in temp directory)
            isDownloadedFile = csvFilePath.contains(System.getProperty("java.io.tmpdir")) ||
                               csvFilePath.contains("AppData\\Local\\Temp");

            System.out.println("Reading CSV file: " + csvFilePath);
            Map<String, BondEntry> latestEntries = analyzer.csvReaderService.readCsvFile(csvFilePath);

            System.out.println("Total " + latestEntries.size() + " unique ISINs found.");

            // Filter only ISINs with supported prefixes (government bonds) and optionally by currency
            Map<String, BondEntry> govEntries = analyzer.filterGovernmentBonds(latestEntries, onlyEuro);

            System.out.println("Of which " + govEntries.size() + " ISINs start with one of the supported prefixes: " + SUPPORTED_ISIN_PREFIXES +
                             (onlyEuro ? " and are in EUR currency." : "."));

            if (govEntries.isEmpty()) {
                System.out.println("No ISINs starting with supported prefixes found.");
                return;
            }

            // Analyze bonds and calculate yields with investment amount and maturity filter
            System.out.println();
            List<BondEntry> bonds = analyzer.bondDataService.filterBonds(govEntries, investmentAmount, maxDaysToMaturity);

            // Display yield analysis results
            analyzer.displayYieldAnalysis(bonds, investmentAmount, maxDaysToMaturity);

        } catch (IOException e) {
            System.err.println("Error processing CSV file: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Save cache before exiting (also saves any unsaved changes on interruption)
            if (analyzer != null) {
                try {
                    analyzer.bondDataService.saveCache();
                } catch (Exception e) {
                    // Ignore cache save errors
                }
            }
            
            // Clean up temporary file (only if it was downloaded)
            if (csvFilePath != null && isDownloadedFile) {
                try {
                    new CsvDownloadService().deleteTempFile(csvFilePath);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Gets the investment amount from user input
     */
    private double getInvestmentAmountFromUser() {
        Scanner scanner = new Scanner(System.in);
        double investmentAmount = 0.0;
        boolean validInput = false;

        System.out.println();
        System.out.println("=== Bond Yield Calculator ===");
        System.out.println();

        while (!validInput) {
            System.out.print("Please enter your desired investment amount in EUR: ");
            try {
                String input = scanner.nextLine().replace(",", ".");
                investmentAmount = Double.parseDouble(input);

                if (investmentAmount <= 0) {
                    System.out.println("Investment amount must be greater than 0. Please try again.");
                } else if (investmentAmount < 100) {
                    System.out.println("Warning: Investment amount is quite low (< €100). Continue anyway? (y/n): ");
                    String confirm = scanner.nextLine().toLowerCase();
                    if (confirm.equals("y") || confirm.equals("yes")) {
                        validInput = true;
                    }
                } else {
                    validInput = true;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number (e.g., 1000 or 1000.50).");
            }
        }

        System.out.println();
        System.out.println("Investment amount set to: €" + String.format("%.2f", investmentAmount));
        System.out.println("Transaction costs: fixed €2.50 per transaction");
        System.out.println("Total transaction costs: €2.50");
        System.out.println();

        return investmentAmount;
    }

    /**
     * Gets the maximum days to maturity from user input
     */
    private int getMaxDaysToMaturityFromUser() {
        Scanner scanner = new Scanner(System.in);
        int maxDays = 0;
        boolean validInput = false;

        while (!validInput) {
            System.out.print("Please enter maximum days to maturity (or 0 for no limit): ");
            try {
                String input = scanner.nextLine().trim();
                maxDays = Integer.parseInt(input);

                if (maxDays < 0) {
                    System.out.println("Days must be 0 or greater. Please try again.");
                } else {
                    validInput = true;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number (e.g., 365 for 1 year, 0 for no limit).");
            }
        }

        if (maxDays == 0) {
            System.out.println("No maturity limit set - all bonds will be considered.");
        } else {
            System.out.println("Maximum days to maturity set to: " + maxDays + " days (" + String.format("%.1f", maxDays / 365.25) + " years)");
        }
        System.out.println();

        return maxDays;
    }

    /**
     * Asks user if they want only EUR or all currencies
     */
    private boolean askCurrencyFilter() {
        Scanner scanner = new Scanner(System.in);
        boolean validInput = false;
        boolean onlyEuro = false;

        while (!validInput) {
            System.out.print("Do you want to see only EUR bonds or all currencies? (EUR/all): ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("eur") || input.equals("euro") || input.equals("e")) {
                onlyEuro = true;
                validInput = true;
                System.out.println("Filter set to: EUR only");
            } else if (input.equals("all") || input.equals("a")) {
                validInput = true;
                System.out.println("Filter set to: All currencies");
            } else {
                System.out.println("Invalid input. Please enter 'EUR' or 'all'.");
            }
        }

        System.out.println();
        return onlyEuro;
    }

    /**
     * Asks user if they want to recheck cached HTTP 400 errors
     */
    private void askRecheckHttp400Errors() {
        int errorCount = bondDataService.getHttp400ErrorCount();

        if (errorCount == 0) {
            // No errors in cache, skip question
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Found " + errorCount + " cached HTTP 400/404 error(s) (ISINs that were not found previously).");
        System.out.print("Do you want to recheck these ISINs? (y/n): ");

        String input = scanner.nextLine().trim().toLowerCase();

        if (input.equals("y") || input.equals("yes")) {
            int removedCount = bondDataService.clearHttp400Errors();
            System.out.println("Removed " + removedCount + " HTTP 400/404 error(s) from cache. These ISINs will be rechecked.");
        } else {
            System.out.println("Keeping cached HTTP 400/404 errors. These ISINs will be skipped.");
        }
        System.out.println();
    }

    /**
     * Asks user if they want to download CSV or use local file
     * Returns the path to the CSV file (either downloaded or local)
     */
    private String getCsvFilePath() throws IOException {
        Scanner scanner = new Scanner(System.in);
        boolean validInput = false;
        String csvFilePath = null;

        while (!validInput) {
            System.out.print("Do you want to download the CSV from gettex.de or use a local file? (download/local): ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("download") || input.equals("d")) {
                // Download CSV
                System.out.println("Note: On Sundays, no file is available for download from gettex.de.");
                csvFilePath = csvDownloadService.downloadLatestCsvFile();
                validInput = true;
            } else if (input.equals("local") || input.equals("l")) {
                // Use local file
                boolean fileFound = false;
                while (!fileFound) {
                    System.out.print("Please enter the path to your local CSV file: ");
                    String filePath = scanner.nextLine().trim();

                    // Remove quotes if user wrapped path in quotes
                    if (filePath.startsWith("\"") && filePath.endsWith("\"")) {
                        filePath = filePath.substring(1, filePath.length() - 1);
                    }

                    java.io.File file = new java.io.File(filePath);
                    if (file.exists() && file.isFile()) {
                        csvFilePath = filePath;
                        fileFound = true;
                        validInput = true;
                        System.out.println("Using local file: " + csvFilePath);
                    } else {
                        System.out.println("File not found or not a file. Please try again.");
                        System.out.print("Try again or switch to download? (retry/download): ");
                        String retry = scanner.nextLine().trim().toLowerCase();
                        if (retry.equals("download") || retry.equals("d")) {
                            csvFilePath = csvDownloadService.downloadLatestCsvFile();
                            fileFound = true;
                            validInput = true;
                        }
                    }
                }
            } else {
                System.out.println("Invalid input. Please enter 'download' or 'local'.");
            }
        }

        System.out.println();
        return csvFilePath;
    }

    /**
     * Displays the bond yield analysis results in a formatted table
     */
    private void displayYieldAnalysis(List<BondEntry> bonds, double investmentAmount, int maxDaysToMaturity) {
        System.out.println();
        System.out.println("=== Government Bond Yield Analysis ===");
        System.out.println("Investment Amount: €" + String.format("%.2f", investmentAmount));
        if (maxDaysToMaturity > 0) {
            System.out.println("Maximum Days to Maturity: " + maxDaysToMaturity + " days (" + String.format("%.1f", maxDaysToMaturity / 365.25) + " years)");
        } else {
            System.out.println("Maximum Days to Maturity: No limit");
        }
        System.out.println("Analyzed bonds: " + bonds.size());
        System.out.println();
        System.out.printf("%-15s %-35s %-8s %-10s %-10s %-12s %-8s %-10s%n",
                "ISIN", "Bond Name", "Currency", "Bid Price", "Ask Price", "Maturity", "Days", "Yield");
        System.out.println("-".repeat(120));
        bonds.stream()
                .sorted(Comparator.comparing(BondEntry::getYield).reversed())
                .forEach(System.out::println);

        System.out.println();
        System.out.println("Note: Yields are calculated based on your investment amount of €" + String.format("%.2f", investmentAmount));
        System.out.println("Fixed transaction costs of €2.50 are included in the calculation.");
    }
}
