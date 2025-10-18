package yield;

/**
 * Utility class for yield calculations
 */
public class YieldCalculator {

    /**
     * Calculates yield to maturity based on investment amount
     * considering fixed transaction costs of €2.50
     */
    public static double calculateYieldForInvestment(double askPrice, double nominalRate, int remainingDays, double investmentAmount) {
        try {
            // Fixed transaction costs of €2.50
            double transactionCosts = 2.50;

            // Calculate effective cost per bond (including proportional transaction costs)
            double bondsCount = investmentAmount / askPrice;
            double costPerBond = (investmentAmount + transactionCosts) / bondsCount;

            return calculateYield(costPerBond, nominalRate, remainingDays, 0.0);

        } catch (Exception e) {
            System.err.println("Error calculating yield for investment: " + e.getMessage());
            return -1.0;
        }
    }

    /**
     * Calculates yield to maturity (YTM) using the standard approximation formula
     * This matches the calculation method used by financial calculators and ChatGPT
     */
    public static double calculateYield(double purchasePrice, double nominalRate, int remainingDays, double costs) {
        try {
            double yearsToMaturity = remainingDays / 365.0;
            double totalCost = purchasePrice + costs;

            // Bond parameters
            double nominalValue = 100.0;
            double annualCoupon = nominalValue * (nominalRate / 100.0);

            // YTM Approximation Formula (same as ChatGPT):
            // YTM = (Coupon + (Face Value - Price) / Years) / ((Face Value + Price) / 2)
            double numerator = annualCoupon + ((nominalValue - totalCost) / yearsToMaturity);
            double denominator = (nominalValue + totalCost) / 2.0;

            double ytm = numerator / denominator;

            return ytm * 100.0; // Convert to percentage

        } catch (Exception e) {
            System.err.println("Error calculating yield: " + e.getMessage());
            return -1.0;
        }
    }
}
