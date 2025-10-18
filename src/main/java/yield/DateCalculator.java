package yield;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for date-related calculations
 */
public class DateCalculator {

    /**
     * Calculates remaining days based on maturity date
     */
    public static int calculateRemainingDays(String maturityDate) {
        try {
            // Format: "24.01.2052"
            String[] parts = maturityDate.split("\\.");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);

                LocalDate maturity = LocalDate.of(year, month, day);
                LocalDate today = LocalDate.now();

                return (int) ChronoUnit.DAYS.between(today, maturity);
            }
        } catch (Exception e) {
            System.err.println("Error parsing maturity date: " + maturityDate);
        }
        return -1;
    }
}
