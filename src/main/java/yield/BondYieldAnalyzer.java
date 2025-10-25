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

    private Map<String, BondEntry> filterGovernmentBonds(Map<String, BondEntry> allEntries) {
        Map<String, BondEntry> filteredEntries = new HashMap<>();
        for (Map.Entry<String, BondEntry> entry : allEntries.entrySet()) {
            String isin = entry.getKey();
            for (String prefix : SUPPORTED_ISIN_PREFIXES) {
                if (isin.startsWith(prefix)) {
                    filteredEntries.put(isin, entry.getValue());
                    break;
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
        BondYieldAnalyzer analyzer = null;

        try {
            analyzer = new BondYieldAnalyzer();

            // Get investment amount from user
            double investmentAmount = analyzer.getInvestmentAmountFromUser();

            // Get maximum days to maturity from user
            int maxDaysToMaturity = analyzer.getMaxDaysToMaturityFromUser();

            // Download the latest CSV file from gettex.de
            csvFilePath = analyzer.csvDownloadService.downloadLatestCsvFile();

            System.out.println("Reading CSV file: " + csvFilePath);
            Map<String, BondEntry> latestEntries = analyzer.csvReaderService.readCsvFile(csvFilePath);

            System.out.println("Total " + latestEntries.size() + " unique ISINs found.");

            // Filter only ISINs with supported prefixes (government bonds)
            Map<String, BondEntry> govEntries = analyzer.filterGovernmentBonds(latestEntries);

            System.out.println("Of which " + govEntries.size() + " ISINs start with one of the supported prefixes: " + SUPPORTED_ISIN_PREFIXES + ".");

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
            
            // Clean up temporary file
            if (csvFilePath != null) {
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
