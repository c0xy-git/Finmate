package finmate;

import finmate.application.FinanceService;
import finmate.domain.*;
import finmate.infrastructure.FileFinanceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static finmate.application.FinanceService.BudgetStatus.*;
import static finmate.domain.TransactionType.*;

/** 외부 테스트 라이브러리 없이 실행하는 요구사항/저장 검증. 실패하면 AssertionError. */
public final class FinanceServiceTest {
    private static int checks;
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("finmate-test-");
        try {
            verifyTransactionsAndBudgets(directory.resolve("data.properties"));
            verifyRepositoryContract();
            verifyFileFailures(directory);
            System.out.println("PASS: " + checks + " checks (R01-R11, persistence, failure handling)");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyTransactionsAndBudgets(Path file) throws Exception {
        FinanceService service = new FinanceService(new FileFinanceRepository(file));
        check(service.data().categories().size() == 9, "기본 카테고리");
        check(service.summary(OCTOBER).expenses() == 0 && service.summary(OCTOBER).byCategory().isEmpty(), "지출 없는 달");
        check(service.budgetUsage(OCTOBER).isEmpty(), "미설정 예산");
        UUID food = service.data().categories().stream().filter(c -> c.name().equals("식비")).findFirst().orElseThrow().id();
        UUID salary = service.data().categories().stream().filter(c -> c.name().equals("급여")).findFirst().orElseThrow().id();
        service.saveTransaction(null, DAY.minusMonths(1), INCOME, salary, 1_000_000, "", "이전 월");
        service.saveTransaction(null, DAY, INCOME, salary, 500_000, "이체", "급여");
        service.setBudget(OCTOBER, null, 200_000);
        service.setBudget(OCTOBER, food, 100_000);
        service.saveTransaction(null, DAY, EXPENSE, food, 89_999, "카드", "점심\n한글·기호 = : \\");
        UUID transaction = service.search(DAY, DAY, EXPENSE, food).get(0).id();
        check(usage(service, food).status() == NORMAL, "90% 미만 정상");
        service.saveTransaction(transaction, DAY, EXPENSE, food, 90_000, "카드", "점심\n한글·기호 = : \\");
        check(usage(service, food).status() == WARNING, "정확히 90% 경고");
        service.saveTransaction(transaction, DAY, EXPENSE, food, 100_000, "", "");
        check(usage(service, food).status() == WARNING, "정확히 100%는 초과가 아님");
        service.saveTransaction(transaction, DAY, EXPENSE, food, 100_001, "카드", "점심\n한글·기호 = : \\");
        check(usage(service, food).status() == OVER && usage(service, food).remaining() == -1, "예산 초과");
        check(usage(service, null).status() == NORMAL, "전체 예산은 별도 계산");
        check(service.summary(OCTOBER).income() == 500_000, "선택 월 수입");
        check(service.summary(OCTOBER).expenses() == 100_001, "선택 월 지출");
        check(service.summary(OCTOBER).balance() == 1_399_999, "모든 달 기준 잔액");
        check(service.summary(OCTOBER).byCategory().get(food) == 100_001, "카테고리 지출 집계");
        check(service.search(DAY, DAY, INCOME, null).size() == 1, "날짜·유형 검색");
        check(service.search(null, null, null, food).size() == 1, "카테고리 검색");
        check(service.search(DAY.plusDays(1), null, null, null).isEmpty(), "검색 결과 없음");
        reject(() -> service.search(DAY, DAY.minusDays(1), null, null), "역전된 검색 기간");
        reject(() -> service.saveTransaction(null, DAY, EXPENSE, food, 0, "", ""), "0원 거래 거부");
        reject(() -> service.saveTransaction(null, null, EXPENSE, food, 1, "", ""), "필수 날짜 검증");
        reject(() -> service.saveTransaction(null, DAY, EXPENSE, UUID.randomUUID(), 1, "", ""), "존재하지 않는 카테고리");
        reject(() -> service.saveTransaction(null, DAY, INCOME, salary, Long.MAX_VALUE, "", ""), "합계 오버플로 방지");
        reject(() -> service.setBudget(OCTOBER, null, -1), "음수 예산 거부");
        reject(() -> service.saveCategory(null, "  식비  "), "공백 정리 후 중복 이름 거부");
        reject(() -> service.saveCategory(null, "  "), "빈 카테고리 거부");
        reject(() -> service.deleteCategory(food), "거래·예산 참조 삭제 방지");
        service.saveCategory(food, "외식비");
        check(service.categoryName(food).equals("외식비"), "카테고리 이름 변경");
        check(service.search(null, null, null, food).size() == 1 && usage(service, food).spent() == 100_001, "이름 변경 시 참조 유지");
        service.saveCategory(null, "임시");
        UUID unused = service.data().categories().stream().filter(c -> c.name().equals("임시")).findFirst().orElseThrow().id();
        service.deleteCategory(unused);
        check(service.data().categories().stream().noneMatch(c -> c.id().equals(unused)), "미사용 카테고리 삭제");

        service.saveGoal(null, "여행", 1_000_000, 0);
        UUID goal = service.data().goals().get(0).id();
        check(service.data().goals().get(0).progress() == 0, "0원 저축 허용");
        reject(() -> service.saveGoal(null, "오류", 0, 0), "목표액 0 거부");
        reject(() -> service.saveGoal(null, "오류", 100, -1), "음수 저축 거부");
        service.saveGoal(goal, "여행", 1_000_000, 1_000_000);
        check(service.data().goals().get(0).achieved(), "정확히 목표 달성");
        service.saveGoal(goal, "여행", 1_000_000, 1_200_000);
        check(service.data().goals().get(0).progress() == 120, "100% 초과 달성률");
        check(service.summary(OCTOBER).balance() == 1_399_999, "저축액은 거래 잔액과 독립");

        FinanceService reopened = new FinanceService(new FileFinanceRepository(file));
        check(reopened.data().transactions().equals(service.data().transactions()), "거래·한글·줄바꿈 저장/복원");
        check(reopened.data().budgets().equals(service.data().budgets()), "월·카테고리별 예산 복원");
        check(reopened.categoryName(food).equals("외식비"), "카테고리 이름·ID 복원");
        check(reopened.data().goals().get(0).saved() == 1_200_000 && reopened.data().goals().get(0).id().equals(goal), "저축 목표 복원");
        service.saveTransaction(transaction, DAY.plusMonths(1), EXPENSE, food, 100_001, "", "");
        check(usage(service, food).spent() == 0 && service.summary(OCTOBER.plusMonths(1)).expenses() == 100_001, "거래 월 수정 시 양쪽 월 재계산");
        service.saveTransaction(transaction, DAY, INCOME, salary, 100_001, "", "");
        check(service.summary(OCTOBER).expenses() == 0 && service.summary(OCTOBER).income() == 600_001, "유형·카테고리 변경 재계산");
        reject(() -> service.deleteCategory(food), "예산에서만 사용하는 카테고리 삭제 방지");
        service.deleteTransaction(transaction);
        check(service.summary(OCTOBER).balance() == 1_500_000, "거래 삭제 후 잔액 갱신");
        service.setBudget(OCTOBER, food, 300_000);
        check(service.data().budgets().size() == 2 && usage(service, food).budget().amount() == 300_000, "같은 범위 예산 수정");
        service.deleteGoal(goal);
        check(service.data().goals().isEmpty(), "저축 목표 삭제");
        check(new FinanceService(new FileFinanceRepository(file)).data().goals().isEmpty(), "삭제 내용 재시작 후 유지");
    }

    private static void verifyRepositoryContract() throws Exception {
        class MemoryRepository implements FinanceRepository {
            private FinanceData stored = FinanceData.empty();
            private boolean fail;
            @Override public FinanceData load() { return stored; }
            @Override public void save(FinanceData next) throws IOException {
                if (fail) throw new IOException("저장 실패 테스트");
                stored = next;
            }
        }
        MemoryRepository repository = new MemoryRepository();
        FinanceService service = new FinanceService(repository);
        service.saveGoal(null, "노트북", 100, 50);
        FinanceData before = service.data();
        repository.fail = true;
        try { service.saveGoal(null, "실패", 100, 0); throw new AssertionError("저장 실패가 전달되어야 함"); }
        catch (IOException expected) { check(service.data() == before && repository.stored == before, "저장 실패 시 메모리·저장소 원상태 유지"); }
        reject(() -> before.transactions().add(new Transaction(UUID.randomUUID(), DAY, EXPENSE, UUID.randomUUID(), 1, "", "")), "스냅샷 수정 불가", UnsupportedOperationException.class);
        NamedEntity entity = service.data().goals().get(0);
        check(entity instanceof Identifiable && entity.name().equals("노트북"), "추상 클래스·인터페이스 다형성");
    }

    private static void verifyFileFailures(Path directory) throws Exception {
        Path blocked = directory.resolve("blocked");
        FinanceService service = new FinanceService(new FileFinanceRepository(blocked.resolve("data.properties")));
        FinanceData before = service.data();
        Files.writeString(blocked, "directory creation must fail");
        try { service.saveGoal(null, "저장 오류", 100, 0); throw new AssertionError("쓰기 실패 필요"); }
        catch (IOException expected) { check(service.data() == before, "실제 파일 쓰기 실패 시 메모리 보존"); }

        Path corrupt = directory.resolve("corrupt.properties");
        String invalid = "version=1\ncategory.count=not-a-number\n";
        Files.writeString(corrupt, invalid);
        try { new FinanceService(new FileFinanceRepository(corrupt)); throw new AssertionError("손상 파일 거부 필요"); }
        catch (IOException expected) { check(Files.readString(corrupt).equals(invalid), "손상 파일을 빈 데이터로 덮어쓰지 않음"); }

        Path dangling = directory.resolve("dangling.properties");
        var bad = new FinanceData(List.of(), List.of(new Transaction(UUID.randomUUID(), DAY, EXPENSE, UUID.randomUUID(), 1, "", "")), List.of(), List.of());
        new FileFinanceRepository(dangling).save(bad);
        try { new FinanceService(new FileFinanceRepository(dangling)); throw new AssertionError("잘못된 참조 거부 필요"); }
        catch (IOException expected) { check(true, "불러올 때 도메인 제약 검증"); }
    }

    private static FinanceService.BudgetUsage usage(FinanceService service, UUID category) {
        return service.budgetUsage(OCTOBER).stream().filter(b -> java.util.Objects.equals(b.budget().categoryId(), category)).findFirst().orElseThrow();
    }
    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private static void reject(Work work, String label) throws Exception { reject(work, label, IllegalArgumentException.class); }
    private static void reject(Work work, String label, Class<? extends Exception> expected) throws Exception {
        try { work.run(); } catch (Exception e) {
            if (!expected.isInstance(e)) throw e;
            checks++; return;
        }
        throw new AssertionError(label);
    }
}
