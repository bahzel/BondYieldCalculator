# Bond Yield Calculator for Government Bonds

A Java application that analyzes CSV trading data to identify government bonds from multiple countries and calculates their yield-to-maturity based on real-time bond information scraped from comdirect.de.

*This project was implemented using Vibe Coding methodology.*

## Features

- **Automatic CSV Download**: Automatically downloads the latest pretrade data CSV file from gettex.de
- **CSV Data Processing**: Reads and processes compressed trading data (`.csv.gz` format)
- **Multi-Country Bond Support**: Automatically identifies government bonds from 32 countries/regions (ISINs starting with: BE, FR, NL, AT, PT, US, XS, CA, AU, CH, ES, DK, EU, GB, HK, IE, IT, LU, MT, MX, NZ, NO, PL, RO, SE, SG, SK, SI, CZ, HU, GR)
- **Maturity Filter**: User can specify maximum days to maturity to filter bonds
- **Maturity Cache**: Caches bond maturity dates locally to avoid unnecessary web requests and improve performance
- **Web Scraping**: Extracts bond details from comdirect.de including:
  - Maturity date
  - Nominal interest rate
  - Current market prices
- **Yield Calculation**: Computes yield-to-maturity considering:
  - Time to maturity (calculated in days)
  - Annual coupon payments
  - Transaction costs (fixed €2.50)
- **Interactive Input**: User-friendly investment amount and maturity filter input with validation
- **Robust HTTP Handling**: Includes retry mechanism for timeout handling
- **Sorted Results**: Displays bonds sorted by yield (highest first)

## Requirements

- Java 17 or higher
- Maven 3.6+
- Internet connection (for downloading CSV data and web scraping)

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

1. Run the application:
```bash
mvn exec:java -Dexec.mainClass="yield.BondYieldAnalyzer"
```

2. The application will:
   - Prompt for investment amount
   - Prompt for maximum days to maturity filter (or 0 for no limit)
   - Automatically download the latest CSV data from gettex.de
   - Load and process the CSV data
   - Filter for government bonds from supported countries
   - Use cached maturity dates when available to skip bonds that don't match the maturity filter
   - Scrape bond details from comdirect.de for remaining bonds
   - Calculate yields based on your investment amount
   - Display results sorted by yield

3. Supported countries/regions:
   - Belgium (BE), France (FR), Netherlands (NL), Austria (AT), Portugal (PT)
   - United States (US), International (XS), Canada (CA), Australia (AU), Switzerland (CH)
   - Spain (ES), Denmark (DK), European Union (EU), United Kingdom (GB), Hong Kong (HK)
   - Ireland (IE), Italy (IT), Luxembourg (LU), Malta (MT), Mexico (MX)
   - New Zealand (NZ), Norway (NO), Poland (PL), Romania (RO), Sweden (SE)
   - Singapore (SG), Slovakia (SK), Slovenia (SI), Czech Republic (CZ), Hungary (HU)
   - Greece (GR)

## Sample Output

```
=== Bond Yield Calculator ===

Please enter your desired investment amount in EUR: 1000

Investment amount set to: €1000.00
Transaction costs: fixed €2.50 per transaction
Total transaction costs: €2.50

Please enter maximum days to maturity (or 0 for no limit): 365
Maximum days to maturity set to: 365 days (1.0 years)

Fetching latest CSV file from gettex.de...
Found CSV file URL: https://erdk.bayerische-boerse.de/...
Downloaded CSV file to: C:\Users\...\pretrade.20251020.17.30.mund.csv.gz

Reading CSV file: C:\Users\...\pretrade.20251020.17.30.mund.csv.gz
Loaded 1234 maturity dates from cache.
Starting CSV processing...
...

=== Government Bond Yield Analysis ===
Investment Amount: €1000.00
Maximum Days to Maturity: 365 days (1.0 years)
Analyzed bonds: 15

ISIN            Bond Name                           Currency Bid Price  Ask Price  Maturity     Days     Yield    
------------------------------------------------------------------------------------------------------------------------
DE0001102481    German Government Bond 2025         EUR      99.85      99.95      15.08.2025   298      3.245%
FR0014001NN4    French Government Bond 2025         EUR      99.12      99.28      25.10.2025   369      2.987%
...

Note: Yields are calculated based on your investment amount of €1000.00
Fixed transaction costs of €2.50 are included in the calculation.
```

## CSV Data Source

The application automatically downloads the latest pretrade data from:
- Website: https://www.gettex.de/handel/delayed-data/pretrade-data/
- The most recent CSV file is automatically selected and downloaded

## Maturity Cache

Bond maturity dates are cached locally in:
- Location: `%USERPROFILE%\.bondcache\maturity_cache.properties` (Windows)
- Purpose: Avoid unnecessary web requests for bonds that don't match the maturity filter
- Performance: Significantly speeds up subsequent runs

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

### Project Structure

```
src/
└── main/
    └── java/
        └── yield/
            ├── BondYieldAnalyzer.java      # Main application class
            ├── BondEntry.java              # Bond data model
            ├── BondDataExtractor.java      # Web scraping service
            ├── BondValidationService.java  # Bond validation and filtering
            ├── CsvReaderService.java       # CSV processing
            ├── DateCalculator.java         # Date calculations
            └── YieldCalculator.java        # Yield calculations
```

### Bond Yield Calculation

The yield-to-maturity is calculated using a simplified formula:

```
Yield = ((Total Return / Total Cost)^(1/Years) - 1) * 100
```

Where:
- **Total Return** = Coupon payments over lifetime + Principal repayment (100)
- **Total Cost** = Ask price + Transaction costs (€2.50)
- **Years** = Days to maturity / 365.25

### Web Scraping

The application uses a dual data source strategy with intelligent fallback:

**Primary Source: comdirect.de**
1. Establishes HTTP session with proper headers
2. Accesses individual bond pages using ISIN
3. Parses HTML to extract maturity dates, nominal rates, and bond names
4. Implements retry logic for timeout handling (up to 3 attempts with exponential backoff)
5. Returns `ExtractionResult` enum (COMPLETE, INCOMPLETE, NOT_FOUND, ERROR)

**Fallback Source: bondblox.com**
- Automatically triggered when comdirect data is incomplete
- Extracts missing bond information
- Provides detailed logging of fallback results
- Complements primary source for comprehensive data coverage

**Smart Error Handling**:
- HTTP 400/404 errors from comdirect are cached to avoid repeated failed requests
- Incomplete data triggers automatic fallback attempt
- Only comdirect HTTP errors are cached (not fallback failures)
- User can optionally recheck cached errors on program start

### Error Handling

- **HTTP Timeouts**: Automatically retried up to 3 attempts with exponential backoff
- **Invalid Data**: Gracefully handled with fallback values and alternative data sources
- **Missing Data**: Automatic fallback from comdirect.de to bondblox.com
- **Cache Management**: Separate tracking of HTTP errors vs. incomplete data
- **Extraction Status**: `ExtractionResult` enum provides clear status tracking (COMPLETE, INCOMPLETE, NOT_FOUND, ERROR)
- **User Control**: Optional recheck of previously failed ISINs

### Dependencies

- Java 17 or higher (built-in HTTP Client)
- Apache Commons Compress (for `.gz` file handling)
- Maven 3.6+ (build tool)
- No external web scraping libraries (uses native Java HTTP Client)

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

- **Data Sources**: Primary source is comdirect.de with automatic fallback to bondblox.com
- **Caching**: Comprehensive caching system for bond data and HTTP errors
- **Performance**: Parallel CSV processing and intelligent caching for optimal speed
- **Currency Support**: Supports all currencies with optional EUR-only filter
- **Country Coverage**: 32 countries/regions supported (not just Greece)
- **Transaction Costs**: Fixed €2.50 per transaction included in all calculations
- **Rate Limiting**: Respectful delays between requests to avoid overwhelming websites
- **Corporate Bonds**: Automatically filtered out (bonds containing: Corp., Inc., Co.)
