package yield;

/**
 * Data class representing a bond entry with all relevant information
 */
public class BondEntry {
    private final String isin;
    private final String timestamp;
    private final String currency;
    private final String bidPrice;
    private final String askPrice;

    // Fields for extended calculation
    private int remainingDays = -1;
    private double yield = -1.0;
    private double askPriceValue = -1.0;
    private double nominalInterestRate = -1.0;
    private String maturityDate = "";

    public BondEntry(String isin, String timestamp, String currency,
                    String bidPrice, String askPrice) {
        this.isin = isin;
        this.timestamp = timestamp;
        this.currency = currency;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
    }

    // Basic getters
    public String getIsin() { return isin; }
    public String getTimestamp() { return timestamp; }
    public String getCurrency() { return currency; }
    public String getBidPrice() { return bidPrice; }
    public String getAskPrice() { return askPrice; }

    // Extended getters and setters
    public int getRemainingDays() { return remainingDays; }
    public void setRemainingDays(int remainingDays) { this.remainingDays = remainingDays; }

    public double getYield() { return yield; }
    public void setYield(double yield) { this.yield = yield; }

    public double getAskPriceValue() { return askPriceValue; }
    public void setAskPriceValue(double askPriceValue) { this.askPriceValue = askPriceValue; }

    public double getNominalInterestRate() { return nominalInterestRate; }
    public void setNominalInterestRate(double nominalInterestRate) { this.nominalInterestRate = nominalInterestRate; }

    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }

    @Override
    public String toString() {
        return String.format("%-15s %-8s %-10s %-10s %-12s %-8d %-10.3f%%",
                isin, currency, bidPrice, askPrice, maturityDate,
                Math.max(remainingDays, 0),
                yield != -1.0 ? yield : 0.0);
    }
}
