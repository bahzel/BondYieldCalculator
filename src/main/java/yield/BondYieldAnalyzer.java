package yield;

import java.io.IOException;
import java.util.*;

/**
 * Main class for analyzing bond yields from CSV data
 * Orchestrates the process of reading CSV files, filtering Greek bonds, and displaying yield results
 */
public class BondYieldAnalyzer {

    private final CsvReaderService csvReaderService;
    private final BondValidationService bondValidationService;

    public BondYieldAnalyzer() {
        this.csvReaderService = new CsvReaderService();
        this.bondValidationService = new BondValidationService();
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        String csvFilePath = "C:\\tmp\\anleihen\\pretrade.20251013.14.45.mund.csv.gz";

        try {
            BondYieldAnalyzer analyzer = new BondYieldAnalyzer();

            // Get investment amount from user
            double investmentAmount = analyzer.getInvestmentAmountFromUser();

            System.out.println("Reading CSV file: " + csvFilePath);
            Map<String, BondEntry> latestEntries = analyzer.csvReaderService.readCsvFile(csvFilePath);

            System.out.println("Total " + latestEntries.size() + " unique ISINs found.");

            // Filter only ISINs with "GR" (Greek bonds)
            Map<String, BondEntry> grEntries = analyzer.filterGreekBonds(latestEntries);

            System.out.println("Of which " + grEntries.size() + " ISINs start with 'GR'.");

            if (grEntries.isEmpty()) {
                System.out.println("No ISINs starting with 'GR' found.");
                return;
            }

            // Analyze bonds and calculate yields with investment amount (only GR-ISINs)
            System.out.println();
            List<BondEntry> bonds = analyzer.bondValidationService.filterBonds(grEntries, investmentAmount);

            // Display yield analysis results
            analyzer.displayYieldAnalysis(bonds, investmentAmount);

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            e.printStackTrace();
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
        System.out.println("Transaction costs will be calculated as 0.25% of investment (minimum €2.50)");
        System.out.println("Estimated transaction costs: €" + String.format("%.2f", Math.max(investmentAmount * 0.0025, 2.50)));
        System.out.println();

        return investmentAmount;
    }

    /**
     * Filters entries to keep only Greek bonds (ISINs starting with "GR")
     */
    private Map<String, BondEntry> filterGreekBonds(Map<String, BondEntry> allEntries) {
        Map<String, BondEntry> grEntries = new HashMap<>();
        for (Map.Entry<String, BondEntry> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("GR")) {
                grEntries.put(entry.getKey(), entry.getValue());
            }
        }
        return grEntries;
    }

    /**
     * Displays the bond yield analysis results in a formatted table
     */
    private void displayYieldAnalysis(List<BondEntry> bonds, double investmentAmount) {
        System.out.println();
        System.out.println("=== Greek Government Bond Yield Analysis ===");
        System.out.printf("Investment Amount: €%.2f%n", investmentAmount);
        System.out.printf("Analyzed bonds: %d%n", bonds.size());
        System.out.println();
        System.out.printf("%-15s %-8s %-10s %-10s %-12s %-8s %-10s%n",
                "ISIN", "Currency", "Bid Price", "Ask Price", "Maturity", "Days", "Yield");
        System.out.println("-".repeat(85));
        bonds.stream()
                .sorted(Comparator.comparing(BondEntry::getYield).reversed())
                .forEach(System.out::println);

        System.out.println();
        System.out.println("Note: Yields are calculated based on your investment amount of €" + String.format("%.2f", investmentAmount));
        System.out.println("Transaction costs are included in the calculation.");
    }
}
