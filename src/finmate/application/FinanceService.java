package finmate.application;

import finmate.domain.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application operations for requirements R01 through R11, independent of Swing.
 * Mutations construct and validate a new snapshot, persist it, then replace the
 * in-memory state only after saving succeeds. Amounts use whole KRW.
 */
public final class FinanceService {
    /**
     * Budget status derived from the spent amount and the configured limit.
     */
    public enum BudgetStatus {
        /** Usage below 90 percent. */
        NORMAL("정상"),
        /** Usage from 90 percent through exactly 100 percent. */
        WARNING("주의 · 90% 이상"),
        /** Expenses strictly exceed the budget. */
        OVER("예산 초과");
        private final String label;
        BudgetStatus(String label) { this.label = label; }
        /**
         * Returns the current Korean UI status label.
         * @return display label
         */
        @Override public String toString() { return label; }
    }
    /**
     * Calculated usage of one monthly budget.
     * @param budget configured budget
     * @param spent expenses matching its month and scope, in whole KRW
     */
    public record BudgetUsage(Budget budget, long spent) {
        /**
         * Subtracts spending from the budget amount.
         * @return remaining amount in whole KRW, negative when overspent
         */
        public long remaining() { return budget.amount() - spent; }
        /**
         * Calculates the spending percentage; assumes a validated positive budget.
         * @return spending divided by budget, multiplied by 100
         */
        public double percentage() { return spent * 100.0 / budget.amount(); }
        /**
         * Classifies usage: NORMAL below 90 percent, WARNING from 90 through 100
         * percent, and OVER strictly above 100 percent. The 90-percent comparison uses BigDecimal.
         * @return calculated budget status
         */
        public BudgetStatus status() {
            if (spent > budget.amount()) return BudgetStatus.OVER;
            BigDecimal threshold = BigDecimal.valueOf(budget.amount()).multiply(new BigDecimal("0.9"));
            return BigDecimal.valueOf(spent).compareTo(threshold) >= 0 ? BudgetStatus.WARNING : BudgetStatus.NORMAL;
        }
    }
    /**
     * Dashboard totals for a selected month.
     * @param income selected-month income in whole KRW
     * @param expenses selected-month expenses in whole KRW
     * @param balance income minus expenses across all recorded months
     * @param byCategory selected-month expenses grouped by category UUID
     */
    public record Summary(long income, long expenses, long balance, Map<UUID, Long> byCategory) { }

    private final FinanceRepository repository;
    private FinanceData data;

    /**
     * Loads and validates initial state from an injected repository.
     * @param repository persistence implementation
     * @throws IOException if the repository cannot load the data
     * @throws IllegalArgumentException if the returned snapshot violates domain constraints
     * @throws NullPointerException if repository is null
     */
    public FinanceService(FinanceRepository repository) throws IOException {
        this.repository = Objects.requireNonNull(repository);
        data = repository.load();
        data.validate();
    }

    /**
     * Returns the current immutable snapshot.
     * @return current application data
     */
    public FinanceData data() { return data; }
    /**
     * Looks up a category label.
     * @param id category UUID, or null for the overall-budget label
     * @return current name or the overall-budget display label
     * @throws java.util.NoSuchElementException if a non-null ID is unknown
     */
    public String categoryName(UUID id) {
        if (id == null) return "전체 월 예산";
        return data.categories().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().name();
    }

    /**
     * Creates or updates a transaction and persists the resulting snapshot.
     * A null ID creates a new UUID; a non-null ID must identify an existing transaction.
     * Optional strings are trimmed and null values become empty strings.
     * @param id existing transaction UUID, or null to create
     * @param date required transaction date
     * @param type required income or expense type
     * @param category UUID of an existing category
     * @param amount positive amount in whole KRW
     * @param payment optional payment description
     * @param note optional transaction note
     * @throws IllegalArgumentException if the ID is unknown or the candidate snapshot is invalid
     * @throws IOException if persistence fails; the previous in-memory state is retained
     */
    public void saveTransaction(UUID id, LocalDate date, TransactionType type, UUID category, long amount,
                                String payment, String note) throws IOException {
        var list = new ArrayList<>(data.transactions());
        if (id != null && !list.removeIf(t -> t.id().equals(id))) throw invalid("거래를 찾을 수 없습니다.");
        list.add(new Transaction(id == null ? UUID.randomUUID() : id, date, type, category, amount,
                cleanOptional(payment), cleanOptional(note)));
        commit(new FinanceData(data.categories(), list, data.budgets(), data.goals()));
    }

    /**
     * Deletes an existing transaction and saves the resulting snapshot.
     * @param id UUID of the transaction to remove
     * @throws IllegalArgumentException if no transaction matches
     * @throws IOException if persistence fails; previous state is retained
     */
    public void deleteTransaction(UUID id) throws IOException {
        var list = new ArrayList<>(data.transactions());
        if (!list.removeIf(t -> t.id().equals(id))) throw invalid("거래를 선택해 주세요.");
        commit(new FinanceData(data.categories(), list, data.budgets(), data.goals()));
    }

    /**
     * Filters transactions with inclusive date bounds and optional type and category criteria.
     * Results are ordered by date descending, independently of the dashboard month.
     * @param from inclusive start date, or null for no lower bound
     * @param to inclusive end date, or null for no upper bound
     * @param type required type, or null for either type
     * @param category required category UUID, or null for any category
     * @return unmodifiable matching transactions, possibly empty
     * @throws IllegalArgumentException if from is after to
     */
    public List<Transaction> search(LocalDate from, LocalDate to, TransactionType type, UUID category) {
        if (from != null && to != null && from.isAfter(to)) throw invalid("시작일은 종료일보다 늦을 수 없습니다.");
        return data.transactions().stream()
                .filter(t -> from == null || !t.date().isBefore(from))
                .filter(t -> to == null || !t.date().isAfter(to))
                .filter(t -> type == null || t.type() == type)
                .filter(t -> category == null || t.categoryId().equals(category))
                .sorted(Comparator.comparing(Transaction::date).reversed()).toList();
    }

    /**
     * Creates or renames a category while preserving an existing UUID.
     * Names are trimmed and must be nonblank and unique ignoring case.
     * @param id existing category UUID, or null to create
     * @param name category name
     * @throws IllegalArgumentException if the ID is unknown or the candidate data is invalid
     * @throws IOException if persistence fails; previous state is retained
     */
    public void saveCategory(UUID id, String name) throws IOException {
        var list = new ArrayList<>(data.categories());
        if (id != null && !list.removeIf(c -> c.id().equals(id))) throw invalid("카테고리를 찾을 수 없습니다.");
        list.add(new Category(id == null ? UUID.randomUUID() : id, cleanOptional(name)));
        commit(new FinanceData(list, data.transactions(), data.budgets(), data.goals()));
    }

    /**
     * Removes a category only when no transaction or budget in any month references it.
     * @param id UUID of an existing unused category
     * @throws IllegalArgumentException if the category is unknown or referenced
     * @throws IOException if persistence fails; previous state is retained
     */
    public void deleteCategory(UUID id) throws IOException {
        boolean used = data.transactions().stream().anyMatch(t -> t.categoryId().equals(id))
                || data.budgets().stream().anyMatch(b -> Objects.equals(b.categoryId(), id));
        if (used) throw invalid("거래 또는 예산에서 사용 중인 카테고리는 삭제할 수 없습니다.");
        var list = new ArrayList<>(data.categories());
        if (!list.removeIf(c -> c.id().equals(id))) throw invalid("카테고리를 선택해 주세요.");
        commit(new FinanceData(list, data.transactions(), data.budgets(), data.goals()));
    }

    /**
     * Creates or replaces the budget for one month and category scope.
     * Overall and category budgets are stored and evaluated independently.
     * @param month required budget month
     * @param category category UUID, or null for the overall monthly budget
     * @param amount positive spending limit in whole KRW
     * @throws IllegalArgumentException if the candidate data is invalid
     * @throws IOException if persistence fails; previous state is retained
     */
    public void setBudget(YearMonth month, UUID category, long amount) throws IOException {
        var list = new ArrayList<>(data.budgets());
        list.removeIf(b -> b.month().equals(month) && Objects.equals(b.categoryId(), category));
        list.add(new Budget(month, category, amount));
        commit(new FinanceData(data.categories(), data.transactions(), list, data.goals()));
    }

    /**
     * Calculates usage for budgets in the selected month using expense transactions.
     * The overall budget is listed first, followed by category budgets sorted by name.
     * @param month month to summarize
     * @return unmodifiable usage list, empty when no budget is configured
     * @throws NullPointerException if month is null
     */
    public List<BudgetUsage> budgetUsage(YearMonth month) {
        Map<UUID, Long> spending = summary(month).byCategory();
        long total = spending.values().stream().reduce(0L, Math::addExact);
        return data.budgets().stream().filter(b -> b.month().equals(month))
                .sorted(Comparator.comparing(b -> b.categoryId() == null ? "" : categoryName(b.categoryId())))
                .map(b -> new BudgetUsage(b, b.categoryId() == null ? total : spending.getOrDefault(b.categoryId(), 0L)))
                .toList();
    }

    /**
     * Calculates monthly income, monthly expenses, and expenses by category.
     * The balance covers all transaction months. Months with no transactions have zero
     * monthly totals and an empty category map. Stored data is not modified.
     * @param month month whose income and expenses are summarized
     * @return totals and an unmodifiable category-expense map
     * @throws NullPointerException if month is null
     */
    public Summary summary(YearMonth month) {
        Objects.requireNonNull(month, "조회할 월이 필요합니다.");
        long allIncome = 0, allExpenses = 0, income = 0, expenses = 0;
        Map<UUID, Long> byCategory = new LinkedHashMap<>();
        for (Transaction t : data.transactions()) {
            if (t.type() == TransactionType.INCOME) allIncome = Math.addExact(allIncome, t.amount());
            else allExpenses = Math.addExact(allExpenses, t.amount());
            if (YearMonth.from(t.date()).equals(month)) {
                if (t.type() == TransactionType.INCOME) income = Math.addExact(income, t.amount());
                else {
                    expenses = Math.addExact(expenses, t.amount());
                    byCategory.merge(t.categoryId(), t.amount(), Math::addExact);
                }
            }
        }
        return new Summary(income, expenses, allIncome - allExpenses, Map.copyOf(byCategory));
    }

    /**
     * Creates or updates a savings goal. Savings are entered manually and do not change
     * transaction balances. Names are trimmed; target must be positive and saved nonnegative.
     * @param id existing goal UUID, or null to create
     * @param name nonblank goal name
     * @param target positive target in whole KRW
     * @param saved recorded savings in whole KRW, including zero
     * @throws IllegalArgumentException if the ID is unknown or the candidate data is invalid
     * @throws IOException if persistence fails; previous state is retained
     */
    public void saveGoal(UUID id, String name, long target, long saved) throws IOException {
        var list = new ArrayList<>(data.goals());
        if (id != null && !list.removeIf(g -> g.id().equals(id))) throw invalid("저축 목표를 찾을 수 없습니다.");
        list.add(new SavingsGoal(id == null ? UUID.randomUUID() : id, cleanOptional(name), target, saved));
        commit(new FinanceData(data.categories(), data.transactions(), data.budgets(), list));
    }

    /**
     * Removes an existing savings goal and persists the new snapshot.
     * @param id UUID of the goal to remove
     * @throws IllegalArgumentException if no goal matches
     * @throws IOException if persistence fails; previous state is retained
     */
    public void deleteGoal(UUID id) throws IOException {
        var list = new ArrayList<>(data.goals());
        if (!list.removeIf(g -> g.id().equals(id))) throw invalid("저축 목표를 선택해 주세요.");
        commit(new FinanceData(data.categories(), data.transactions(), data.budgets(), list));
    }

    private void commit(FinanceData next) throws IOException {
        next.validate();
        repository.save(next); // 파일 저장이 성공한 뒤에만 메모리 상태를 바꾼다.
        data = next;
    }

    private static String cleanOptional(String text) { return text == null ? "" : text.strip(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
