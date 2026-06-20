package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.llm.ChatClient;
import com.example.rag.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {

    private final ChatClient chatClient;
    private final RagProperties properties;

    private static final Pattern QUESTION_PATTERN = Pattern.compile("\\d+\\.\\s*(.+)");

    public SuggestionService(ChatClient chatClient, RagProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    public List<String> generate(String question, List<SearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String block = "Source %d: %s\n".formatted(i + 1, result.text());
            if (context.length() + block.length() > properties.maxContextChars()) break;
            context.append(block);
        }

        String prompt = """
                Based on the following document context, suggest 3 follow-up questions the user could ask next.
                The questions should be related to the documents and the user's last question.
                Return only the 3 questions, one per line, numbered 1-3.

                Last question: %s

                Document context:
                %s
                """.formatted(question, context);

        String response = chatClient.complete(prompt);
        return parseQuestions(response);
    }

    private List<String> parseQuestions(String text) {
        List<String> questions = new ArrayList<>();
        Matcher matcher = QUESTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String q = matcher.group(1).trim();
            if (!q.isEmpty()) questions.add(q);
            if (questions.size() >= 3) break;
        }
        if (questions.isEmpty()) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && line.length() > 10) {
                    questions.add(line);
                    if (questions.size() >= 3) break;
                }
            }
        }
        return questions;
    }
}
