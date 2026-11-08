package com.aquasense.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.intelligence.agent.AgentReport;
import com.aquasense.intelligence.agent.AgentReportValidator;
import com.aquasense.intelligence.agent.Verification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentReportValidatorTest {

    private final AgentReportValidator validator = new AgentReportValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String VALID = """
        {"title":"Informe semanal","executiveSummary":"El promedio fue 7.17 y el mínimo 6,55.",
         "keyMetrics":[{"label":"Promedio","value":"7.2"}],
         "sections":[{"heading":"Tendencia","body":"Pendiente de 0.013 pH por día.","chart":"PH_EVOLUTION"},
                     {"heading":"Anomalías","body":"Un valor de 6.83 sin explicación.","chart":"INVENTADA"}],
         "recommendations":[{"action":"Medir mañana","priority":3,"rationale":""},{"action":"Revisar sonda","priority":1,"rationale":"valor atípico"}],
         "limitations":["Pocos datos"]}""";

    @Test
    void parsesReportAndNormalizesChartAndPriorityOrder() throws Exception {
        AgentReport report = validator.parse(mapper.readTree(VALID));
        assertThat(report.sections()).hasSize(2);
        assertThat(report.sections().get(1).chart()).isEqualTo(AgentReport.ChartKind.NONE);
        assertThat(report.recommendations().get(0).priority()).isEqualTo(1);
    }

    @Test
    void verifiesNumbersAgainstToolResultsIncludingRounding() throws Exception {
        AgentReport report = validator.parse(mapper.readTree(VALID));
        Verification v = validator.verify(report, Set.of(7.17, 6.55, 0.013));
        assertThat(v.checked()).isEqualTo(5);
        assertThat(v.verified()).isEqualTo(4);
        assertThat(v.unverified()).containsExactly("6.83");
    }

    @Test
    void rejectsReportsWithoutEnoughSectionsOrSummary() throws Exception {
        assertThatThrownBy(() -> validator.parse(mapper.readTree("{\"title\":\"x\",\"executiveSummary\":\"y\",\"sections\":[],\"recommendations\":[]}")))
            .isInstanceOf(AgentReportValidator.InvalidReportException.class);
        assertThatThrownBy(() -> validator.parse(mapper.readTree(VALID.replace("\"El promedio fue 7.17 y el mínimo 6,55.\"", "\"\""))))
            .isInstanceOf(AgentReportValidator.InvalidReportException.class).hasMessageContaining("executiveSummary");
    }
}
