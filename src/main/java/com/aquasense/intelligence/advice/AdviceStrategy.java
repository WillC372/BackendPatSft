package com.aquasense.intelligence.advice;

import com.aquasense.profile.Classification;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;

/**
 * Strategy: cada contexto de uso del agua interpreta el mismo pH de forma distinta.
 */
public interface AdviceStrategy {

    ProfileType type();

    Advice advise(Classification classification, WaterProfile profile);
}
