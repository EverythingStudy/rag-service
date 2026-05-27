package cn.project.base.ragpgvector.dto;

import java.util.List;

public class IngestionResult {

    private String status;
    private int totalChunks;
    private List<String> processedFiles;
    private List<String> errors;

    public IngestionResult() {}

    public IngestionResult(String status, int totalChunks, List<String> processedFiles, List<String> errors) {
        this.status = status;
        this.totalChunks = totalChunks;
        this.processedFiles = processedFiles;
        this.errors = errors;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public List<String> getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(List<String> processedFiles) {
        this.processedFiles = processedFiles;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public static IngestionResult success(int totalChunks, List<String> processedFiles) {
        return new IngestionResult("SUCCESS", totalChunks, processedFiles, List.of());
    }

    public static IngestionResult partial(int totalChunks, List<String> processedFiles, List<String> errors) {
        return new IngestionResult("PARTIAL", totalChunks, processedFiles, errors);
    }

    public static IngestionResult error(String message) {
        return new IngestionResult("ERROR", 0, List.of(), List.of(message));
    }
}
