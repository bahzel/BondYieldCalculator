package yield;

/**
 * Service for extracting bond data from HTML content and calculating yields
 */
public class BondDataExtractor {

    /**
     * Extracts bond data from HTML and calculates remaining time and yield
     */
    public void extractBondData(BondEntry entry, String htmlContent, double investmentAmount) {
        try {
            // DEBUG: Output relevant HTML part for maturity
            int maturityStart = htmlContent.indexOf("lligkeit"); // Search for "lligkeit" to find both "Fälligkeit" and "F&auml;lligkeit"
            if (maturityStart >= 0) {
                int contextStart = Math.max(0, maturityStart - 100);
                int contextEnd = Math.min(htmlContent.length(), maturityStart + 300);
                String context = htmlContent.substring(contextStart, contextEnd);
                System.out.println("DEBUG - HTML around maturity:");
                System.out.println(context);
                System.out.println("---");
            }

            // Extract maturity - considers both "Fälligkeit" and "F&auml;lligkeit"
            String maturityPattern = "<th[^>]*>F(?:ä|&auml;)lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(maturityPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String maturity = matcher.group(1).trim();
                entry.setMaturityDate(maturity);
                System.out.println("DEBUG - Maturity found: " + maturity);

                // Calculate remaining days (precise via date)
                int remainingDays = DateCalculator.calculateRemainingDays(maturity);
                entry.setRemainingDays(remainingDays);
                System.out.println("DEBUG - Remaining days calculated: " + remainingDays + " days");
            } else {
                System.out.println("DEBUG - Maturity NOT found with pattern: " + maturityPattern);

                // Extended fallback patterns for different HTML variants
                String[] fallbackPatterns = {
                    "<th[^>]*>F&auml;lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    "<th[^>]*>Fälligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    ">[^<]*(?:F&auml;lligkeit|Fälligkeit)[^<]*</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>"
                };

                for (String fallbackPattern : fallbackPatterns) {
                    pattern = java.util.regex.Pattern.compile(fallbackPattern);
                    matcher = pattern.matcher(htmlContent);
                    if (matcher.find()) {
                        String maturity = matcher.group(1).trim();
                        entry.setMaturityDate(maturity);
                        System.out.println("DEBUG - Maturity found with fallback: " + maturity + " (Pattern: " + fallbackPattern + ")");

                        int remainingDays = DateCalculator.calculateRemainingDays(maturity);
                        entry.setRemainingDays(remainingDays);
                        System.out.println("DEBUG - Remaining days calculated: " + remainingDays + " days");
                        break;
                    }
                }
            }

            // Extract nominal interest rate
            extractNominalInterestRate(entry, htmlContent);

            // Take ask price from original CSV data (askPrice)
            try {
                double askPrice = Double.parseDouble(entry.getAskPrice().replace(",", "."));
                entry.setAskPriceValue(askPrice);

                // Calculate yield based on investment amount - now also works for zero-coupon bonds (0% nominal rate)
                if (entry.getRemainingDays() > 0 && entry.getNominalInterestRate() >= 0) {
                    double yield = YieldCalculator.calculateYieldForInvestment(askPrice, entry.getNominalInterestRate(),
                                                entry.getRemainingDays(), investmentAmount);
                    entry.setYield(yield);
                    System.out.println("DEBUG - Yield calculated for investment amount " + String.format("%.2f", investmentAmount) + ": " + String.format("%.3f", yield) + "%");
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing ask price from CSV for " + entry.getIsin() + ": " + entry.getAskPrice());
            }

        } catch (Exception e) {
            System.err.println("Error extracting bond data for " + entry.getIsin() + ": " + e.getMessage());
        }
    }

    /**
     * Extracts nominal interest rate from HTML content
     */
    private void extractNominalInterestRate(BondEntry entry, String htmlContent) {
        // DEBUG: Output relevant HTML part for nominal interest rate
        int nominalStart = htmlContent.indexOf("Nominalzinssatz");
        if (nominalStart >= 0) {
            int contextStart = Math.max(0, nominalStart - 100);
            int contextEnd = Math.min(htmlContent.length(), nominalStart + 300);
            String context = htmlContent.substring(contextStart, contextEnd);
            System.out.println("DEBUG - HTML around nominal interest rate:");
            System.out.println(context);
            System.out.println("---");
        }

        // Extract nominal interest rate (with &#160; HTML entity)
        String nominalRatePattern = "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,.]+)\\s*&#160;\\s*%</td>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(nominalRatePattern);
        java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

        if (matcher.find()) {
            String nominalRateStr = matcher.group(1).replace(",", ".");
            double nominalRate = Double.parseDouble(nominalRateStr);
            entry.setNominalInterestRate(nominalRate);
            System.out.println("DEBUG - Nominal interest rate found: " + nominalRate + "%");
        } else {
            System.out.println("DEBUG - Nominal interest rate NOT found with pattern: " + nominalRatePattern);

            // Fallback: Try other variants
            String[] fallbackPatterns = {
                "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*&nbsp;\\s*%</td>",
                "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*%</td>",
                "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)[^0-9]*%</td>"
            };

            for (String fallbackPattern : fallbackPatterns) {
                pattern = java.util.regex.Pattern.compile(fallbackPattern);
                matcher = pattern.matcher(htmlContent);
                if (matcher.find()) {
                    String nominalRateStr = matcher.group(1).replace(",", ".");
                    double nominalRate = Double.parseDouble(nominalRateStr);
                    entry.setNominalInterestRate(nominalRate);
                    System.out.println("DEBUG - Nominal interest rate found with fallback: " + nominalRate + "% (Pattern: " + fallbackPattern + ")");
                    break;
                }
            }
        }
    }
}
