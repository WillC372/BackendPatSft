package com.aquasense.intelligence.agent.tools;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class AgentFormats {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private AgentFormats() {
    }
}
