package cn.project.base.ragpgvector.service;

import cn.project.base.ragpgvector.dto.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QaRecordService {

    private static final Logger log = LoggerFactory.getLogger(QaRecordService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ReentrantLock lock = new ReentrantLock();

    @Value("${rag.qa.record.doc-dir:doc}")
    private String docDir;

    /**
     * Record a Q&A pair to the daily document file.
     * Thread-safe: uses ReentrantLock for concurrent access.
     */
    public void record(String question, String answer, List<QueryResponse.Reference> references) {
        Path file = getDailyFile();
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            String entry = buildEntry(question, answer, references);
            Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Q&A record saved to {}", file);
        } catch (IOException e) {
            log.warn("Failed to save Q&A record: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private Path getDailyFile() {
        return Path.of(docDir, LocalDate.now().toString() + ".md");
    }

    private String buildEntry(String question, String answer, List<QueryResponse.Reference> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("## ").append(LocalDateTime.now().format(TIME_FMT)).append("\n\n");
        sb.append("**Q**: ").append(question).append("\n\n");
        sb.append("**A**: ").append(answer).append("\n\n");

        if (references != null && !references.isEmpty()) {
            sb.append("**References**:\n");
            for (QueryResponse.Reference ref : references) {
                sb.append("- [").append(ref.getSource()).append("]")
                        .append(" (score: ").append(String.format("%.3f", ref.getScore())).append(")\n");
                String content = ref.getContent();
                if (content.length() > 120) {
                    content = content.substring(0, 120) + "...";
                }
                sb.append("  ").append(content).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
