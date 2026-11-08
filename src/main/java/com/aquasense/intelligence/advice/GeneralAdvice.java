package com.aquasense.intelligence.advice;

import com.aquasense.profile.ProfileType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GeneralAdvice extends BaseAdviceStrategy {

    @Override
    public ProfileType type() {
        return ProfileType.GENERAL;
    }

    @Override
    protected List<String> lowCauses() {
        return List.of("Disolución de CO2 o lluvia ácida.", "Materia orgánica en descomposición.",
            "Sonda sin calibrar o contaminada.");
    }

    @Override
    protected List<String> highCauses() {
        return List.of("Contacto con rocas o materiales calcáreos.", "Residuos de limpiadores alcalinos.",
            "Sonda sin calibrar.");
    }

    @Override
    protected List<String> lowActions() {
        return List.of("Repite la medición después de enjuagar la sonda con agua destilada.",
            "Identifica el uso final del agua y elige el perfil correspondiente para una recomendación precisa.");
    }

    @Override
    protected List<String> highActions() {
        return lowActions();
    }
}
