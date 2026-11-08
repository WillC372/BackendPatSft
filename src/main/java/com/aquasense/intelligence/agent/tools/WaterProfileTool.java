package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WaterProfileTool implements AgentTool {

    private final ProfileService profileService;

    public WaterProfileTool(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "get_water_profile";
    }

    @Override
    public String description() {
        return "Devuelve el perfil de agua activo del usuario (tipo de uso, rango ideal, rango de precaución y fuente de los rangos).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        WaterProfile p = profileService.activeProfile(context.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nombre", p.getName());
        result.put("tipo", p.getType().label());
        result.put("rangoIdeal", List.of(p.getIdealMin(), p.getIdealMax()));
        result.put("rangoPrecaucion", List.of(p.getCautionMin(), p.getCautionMax()));
        result.put("fuera_de_precaucion", "crítico");
        result.put("fuente", p.getSource());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Consultó el perfil de agua activo";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return map.get("nombre") + ", rango ideal " + map.get("rangoIdeal");
    }
}
