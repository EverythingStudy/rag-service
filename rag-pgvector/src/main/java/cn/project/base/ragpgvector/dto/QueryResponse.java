package cn.project.base.ragpgvector.dto;

import java.util.List;

public class QueryResponse {

    private String answer;
    private List<Reference> references;

    public QueryResponse() {}

    public QueryResponse(String answer, List<Reference> references) {
        this.answer = answer;
        this.references = references;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Reference> getReferences() {
        return references;
    }

    public void setReferences(List<Reference> references) {
        this.references = references;
    }

    public static class Reference {
        private String content;
        private String source;
        private double score;

        public Reference() {}

        public Reference(String content, String source, double score) {
            this.content = content;
            this.source = source;
            this.score = score;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
