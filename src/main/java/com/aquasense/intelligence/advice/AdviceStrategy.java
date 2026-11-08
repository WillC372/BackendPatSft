package com.aquasense.intelligence.advice;

import com.aquasense.profile.Classification;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;

/**
 * Strategy: every water use context interprets the same pH differently.
 */
public interface AdviceStrategy {

    ProfileType type();

    Advice advise(Classification classification, WaterProfile profile);
}
