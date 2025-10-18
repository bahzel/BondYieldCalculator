package yield;

/**
 * Utility class for yield calculations
 */
public class YieldCalculator {

    /**
     * Calculates yield to maturity (Yield to Maturity)
     * considering transaction costs
     */
    public static double calculateYield(double askPrice, double nominalRate, int remainingDays, double costs) {
        try {
            // Simple yield calculation (without complex YTM iteration)
            double yearsToMaturity = remainingDays / 365.0;

            // Total cost of purchase (ask price + transaction costs)
            double totalCosts = askPrice + costs;

            // Assumption: Nominal value = 100, annual interest payments
            double nominalValue = 100.0;
            double annualInterest = nominalValue * (nominalRate / 100.0);

            // Total interest payments until maturity
            double totalInterest = annualInterest * yearsToMaturity;

            // Total return = interest payments + repayment of nominal value
            double totalReturn = totalInterest + nominalValue;

            // Yield = (Total return / Total costs)^(1/years) - 1
            double yield = Math.pow(totalReturn / totalCosts, 1.0 / yearsToMaturity) - 1.0;

            return yield * 100.0; // In percent

        } catch (Exception e) {
            System.err.println("Error calculating yield: " + e.getMessage());
            return -1.0;
        }
    }
}
