package yield.scraping;

import java.net.http.HttpClient;
import yield.BondEntry;

/**
 * Abstract base class for bond data extractors
 * Provides common functionality for fetching and extracting bond data from various sources
 */
public abstract class BondDataExtractor {

    protected final HttpClient httpClient;

    protected BondDataExtractor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches and extracts bond data from the data source
     *
     * @param isin The ISIN of the bond
     * @param entry The BondEntry object to populate with data
     * @param investmentAmount The investment amount for yield calculation
     * @return ExtractionResult indicating the outcome of the extraction
     */
    public abstract ExtractionResult fetchAndExtractBondData(String isin, BondEntry entry, double investmentAmount);

    /**
     * Common method to calculate and set yield based on ask price
     *
     * @param entry The BondEntry object
     * @param investmentAmount The investment amount for yield calculation
     */
    protected void calculateAndSetYield(BondEntry entry, double investmentAmount) {
        try {
            double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
            entry.setAskPriceValue(askPrice);

            // Calculate yield based on investment amount
            if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                double yield = YieldCalculator.calculateYieldForInvestment(
                    askPrice,
                    entry.getNominalInterestRate(),
                    entry.getRemainingDays(),
                    investmentAmount
                );
                entry.setYield(yield);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
        }
    }

    /**
     * Helper method to extract and set maturity date and calculate remaining days
     *
     * @param entry The BondEntry object
     * @param maturityDate The maturity date in DD.MM.YYYY format
     */
    protected void setMaturityAndCalculateRemainingDays(BondEntry entry, String maturityDate) {
        entry.setMaturityDate(maturityDate);
        int remainingDays = DateCalculator.calculateRemainingDays(maturityDate);
        entry.setRemainingDays(remainingDays);
    }
}

