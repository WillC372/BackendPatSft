package com.aquasense.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentReportValidator;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.agent.AgentToolRegistry;
import com.aquasense.intelligence.agent.ReportAgent;
import com.aquasense.intelligence.agent.TraceStep;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiTextProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportAgentTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> usersSeenByTools = new ArrayList<>();

    private final AgentTool stats = new AgentTool() {
        public String name() { return "get_statistics"; }
        public String description() { return "stats"; }
        public Map<String, Object> parameters() { return Map.of("type", "object", "properties", Map.of()); }
        public Object execute(AgentContext context, JsonNode arguments) {
            usersSeenByTools.add(context.userId());
            return Map.of("promedio", 7.17, "minimo", 6.55, "dias", context.clampDays(arguments.path("days").asInt()));
        }
        public String label(JsonNode arguments) { return "Calculó estadísticas"; }
        public String summarize(Object result) { return "promedio 7.17"; }
    };

    private String call(String id, String name, String args) {
        return "{\"id\":\"" + id + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":"
            + mapper.valueToTree(args) + "}}";
    }

    private AiTextProvider scripted(String... assistantMessages) {
        Deque<String> script = new ArrayDeque<>(List.of(assistantMessages));
        return new AiTextProvider() {
            public boolean isAvailable() { return true; }
            public String providerName() { return "Fake"; }
            public String modelName() { return "fake-model"; }
            public boolean supportsTools() { return true; }
            public AiAnswer complete(AiPrompt prompt) { throw new UnsupportedOperationException(); }
            public ToolTurn chatWithTools(ArrayNode messages, ArrayNode tools) {
                try {
                    String next = script.isEmpty() ? "{\"role\":\"assistant\",\"content\":\"Listo\"}" : script.pollFirst();
                    return new ToolTurn(mapper.readTree(next), "fake-model", 100, 20);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };
    }

    @Test
    void investigatesWithToolsCorrectsInvalidReportAndVerifiesNumbers() {
        String report = "{\"title\":\"Informe\",\"executiveSummary\":\"Promedio 7.17, mínimo 6.55 y un valor 9.99.\","
            + "\"keyMetrics\":[],\"sections\":[{\"heading\":\"A\",\"body\":\"x\",\"chart\":\"NONE\"},{\"heading\":\"B\",\"body\":\"y\",\"chart\":\"PH_EVOLUTION\"}],"
            + "\"recommendations\":[{\"action\":\"Medir\",\"priority\":1,\"rationale\":\"\"}],\"limitations\":[]}";
        AiTextProvider provider = scripted(
            "{\"role\":\"assistant\",\"tool_calls\":[" + call("1", "get_statistics", "{\"days\":400}") + "]}",
            "{\"role\":\"assistant\",\"tool_calls\":[" + call("2", "submit_report", "{\"title\":\"sin secciones\"}") + "]}",
            "{\"role\":\"assistant\",\"tool_calls\":[" + call("3", "submit_report", report) + "]}");
        ReportAgent agent = new ReportAgent(new AgentToolRegistry(List.of(stats), mapper), new AgentReportValidator(), mapper);
        List<TraceStep> trace = new ArrayList<>();
        ReportAgent.Result result = agent.run(provider, new AgentContext(42L, 7, 30, 3), "Informe general", trace::add, 0);

        assertThat(usersSeenByTools).containsExactly(42L);
        assertThat(result.toolCalls()).isEqualTo(1);
        assertThat(result.verification().verified()).isEqualTo(2);
        assertThat(result.verification().unverified()).containsExactly("9.99");
        assertThat(trace).extracting(TraceStep::status).containsExactly("OK", "RETRY", "OK");
        assertThat(result.inputTokens()).isEqualTo(300);
    }

    @Test
    void failsWhenTheModelNeverDeliversTheReport() {
        ReportAgent agent = new ReportAgent(new AgentToolRegistry(List.of(stats), mapper), new AgentReportValidator(), mapper);
        assertThatThrownBy(() -> agent.run(scripted(), new AgentContext(1L, 7, 30, 3), "x", s -> { }, 0))
            .isInstanceOf(ReportAgent.AgentFailedException.class);
    }

    @Test
    void clampsRequestedDaysToThePlanLimit() {
        assertThat(new AgentContext(1L, 7, 30, 3).clampDays(400)).isEqualTo(30);
        assertThat(new AgentContext(1L, 7, 30, 3).clampDays(0)).isEqualTo(7);
    }
}
