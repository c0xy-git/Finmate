package finmate.ui;

import finmate.domain.*;
import finmate.application.FinanceService;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import static finmate.application.FinanceService.*;

/** 외부 의존성이 없는 Java Swing의 다섯 가지 화면. 실행은 Application이 담당한다. */
@SuppressWarnings("serial")
/**
 * Swing desktop interface for the FinMate beta release.
 * Five tabs expose dashboard, transaction, category, budget, and savings-goal operations.
 * All construction and interaction must occur on the Swing event dispatch thread.
 */
public final class FinMateWindow extends JFrame {
    private static final Color INK = new Color(30, 45, 62);
    private static final Color MUTED = new Color(100, 116, 132);
    private static final Color ACCENT = new Color(26, 125, 110);
    private static final Color WARNING = new Color(162, 93, 10);
    private static final Color DANGER = new Color(184, 53, 53);
    private static final Color BACKGROUND = new Color(245, 247, 249);
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.KOREA);

    private final FinanceService service;
    private YearMonth month = YearMonth.now();
    private final JTextField monthInput = new JTextField(month.toString(), 7);
    private final JLabel status = new JLabel("준비되었습니다. 변경 사항은 자동으로 저장됩니다.");
    private final JLabel incomeValue = valueLabel(), expenseValue = valueLabel();
    private final JLabel balanceValue = valueLabel(), remainingValue = valueLabel();
    private final ExpenseChart chart = new ExpenseChart();

    private final JTable transactions = table(new String[]{"날짜", "유형", "카테고리", "금액 (원)", "결제 수단", "메모"},
            LocalDate.class, String.class, String.class, Long.class, String.class, String.class);
    private final JTable categories = table(new String[]{"카테고리", "사용 여부"}, String.class, String.class);
    private final JTable budgets = table(new String[]{"범위", "예산 (원)", "지출 (원)", "잔여 (원)", "사용률", "상태"},
            String.class, Long.class, Long.class, Long.class, Double.class, String.class);
    private final JTable goals = table(new String[]{"목표", "목표 금액 (원)", "현재 저축액 (원)", "달성률", "상태"},
            String.class, Long.class, Long.class, Double.class, String.class);
    private final JTable budgetOverview = table(new String[]{"예산", "사용률", "상태"}, String.class, Double.class, String.class);
    private final JTable goalOverview = table(new String[]{"목표", "달성률", "상태"}, String.class, Double.class, String.class);

    private final JTextField fromInput = new JTextField(9), toInput = new JTextField(9);
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"전체 유형", "수입", "지출"});
    private final JComboBox<Choice> categoryFilter = new JComboBox<>();
    private LocalDate filterFrom, filterTo;
    private TransactionType filterType;
    private UUID filterCategory;
    private List<Transaction> shownTransactions = List.of();
    private List<Category> shownCategories = List.of();
    private List<BudgetUsage> shownBudgets = List.of();
    private List<SavingsGoal> shownGoals = List.of();

    /**
     * Builds the five-tab interface and displays the current service data.
     * Call on the Swing event dispatch thread. The constructor does not make the window visible.
     * @param service application service for business operations
     * @param storageLocation storage location shown in the window footer
     */
    public FinMateWindow(FinanceService service, String storageLocation) {
        super("FinMate · 나의 가계부");
        this.service = service;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 660));
        setSize(1160, 800);
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(18, 22, 14, 22));
        root.setBackground(BACKGROUND);
        root.add(header(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("대시보드", dashboard());
        tabs.addTab("거래 관리", transactionPage());
        tabs.addTab("카테고리", categoryPage());
        tabs.addTab("예산 관리", budgetPage());
        tabs.addTab("저축 목표", goalPage());
        root.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 5));
        footer.setOpaque(false);
        status.setForeground(MUTED);
        footer.add(status);
        JLabel location = new JLabel("로컬 저장 · " + storageLocation);
        location.setFont(location.getFont().deriveFont(11f));
        location.setForeground(MUTED);
        footer.add(location);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);

        budgets.setDefaultRenderer(Double.class, new ProgressRenderer(true));
        budgetOverview.setDefaultRenderer(Double.class, new ProgressRenderer(true));
        goals.setDefaultRenderer(Double.class, new ProgressRenderer(false));
        goalOverview.setDefaultRenderer(Double.class, new ProgressRenderer(false));
        refresh();
        setLocationRelativeTo(null);
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel brand = new JPanel(new GridLayout(2, 1));
        brand.setOpaque(false);
        JLabel name = new JLabel("FinMate");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 28f));
        name.setForeground(ACCENT);
        brand.add(name);
        JLabel subtitle = new JLabel("기록하고, 살펴보고, 계획하는 나의 가계부");
        subtitle.setForeground(MUTED);
        brand.add(subtitle);
        header.add(brand, BorderLayout.WEST);
        JPanel monthPicker = toolbar(new JLabel("대시보드 · 예산 조회 월"),
                button("‹", () -> changeMonth(month.minusMonths(1))), monthInput,
                button("조회", () -> changeMonth(parseMonth(monthInput.getText()))),
                button("›", () -> changeMonth(month.plusMonths(1))));
        monthInput.addActionListener(e -> run(() -> changeMonth(parseMonth(monthInput.getText()))));
        header.add(monthPicker, BorderLayout.EAST);
        return header;
    }

    private JPanel dashboard() {
        JPanel page = page();
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setOpaque(false);
        cards.add(card("선택 월 수입", incomeValue, "조회한 월의 수입 합계"));
        cards.add(card("선택 월 지출", expenseValue, "조회한 월의 지출 합계"));
        cards.add(card("전체 누적 잔액", balanceValue, "모든 기록의 수입 − 지출"));
        cards.add(card("전체 월 예산 잔액", remainingValue, "선택 월의 전체 예산 기준"));
        page.add(cards, BorderLayout.NORTH);
        JPanel content = new JPanel(new GridLayout(1, 2, 14, 0));
        content.setOpaque(false);
        content.add(section("카테고리별 지출", new JScrollPane(chart)));
        JPanel summaries = new JPanel(new GridLayout(2, 1, 0, 14));
        summaries.setOpaque(false);
        summaries.add(section("이달의 예산", new JScrollPane(budgetOverview)));
        summaries.add(section("저축 목표", new JScrollPane(goalOverview)));
        content.add(summaries);
        page.add(content, BorderLayout.CENTER);
        page.add(hint("예산 사용률 90% 이상: 주의  ·  100% 초과: 예산 초과  |  저축액은 사용자가 별도로 입력합니다."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel transactionPage() {
        JPanel page = page();
        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 4));
        controls.setOpaque(false);
        controls.add(toolbar(new JLabel("기간"), fromInput, new JLabel("~"), toInput,
                typeFilter, categoryFilter, button("검색", this::applyFilter), button("초기화", this::resetFilter)));
        controls.add(toolbar(button("+ 거래 추가", () -> editTransaction(null)),
                button("선택 거래 수정", () -> editTransaction(selected(transactions, shownTransactions))),
                button("선택 거래 삭제", this::deleteTransaction)));
        page.add(controls, BorderLayout.NORTH);
        page.add(new JScrollPane(transactions), BorderLayout.CENTER);
        page.add(hint("날짜: YYYY-MM-DD · 빈 날짜는 제한 없음 · 검색 기간은 상단의 조회 월과 독립적입니다."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel categoryPage() {
        JPanel page = page();
        page.add(toolbar(button("+ 카테고리 추가", () -> editCategory(null)),
                button("이름 변경", () -> editCategory(selected(categories, shownCategories))),
                button("선택 카테고리 삭제", this::deleteCategory)), BorderLayout.NORTH);
        page.add(new JScrollPane(categories), BorderLayout.CENTER);
        page.add(hint("거래 또는 예산에서 사용 중인 카테고리는 삭제할 수 없습니다. 이름 변경 시 연결은 유지됩니다."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel budgetPage() {
        JPanel page = page();
        page.add(toolbar(button("+ 예산 설정", () -> editBudget(null)),
                button("선택 예산 수정", () -> editBudget(selected(budgets, shownBudgets).budget()))), BorderLayout.NORTH);
        page.add(new JScrollPane(budgets), BorderLayout.CENTER);
        page.add(hint("상단에서 조회 월을 변경할 수 있습니다. 전체 예산과 카테고리 예산은 각각 독립적으로 계산합니다."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel goalPage() {
        JPanel page = page();
        page.add(toolbar(button("+ 저축 목표 추가", () -> editGoal(null)),
                button("선택 목표 수정", () -> editGoal(selected(goals, shownGoals))),
                button("선택 목표 삭제", this::deleteGoal)), BorderLayout.NORTH);
        page.add(new JScrollPane(goals), BorderLayout.CENTER);
        page.add(hint("현재 저축액은 직접 입력합니다. 거래 잔액과 자동 연동하지 않으며, 목표액 이상이면 달성 상태가 됩니다."), BorderLayout.SOUTH);
        return page;
    }

    private void refresh() {
        UUID selectedFilter = selectedChoice(categoryFilter);
        fillCategories(categoryFilter, "전체 카테고리", selectedFilter);
        if (filterCategory != null && service.data().categories().stream().noneMatch(c -> c.id().equals(filterCategory))) {
            filterCategory = null;
        }
        Summary summary = service.summary(month);
        incomeValue.setText(won(summary.income()));
        expenseValue.setText(won(summary.expenses()));
        balanceValue.setText(won(summary.balance()));
        shownBudgets = service.budgetUsage(month);
        BudgetUsage overall = shownBudgets.stream().filter(b -> b.budget().categoryId() == null).findFirst().orElse(null);
        remainingValue.setText(overall == null ? "예산 미설정" : won(overall.remaining()));
        remainingValue.setForeground(overall != null && overall.remaining() < 0 ? DANGER : INK);
        List<Spending> spending = summary.byCategory().entrySet().stream()
                .map(e -> new Spending(service.categoryName(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(Spending::amount).reversed().thenComparing(Spending::name)).toList();
        chart.update(spending, summary.expenses());

        refreshTransactions();
        shownCategories = service.data().categories().stream().sorted(Comparator.comparing(Category::name)).toList();
        rows(categories, shownCategories.stream().map(c -> {
            long used = service.data().transactions().stream().filter(t -> t.categoryId().equals(c.id())).count();
            long budgetCount = service.data().budgets().stream().filter(b -> c.id().equals(b.categoryId())).count();
            return new Object[]{c.name(), used + "건의 거래 / " + budgetCount + "개의 예산"};
        }).toList());
        rows(budgets, shownBudgets.stream().map(b -> new Object[]{service.categoryName(b.budget().categoryId()),
                b.budget().amount(), b.spent(), b.remaining(), b.percentage(), b.status().toString()}).toList());
        rows(budgetOverview, shownBudgets.stream().map(b -> new Object[]{service.categoryName(b.budget().categoryId()),
                b.percentage(), b.status().toString()}).toList());
        if (shownBudgets.isEmpty()) rows(budgetOverview, List.<Object[]>of(new Object[]{"예산 미설정", null, "예산 관리에서 설정"}));
        shownGoals = service.data().goals();
        rows(goals, shownGoals.stream().map(g -> new Object[]{g.name(), g.target(), g.saved(), g.progress(), g.achieved() ? "달성" : "진행 중"}).toList());
        rows(goalOverview, shownGoals.stream().map(g -> new Object[]{g.name(), g.progress(), g.achieved() ? "달성" : "진행 중"}).toList());
        if (shownGoals.isEmpty()) rows(goalOverview, List.<Object[]>of(new Object[]{"등록된 목표 없음", null, "저축 목표에서 추가"}));
    }

    private void refreshTransactions() {
        shownTransactions = service.search(filterFrom, filterTo, filterType, filterCategory);
        rows(transactions, shownTransactions.stream().map(t -> new Object[]{t.date(), t.type().toString(),
                service.categoryName(t.categoryId()), t.amount(), t.payment(), t.note()}).toList());
    }

    private void applyFilter() {
        LocalDate from = optionalDate(fromInput.getText()), to = optionalDate(toInput.getText());
        TransactionType type = typeFilter.getSelectedIndex() == 0 ? null : TransactionType.values()[typeFilter.getSelectedIndex() - 1];
        UUID category = selectedChoice(categoryFilter);
        service.search(from, to, type, category); // 검증 성공 후에만 적용된 검색 조건 변경
        filterFrom = from; filterTo = to; filterType = type; filterCategory = category;
        refreshTransactions();
        message(shownTransactions.size() + "건의 거래를 찾았습니다.", MUTED);
    }

    private void resetFilter() {
        fromInput.setText(""); toInput.setText(""); typeFilter.setSelectedIndex(0); categoryFilter.setSelectedIndex(0);
        applyFilter();
    }

    private void changeMonth(YearMonth value) {
        month = value;
        monthInput.setText(month.toString());
        refresh();
        message(month + "의 대시보드와 예산을 표시합니다.", MUTED);
    }

    private void editTransaction(Transaction old) {
        if (service.data().categories().isEmpty()) throw new IllegalArgumentException("카테고리를 먼저 추가해 주세요.");
        JTextField date = new JTextField((old == null ? LocalDate.now() : old.date()).toString());
        JComboBox<TransactionType> type = new JComboBox<>(TransactionType.values());
        type.setSelectedItem(old == null ? TransactionType.EXPENSE : old.type());
        JComboBox<Choice> category = new JComboBox<>();
        fillCategories(category, null, old == null ? null : old.categoryId());
        JTextField amount = new JTextField(old == null ? "" : Long.toString(old.amount()));
        JTextField payment = new JTextField(old == null ? "" : old.payment());
        JTextField note = new JTextField(old == null ? "" : old.note());
        JPanel form = form("날짜 * (YYYY-MM-DD)", date, "유형 *", type, "카테고리 *", category,
                "금액 * (원)", amount, "결제 수단 (선택)", payment, "메모 (선택)", note);
        submit(old == null ? "거래 추가" : "거래 수정", form, () -> {
            LocalDate parsed = parseDate(date.getText());
            service.saveTransaction(old == null ? null : old.id(), parsed, (TransactionType) type.getSelectedItem(),
                    selectedChoice(category), parseMoney(amount.getText()), payment.getText(), note.getText());
            if (old == null) changed("거래를 저장했습니다.", YearMonth.from(parsed));
            else changed("거래를 수정했습니다.", YearMonth.from(old.date()), YearMonth.from(parsed));
        });
    }

    private void deleteTransaction() throws IOException {
        Transaction selected = selected(transactions, shownTransactions);
        if (confirm(selected.date() + " · " + service.categoryName(selected.categoryId()) + " · " + won(selected.amount()) + " 거래를 삭제할까요?")) {
            service.deleteTransaction(selected.id());
            changed("거래를 삭제했습니다.", YearMonth.from(selected.date()));
        }
    }

    private void editCategory(Category old) {
        JTextField name = new JTextField(old == null ? "" : old.name());
        submit(old == null ? "카테고리 추가" : "카테고리 이름 변경", form("이름 *", name), () -> {
            service.saveCategory(old == null ? null : old.id(), name.getText());
            changed("카테고리를 저장했습니다.");
        });
    }

    private void deleteCategory() throws IOException {
        Category category = selected(categories, shownCategories);
        if (confirm("'" + category.name() + "' 카테고리를 삭제할까요?")) {
            service.deleteCategory(category.id()); changed("카테고리를 삭제했습니다.");
        }
    }

    private void editBudget(Budget old) {
        JTextField selectedMonth = new JTextField((old == null ? month : old.month()).toString());
        JComboBox<Choice> scope = new JComboBox<>();
        fillCategories(scope, "전체 월 예산", old == null ? null : old.categoryId());
        JTextField amount = new JTextField(old == null ? "" : Long.toString(old.amount()));
        if (old != null) { selectedMonth.setEditable(false); scope.setEnabled(false); }
        JPanel form = form("월 * (YYYY-MM)", selectedMonth, "범위 *", scope, "예산 * (원)", amount,
                "설정 방식", new JLabel("같은 월·범위의 예산은 수정됩니다."));
        submit(old == null ? "예산 설정" : "예산 수정", form, () -> {
            YearMonth targetMonth = parseMonth(selectedMonth.getText());
            service.setBudget(targetMonth, selectedChoice(scope), parseMoney(amount.getText()));
            month = targetMonth; monthInput.setText(month.toString());
            changed("예산을 저장했습니다.", targetMonth);
        });
    }

    private void editGoal(SavingsGoal old) {
        JTextField name = new JTextField(old == null ? "" : old.name());
        JTextField target = new JTextField(old == null ? "" : Long.toString(old.target()));
        JTextField saved = new JTextField(old == null ? "0" : Long.toString(old.saved()));
        submit(old == null ? "저축 목표 추가" : "저축 목표 수정", form("목표 이름 *", name, "목표 금액 * (원)", target,
                "현재 저축액 * (원)", saved, "저축액 입력", new JLabel("거래 잔액과 별도로 직접 입력합니다.")), () -> {
            service.saveGoal(old == null ? null : old.id(), name.getText(), parseMoney(target.getText()), parseMoney(saved.getText()));
            changed("저축 목표를 저장했습니다.");
        });
    }

    private void deleteGoal() throws IOException {
        SavingsGoal goal = selected(goals, shownGoals);
        if (confirm("'" + goal.name() + "' 저축 목표를 삭제할까요?")) {
            service.deleteGoal(goal.id()); changed("저축 목표를 삭제했습니다.");
        }
    }

    private void changed(String text, YearMonth... affectedMonths) {
        refresh();
        List<String> alerts = new ArrayList<>();
        for (YearMonth affected : java.util.Arrays.stream(affectedMonths).distinct().toList()) {
            for (BudgetUsage b : service.budgetUsage(affected)) {
                if (b.status() != BudgetStatus.NORMAL) alerts.add(affected + " " + service.categoryName(b.budget().categoryId())
                        + " " + percent(b.percentage()) + " · " + b.status());
            }
        }
        message(text + (alerts.isEmpty() ? "" : "  |  " + String.join(" / ", alerts)), alerts.isEmpty() ? ACCENT : WARNING);
    }

    private void message(String text, Color color) {
        status.setText(text); status.setToolTipText(text); status.setForeground(color);
    }

    @FunctionalInterface private interface Action { void execute() throws IOException; }

    private void run(Action action) {
        try { action.execute(); } catch (IOException e) {
            error("파일 저장에 실패했습니다. 변경 사항은 반영되지 않았습니다.\n" + e.getMessage());
        } catch (IllegalArgumentException e) { error(e.getMessage()); }
    }

    private void submit(String title, JPanel form, Action action) {
        while (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try { action.execute(); return; }
            catch (IOException e) { error("파일 저장에 실패했습니다. 입력 내용을 확인하고 다시 시도해 주세요.\n" + e.getMessage()); }
            catch (IllegalArgumentException e) { error(e.getMessage()); }
        }
    }

    private void error(String message) { JOptionPane.showMessageDialog(this, message, "확인해 주세요", JOptionPane.ERROR_MESSAGE); }
    private boolean confirm(String message) { return JOptionPane.showConfirmDialog(this, message, "삭제 확인", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }
    private JButton button(String label, Action action) {
        JButton button = new JButton(label);
        button.addActionListener(e -> run(action));
        return button;
    }

    private record Choice(UUID id, String name) { @Override public String toString() { return name; } }
    private void fillCategories(JComboBox<Choice> box, String first, UUID selected) {
        box.removeAllItems();
        if (first != null) box.addItem(new Choice(null, first));
        service.data().categories().stream().sorted(Comparator.comparing(Category::name))
                .forEach(c -> box.addItem(new Choice(c.id(), c.name())));
        for (int i = 0; i < box.getItemCount(); i++) {
            if (java.util.Objects.equals(box.getItemAt(i).id(), selected)) { box.setSelectedIndex(i); break; }
        }
    }

    private static UUID selectedChoice(JComboBox<Choice> box) {
        Choice selected = (Choice) box.getSelectedItem();
        return selected == null ? null : selected.id();
    }

    private static <T> T selected(JTable table, List<T> items) {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("목록에서 항목을 먼저 선택해 주세요.");
        return items.get(table.convertRowIndexToModel(row));
    }

    private static JPanel page() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(18, 12, 12, 12));
        return page;
    }
    private static JPanel toolbar(Component... items) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 5));
        panel.setOpaque(false);
        for (Component item : items) panel.add(item);
        return panel;
    }
    private static JLabel valueLabel() {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f)); label.setForeground(INK);
        return label;
    }
    private static JPanel card(String title, JLabel value, String subtitle) {
        JPanel panel = new JPanel(new GridLayout(3, 1, 0, 6));
        panel.setBackground(Color.WHITE); panel.setBorder(new EmptyBorder(15, 16, 15, 16));
        panel.add(new JLabel(title)); panel.add(value); panel.add(hint(subtitle));
        return panel;
    }
    private static JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Color.WHITE); panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, BorderLayout.NORTH); panel.add(content, BorderLayout.CENTER);
        return panel;
    }
    private static JLabel hint(String text) {
        JLabel label = new JLabel(text); label.setForeground(MUTED); label.setFont(label.getFont().deriveFont(12f));
        return label;
    }
    private static JPanel form(Object... items) {
        JPanel form = new JPanel(new GridLayout(items.length / 2, 2, 12, 12));
        form.setBorder(new EmptyBorder(8, 6, 8, 6));
        form.setPreferredSize(new Dimension(480, items.length / 2 * 44));
        for (int i = 0; i < items.length; i += 2) {
            form.add(new JLabel(items[i].toString())); form.add((Component) items[i + 1]);
        }
        return form;
    }

    private static JTable table(String[] columns, Class<?>... types) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) { return types[column]; }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Long.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(value == null ? "" : NUMBER.format(value));
            }
        });
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                String text = value == null ? "" : value.toString();
                if (!selected) setForeground(text.equals(BudgetStatus.OVER.toString()) ? DANGER
                        : text.equals(BudgetStatus.WARNING.toString()) ? WARNING : text.equals("달성") ? ACCENT : INK);
                setToolTipText(text);
                return this;
            }
        });
        return table;
    }

    private static void rows(JTable table, List<Object[]> values) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Object[] row : values) model.addRow(row);
    }

    private static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        private final boolean budget;
        ProgressRenderer(boolean budget) { super(0, 100); this.budget = budget; setStringPainted(true); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            double amount = value instanceof Number number ? number.doubleValue() : 0;
            setValue((int) Math.min(100, amount));
            setString(value == null ? "—" : percent(amount));
            setForeground(budget && amount > 100 ? DANGER : budget && amount >= 90 ? WARNING : ACCENT);
            setToolTipText(value == null ? null : percent(amount));
            return this;
        }
    }

    private record Spending(String name, long amount) { }
    private static class ExpenseChart extends JPanel {
        private List<Spending> spending = List.of();
        private long total;
        ExpenseChart() { setBackground(Color.WHITE); }
        void update(List<Spending> data, long total) {
            this.spending = data; this.total = total;
            setPreferredSize(new Dimension(320, Math.max(180, data.size() * 66 + 26)));
            revalidate(); repaint();
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont());
            if (spending.isEmpty()) {
                g.setColor(MUTED); g.drawString("선택한 월에 지출 데이터가 없습니다.", 14, 45);
            } else {
                int width = Math.max(10, getWidth() - 28);
                for (int i = 0; i < spending.size(); i++) {
                    Spending item = spending.get(i);
                    int y = 28 + i * 66;
                    String amount = won(item.amount()) + " · " + percent(item.amount() * 100.0 / total);
                    FontMetrics metrics = g.getFontMetrics();
                    int amountWidth = metrics.stringWidth(amount);
                    g.setColor(INK);
                    g.drawString(clip(item.name(), metrics, Math.max(20, width - amountWidth - 16)), 14, y);
                    g.setColor(MUTED); g.drawString(amount, Math.max(14, getWidth() - 14 - amountWidth), y);
                    g.setColor(new Color(231, 239, 237)); g.fillRoundRect(14, y + 10, width, 12, 10, 10);
                    g.setColor(ACCENT); g.fillRoundRect(14, y + 10, Math.max(2, (int) (width * (item.amount() / (double) total))), 12, 10, 10);
                }
            }
            g.dispose();
        }
    }

    private static String clip(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        while (!text.isEmpty() && metrics.stringWidth(text + "…") > width) text = text.substring(0, text.length() - 1);
        return text + "…";
    }
    private static String won(long value) { return NUMBER.format(value) + "원"; }
    private static String percent(double value) { return String.format(Locale.KOREA, "%.1f%%", value); }
    private static long parseMoney(String text) {
        String value = text.strip();
        if (!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"))
            throw new IllegalArgumentException("금액은 원 단위 정수로 입력해 주세요. 예: 12000 또는 12,000");
        try { return Long.parseLong(value.replace(",", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("입력 금액이 지원 가능한 범위를 초과합니다."); }
    }
    private static LocalDate optionalDate(String text) { return text.isBlank() ? null : parseDate(text); }
    private static LocalDate parseDate(String text) {
        try { return LocalDate.parse(text.strip()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-10-06"); }
    }
    private static YearMonth parseMonth(String text) {
        try { return YearMonth.parse(text.strip()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("월을 YYYY-MM 형식으로 입력해 주세요. 예: 2026-10"); }
    }

    /**
     * Selects Nimbus when available and configures fonts and Korean dialog labels.
     * Call before constructing the Swing interface; if Nimbus is unavailable, the default theme remains.
     */
    public static void applyTheme() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (info.getName().equals("Nimbus")) { UIManager.setLookAndFeel(info.getClassName()); break; }
            }
            var keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                if (UIManager.get(key) instanceof FontUIResource)
                    UIManager.put(key, new FontUIResource("SansSerif", Font.PLAIN, 13));
            }
            UIManager.put("OptionPane.okButtonText", "확인");
            UIManager.put("OptionPane.cancelButtonText", "취소");
            UIManager.put("OptionPane.yesButtonText", "삭제");
            UIManager.put("OptionPane.noButtonText", "취소");
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Nimbus를 사용할 수 없으면 기본 Swing 테마를 사용한다.
        }
    }
}
