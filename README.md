# FinMate Beta Release

Standalone personal household account book using Java 17 or newer and Java Swing. No external libraries, database, or server are required.

## Build and run

```sh
javac --release 17 -encoding UTF-8 -d out -sourcepath src src/finmate/Application.java
jar --create --file ssd_ip_cp4_2026f_224561_app.jar --main-class finmate.Application -C out .
java -jar ssd_ip_cp4_2026f_224561_app.jar
```

Alternatively, run `finmate.Application.main` in an IDE configured with JDK 17 or newer.

Data is saved automatically to `.finmate/data.properties`, relative to the current working directory. Use a single application instance per data file. Set an explicit location with `java -Dfinmate.data=/path/data.properties -jar ssd_ip_cp4_2026f_224561_app.jar`.

## Features and rules

- Transactions: create, edit, delete, and search by inclusive date range, type, and category.
- Categories: add, rename, and delete unused categories. Referenced categories cannot be deleted.
- Budgets: independent overall and category limits for each month. Usage at 90 percent is a warning; strictly above 100 percent is overspending.
- Dashboard: selected-month income, expenses, category spending, and balance across all months.
- Savings: manually entered targets and saved amounts, independent of transaction balances. Progress may exceed 100 percent.
- Storage: validated immutable snapshots, UTF-8 Properties persistence, and atomic replacement. Invalid input or a failed save leaves the previous state unchanged. Corrupt files are not overwritten at startup.

Amounts are whole Korean won (KRW). The beta user interface uses Korean labels. Online synchronization, account login, bank imports, and multi-user editing are outside this release.

## Tests

```sh
javac --release 17 -encoding UTF-8 -cp out -d out-test test/finmate/FinanceServiceTest.java
java -cp out:out-test finmate.FinanceServiceTest
```

On Windows, replace the classpath separator `:` with `;`. The test suite uses temporary data and includes persistence and failure handling checks.

## API manual

```sh
javadoc -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -Xdoclint:all,-missing -d api_manual -sourcepath src -subpackages finmate
```

Open `api_manual/index.html` in a browser. The API documentation describes public inputs, outputs, state changes, and error conditions.
