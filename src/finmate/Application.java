package finmate;

import finmate.application.FinanceService;
import finmate.infrastructure.FileFinanceRepository;
import finmate.ui.FinMateWindow;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Starts the standalone FinMate Swing application.
 * The application uses an injected file repository and creates its window on the Swing event dispatch thread.
 */
public final class Application {
    /**
     * Starts the application.
     * @param args command-line arguments; currently unused
     */
    public static void main(String[] args) {
        new Application().run();
    }

    /**
     * Schedules application initialization on the Swing event dispatch thread.
     * The {@code finmate.data} system property selects the storage file; the default is
     * {@code .finmate/data.properties} relative to the working directory.
     * Startup errors are shown in a dialog without replacing the original data file.
     */
    public void run() {
        SwingUtilities.invokeLater(() -> {
            FinMateWindow.applyTheme();
            Path file = Path.of(System.getProperty("finmate.data", ".finmate/data.properties")).toAbsolutePath();
            try {
                var repository = new FileFinanceRepository(file);
                var service = new FinanceService(repository);
                new FinMateWindow(service, file.toString()).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "FinMate를 시작할 수 없습니다.\n원본 데이터를 보존합니다.\n"
                        + e.getMessage(), "실행 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
