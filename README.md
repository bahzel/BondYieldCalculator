# Bond Yield Calculator for Greek Government Bonds

A Java application that analyzes CSV trading data to identify Greek government bonds and calculates their yield-to-maturity based on real-time bond information scraped from comdirect.de.

## Features

- **CSV Data Processing**: Reads and processes compressed trading data (`.csv.gz` format)
- **Bond Identification**: Automatically identifies Greek government bonds (ISINs starting with "GR")
- **Web Scraping**: Extracts bond details from comdirect.de including:
  - Maturity date
  - Nominal interest rate
  - Current market prices
- **Yield Calculation**: Computes yield-to-maturity considering:
  - Time to maturity (calculated in days)
  - Annual coupon payments
  - Transaction costs
- **Robust HTTP Handling**: Includes retry mechanism for timeout handling
- **Sorted Results**: Displays bonds sorted by yield (highest first)

## Requirements

- Java 11 or higher
- Maven 3.6+
- Internet connection (for web scraping)

## Installation

1. Clone the repository:
```bash
git clone https://github.com/bahzel/BondYieldCalculator.git
cd BondYieldCalculator
```

2. Compile the project:
```bash
mvn compile
```

## Usage

1. Place your compressed CSV trading data file in the expected location:
   - Default path: `C:\tmp\anleihen\pretrade.YYYYMMDD.HH.MM.mund.csv.gz`

2. Run the application:
```bash
mvn exec:java -Dexec.mainClass="de.example.rendite.CsvReader"
```

3. The application will:
   - Load the CSV data
   - Filter for Greek bonds (GR ISINs)
   - Scrape bond details from comdirect.de
   - Calculate yields
   - Display results sorted by yield

## Sample Output

```
=== Greek Government Bonds (GR) ===
Found bonds: 3

ISIN            Currency BidPrice  AskPrice  Maturity     Days     Yield    
---------------------------------------------------------------------------------
GR0338001231    EUR      98.50     98.75     12.02.2026   117      4.523%
GR0114020714    EUR      101.20    101.45    15.07.2029   1365     3.891%
GR0124030710    EUR      95.80     96.05     20.03.2034   3071     3.654%
```

## CSV Data Format

The application expects CSV files with the following columns:
- ISIN
- Timestamp
- Currency
- Bid Price
- Bid Size
- Ask Price
- Ask Size

## Configuration

To modify the CSV file path, edit the `csvFilePath` variable in the `main` method:

```java
String csvFilePath = "path/to/your/data.csv.gz";
```

## Technical Details

### Bond Yield Calculation

The yield-to-maturity is calculated using a simplified formula:

```
Yield = ((Total Return / Total Cost)^(1/Years) - 1) * 100
```

Where:
- **Total Return** = Coupon payments over lifetime + Principal repayment (100)
- **Total Cost** = Ask price + Transaction costs (2.50 EUR)
- **Years** = Days to maturity / 365.25

### Web Scraping

The application scrapes bond information from comdirect.de by:
1. Establishing a session with the main page
2. Accessing individual bond pages using ISIN
3. Parsing HTML to extract maturity dates and nominal rates
4. Implementing retry logic for timeout handling

### Error Handling

- HTTP timeouts are automatically retried (up to 3 attempts)
- Invalid data is gracefully handled with fallback values
- Debug output helps troubleshoot parsing issues

## Development

This project was developed using **Vibe Coding** techniques for rapid prototyping and iterative development.

### Project Structure

```
src/
└── main/
    └── java/
        └── de/example/rendite/
            └── CsvReader.java    # Main application class
```

### Dependencies

- Java HTTP Client (built-in)
- Standard Java libraries for file I/O and data processing

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is open source and available under the MIT License.

## Disclaimer

This application is for educational and research purposes only. The yield calculations are simplified and should not be used for actual investment decisions. Always consult with financial professionals before making investment choices.

## Notes

- The application includes a 3-second delay between requests to avoid overwhelming the target website
- Bond data is scraped in real-time, so results may vary based on market conditions
- Only Greek government bonds (ISINs starting with "GR") are processed

---

*Built with ❤️ using Vibe Coding*
