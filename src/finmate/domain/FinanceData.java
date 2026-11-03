package finmate.domain;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable snapshot containing all persisted domain data.
 * List inputs are defensively copied. Calling the canonical constructor does not
 * perform domain validation; the service calls {@link #validate()} before saving.
 * @param categories all categories
 * @param transactions all recorded transactions
 * @param budgets monthly overall and category budgets
 * @param goals manually maintained savings goals
 */
public record FinanceData(List<Category> categories, List<Transaction> transactions,
                          List<Budget> budgets, List<SavingsGoal> goals) {
    /**
     * Copies each list into an unmodifiable list.
     * @param categories category values
     * @param transactions transaction values
     * @param budgets budget values
     * @param goals savings-goal values
     */
    public FinanceData {
        categories = List.copyOf(categories);
        transactions = List.copyOf(transactions);
        budgets = List.copyOf(budgets);
        goals = List.copyOf(goals);
    }

    /**
     * Creates initial data with nine default categories and no transactions, budgets, or goals.
     * @return a new initial snapshot with newly generated category UUIDs
     */
    public static FinanceData empty() {
        var categories = List.of("식비", "교통", "쇼핑", "주거", "교육", "여가", "의료", "기타", "급여")
                .stream().map(name -> new Category(UUID.randomUUID(), name)).toList();
        return new FinanceData(categories, List.of(), List.of(), List.of());
    }

    /**
     * Checks identifiers, references, nonblank and unique category names, required transaction
     * fields, positive transaction and budget amounts, unique monthly budget scopes,
     * positive savings targets, and nonnegative saved amounts. Income and expense totals
     * are checked for long overflow. Category-name comparisons ignore surrounding whitespace and case.
     * @throws IllegalArgumentException if a domain constraint is violated
     */
    public void validate() {
        var categoryIds = uniqueIds(categories, "카테고리");
        var names = new HashSet<String>();
        for (Category c : categories) {
            require(c.name() != null && !c.name().isBlank(), "카테고리 이름을 입력해 주세요.");
            require(names.add(c.name().strip().toLowerCase(Locale.ROOT)), "이미 있는 카테고리 이름입니다.");
        }
        uniqueIds(transactions, "거래");
        long income = 0, expenses = 0;
        for (Transaction t : transactions) {
            require(t.date() != null && t.type() != null && categoryIds.contains(t.categoryId()), "날짜·유형·카테고리를 확인해 주세요.");
            require(t.amount() > 0, "거래 금액은 0보다 커야 합니다.");
            require(t.payment() != null && t.note() != null, "거래의 선택 항목 형식이 잘못되었습니다.");
            try {
                if (t.type() == TransactionType.INCOME) income = Math.addExact(income, t.amount());
                else expenses = Math.addExact(expenses, t.amount());
            } catch (ArithmeticException e) { throw invalid("거래 합계가 지원 가능한 금액 범위를 초과합니다."); }
        }
        var budgetKeys = new HashSet<String>();
        for (Budget b : budgets) {
            require(b.month() != null && (b.categoryId() == null || categoryIds.contains(b.categoryId())), "예산의 월·카테고리를 확인해 주세요.");
            require(b.amount() > 0, "예산은 0보다 커야 합니다.");
            require(budgetKeys.add(b.month() + ":" + b.categoryId()), "같은 월·범위의 예산이 중복되었습니다.");
        }
        uniqueIds(goals, "목표");
        for (SavingsGoal g : goals) {
            require(g.name() != null && !g.name().isBlank(), "목표 이름을 입력해 주세요.");
            require(g.target() > 0, "목표 금액은 0보다 커야 합니다.");
            require(g.saved() >= 0, "현재 저축액은 0 이상이어야 합니다.");
        }
    }

    private static HashSet<UUID> uniqueIds(List<? extends Identifiable> items, String label) {
        var ids = new HashSet<UUID>();
        for (Identifiable item : items) require(item.id() != null && ids.add(item.id()), label + " ID가 잘못되었습니다.");
        return ids;
    }

    private static void require(boolean valid, String message) { if (!valid) throw invalid(message); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
