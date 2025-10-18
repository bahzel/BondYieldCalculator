package de.example.rendite;

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

            // Analyze bonds and calculate yields (only GR-ISINs)
            System.out.println();
            List<BondEntry> bonds = analyzer.bondValidationService.filterBonds(grEntries);

            // Display yield analysis results
            analyzer.displayYieldAnalysis(bonds);

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            e.printStackTrace();
        }
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
    private void displayYieldAnalysis(List<BondEntry> bonds) {
        System.out.println();
        System.out.println("=== Greek Government Bond Yield Analysis ===");
        System.out.printf("Analyzed bonds: %d%n", bonds.size());
        System.out.println();
        System.out.printf("%-15s %-8s %-10s %-10s %-12s %-8s %-10s%n",
                "ISIN", "Currency", "Bid Price", "Ask Price", "Maturity", "Days", "Yield");
        System.out.println("-".repeat(85));
        bonds.stream()
                .sorted(Comparator.comparing(BondEntry::getYield).reversed())
                .forEach(System.out::println);
    }
}
