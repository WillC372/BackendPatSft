package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.intelligence.chat.ChatMemory;
import com.aquasense.intelligence.chat.ChatMessage;
import com.aquasense.intelligence.scenario.ScenarioExplainerProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatMemoryAndScenarioTest {

    private static ChatMessage message(boolean assistant, String text) {
        return new ChatMessage(1L, assistant ? ChatMessage.Role.ASSISTANT : ChatMessage.Role.USER, text, null, List.of());
    }

    @Test
    void memoryKeepsTheMostRecentTurnsStartingWithTheUser() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(message(i % 2 == 1, "mensaje " + i));
        }
        ChatMemory.Window window = ChatMemory.select(messages);
        assertThat(window.turns()).hasSize(12);
        assertThat(window.turns().get(0).assistant()).isFalse();
        assertThat(window.turns().get(0).content()).isEqualTo("mensaje 8");
        assertThat(window.turns().get(11).content()).isEqualTo("mensaje 19");
        assertThat(window.omitted()).isEqualTo(8);
        assertThat(ChatMemory.select(List.of()).turns()).isEmpty();
    }

    @Test
    void memoryRespectsTheCharacterBudget() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(message(i % 2 == 1, "x".repeat(2500)));
        }
        ChatMemory.Window window = ChatMemory.select(messages);
        assertThat(window.turns().stream().mapToInt(t -> t.content().length()).sum()).isLessThanOrEqualTo(9000);
        assertThat(window.turns().get(0).assistant()).isFalse();
    }

    @Test
    void scenarioTimeToReachAndHydrogenFactorAreComputedBySystem() {
        TrendResult falling = new TrendResult(true, "", 10, 7.0, 6.8, 7.3, 0.1, -0.1, "FALLING", "Descendente", null, null, Map.of());
        assertThat(ScenarioExplainerProbe.timeToReach(7.0, 6.5, falling)).contains("unos 5 días");
        assertThat(ScenarioExplainerProbe.timeToReach(7.0, 7.5, falling)).contains("no se dirige");
        assertThat(ScenarioExplainerProbe.timeToReach(null, 7.5, falling)).contains("Sin medición");
        assertThat(ScenarioExplainerProbe.hydrogenFactor(7.0, 6.0)).isEqualTo(10.0);
        assertThat(ScenarioExplainerProbe.hydrogenFactor(7.0, 9.5)).isEqualTo(0.00316);
    }
}
