package com.aquasense.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registry of the agent tools. Spring injects every {@link AgentTool} implementation;
 * adding a new tool does not require changing the agent.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper;

    public AgentToolRegistry(List<AgentTool> available, ObjectMapper mapper) {
        available.forEach(tool -> tools.put(tool.name(), tool));
        this.mapper = mapper;
    }

    public AgentTool find(String name) {
        return tools.get(name);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public ArrayNode specification() {
        ArrayNode spec = mapper.createArrayNode();
        tools.values().forEach(tool -> add(spec, tool.name(), tool.description(), tool.parameters()));
        add(spec, SubmitReportSchema.TOOL_NAME, SubmitReportSchema.DESCRIPTION, SubmitReportSchema.parameters());
        return spec;
    }

    private void add(ArrayNode spec, String name, String description, Map<String, Object> parameters) {
        ObjectNode function = spec.addObject().put("type", "function").putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", mapper.valueToTree(parameters));
    }
}
