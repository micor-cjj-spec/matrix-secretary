package com.kailei.demo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class PythonSemanticClient {

    private final RestClient restClient;
    private final String semanticUrl;

    public PythonSemanticClient(RestClient restClient,
                                @Value("${ai-secretary.python-semantic-url}") String semanticUrl) {
        this.restClient = restClient;
        this.semanticUrl = semanticUrl;
    }

    public PythonParseResponse parse(PythonParseRequest request) {
        return restClient.post()
                .uri(semanticUrl)
                .body(request)
                .retrieve()
                .body(PythonParseResponse.class);
    }

    public record PythonParseRequest(
            String text,
            String timezone,
            @JsonProperty("user_id") String userId,
            @JsonProperty("trace_id") String traceId
    ) {
    }

    public record PythonParseResponse(
            @JsonProperty("trace_id") String traceId,
            String language,
            String summary,
            List<PythonTaskAction> tasks,
            List<String> warnings
    ) {
    }

    public record PythonTaskAction(
            @JsonProperty("action_id") String actionId,
            @JsonProperty("action_type") String actionType,
            @JsonProperty("skill_name") String skillName,
            String title,
            String content,
            TaskTarget target,
            TaskSchedule schedule,
            Map<String, Object> args,
            String priority,
            Double confidence,
            @JsonProperty("requires_confirmation") Boolean requiresConfirmation,
            @JsonProperty("source_sentence") String sourceSentence,
            @JsonProperty("analysis_note") String analysisNote
    ) {
    }
}
