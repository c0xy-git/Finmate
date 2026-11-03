package finmate.infrastructure;

import finmate.domain.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

/**
 * UTF-8 Java Properties implementation of {@link FinanceRepository}.
 * Data is written to a temporary file, flushed, and atomically moved over the destination.
 * A file system that does not support atomic replacement causes the save to fail.
 */
public final class FileFinanceRepository implements FinanceRepository {
    private final Path file;

    /**
     * Creates a repository for an absolute form of the supplied path.
     * @param file destination data-file path
     */
    public FileFinanceRepository(Path file) { this.file = file.toAbsolutePath(); }

    /**
     * Loads version 1 data and validates its domain constraints.
     * If the file does not exist, returns {@link FinanceData#empty()}.
     * Malformed files are rejected without changing their contents.
     * @return restored or initial snapshot
     * @throws IOException if reading, decoding, version checking, or domain validation fails
     */
    @Override public FinanceData load() throws IOException {
        if (Files.notExists(file)) return FinanceData.empty();
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
            if (!required(p, "version").equals("1")) {
                throw new IllegalArgumentException("지원하지 않는 저장 파일 버전입니다.");
            }
            var categories = new ArrayList<Category>();
            var transactions = new ArrayList<Transaction>();
            var budgets = new ArrayList<Budget>();
            var goals = new ArrayList<SavingsGoal>();
            for (int i = 0; i < count(p, "category"); i++) {
                String k = "category." + i + ".";
                categories.add(new Category(id(p, k + "id"), required(p, k + "name")));
            }
            for (int i = 0; i < count(p, "transaction"); i++) {
                String k = "transaction." + i + ".";
                transactions.add(new Transaction(id(p, k + "id"),
                        LocalDate.parse(required(p, k + "date")), TransactionType.valueOf(required(p, k + "type")),
                        id(p, k + "category"), number(p, k + "amount"),
                        required(p, k + "payment"), required(p, k + "note")));
            }
            for (int i = 0; i < count(p, "budget"); i++) {
                String k = "budget." + i + ".";
                String category = required(p, k + "category");
                budgets.add(new Budget(YearMonth.parse(required(p, k + "month")),
                        category.isEmpty() ? null : UUID.fromString(category), number(p, k + "amount")));
            }
            for (int i = 0; i < count(p, "goal"); i++) {
                String k = "goal." + i + ".";
                goals.add(new SavingsGoal(id(p, k + "id"), required(p, k + "name"),
                        number(p, k + "target"), number(p, k + "saved")));
            }
            FinanceData data = new FinanceData(categories, transactions, budgets, goals);
            data.validate();
            return data;
        } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException e) {
            throw new IOException("저장 파일을 읽을 수 없습니다. 원본 파일은 변경하지 않았습니다.\n" + e.getMessage(), e);
        }
    }

    /**
     * Serializes the supplied snapshot and replaces the destination atomically.
     * The caller is responsible for domain validation before calling this method.
     * @param data snapshot to serialize
     * @throws IOException if directory creation, writing, flushing, or atomic replacement fails
     */
    @Override public void save(FinanceData data) throws IOException {
        Properties p = new Properties();
        put(p, "version", "1");
        put(p, "category.count", data.categories().size());
        put(p, "transaction.count", data.transactions().size());
        put(p, "budget.count", data.budgets().size());
        put(p, "goal.count", data.goals().size());
        for (int i = 0; i < data.categories().size(); i++) {
            Category c = data.categories().get(i);
            String k = "category." + i + ".";
            put(p, k + "id", c.id()); put(p, k + "name", c.name());
        }
        for (int i = 0; i < data.transactions().size(); i++) {
            Transaction t = data.transactions().get(i);
            String k = "transaction." + i + ".";
            put(p, k + "id", t.id()); put(p, k + "date", t.date());
            put(p, k + "type", t.type().name()); put(p, k + "category", t.categoryId());
            put(p, k + "amount", t.amount()); put(p, k + "payment", t.payment()); put(p, k + "note", t.note());
        }
        for (int i = 0; i < data.budgets().size(); i++) {
            Budget b = data.budgets().get(i);
            String k = "budget." + i + ".";
            put(p, k + "month", b.month()); put(p, k + "category", b.categoryId() == null ? "" : b.categoryId());
            put(p, k + "amount", b.amount());
        }
        for (int i = 0; i < data.goals().size(); i++) {
            SavingsGoal g = data.goals().get(i);
            String k = "goal." + i + ".";
            put(p, k + "id", g.id()); put(p, k + "name", g.name());
            put(p, k + "target", g.target()); put(p, k + "saved", g.saved());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "finmate-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                p.store(writer, "FinMate v1 - amounts in KRW");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // 원자적 교체를 지원하지 않는 파일 시스템에서는 저장을 실패시켜 기존 파일을 보존한다.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* 저장 결과에 영향 없음 */ }
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new IllegalArgumentException("저장 항목 누락: " + key);
        return value;
    }

    private static int count(Properties p, String key) {
        int value = Integer.parseInt(required(p, key + ".count"));
        if (value < 0) throw new IllegalArgumentException("잘못된 항목 수: " + key);
        return value;
    }

    private static UUID id(Properties p, String key) { return UUID.fromString(required(p, key)); }
    private static long number(Properties p, String key) { return Long.parseLong(required(p, key)); }
    private static void put(Properties p, String key, Object value) { p.setProperty(key, value.toString()); }
}
