package com.aquaassistant.intelligence.advice;

import com.aquaassistant.profile.Classification;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;

/**
 * Strategy: every water use context interprets the same pH differently.
 */
public interface AdviceStrategy {

    ProfileType type();

    Advice advise(Classification classification, WaterProfile profile);
}
