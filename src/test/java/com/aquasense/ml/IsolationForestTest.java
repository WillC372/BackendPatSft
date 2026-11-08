package com.aquasense.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.aquasense.intelligence.ml.IsolationForest;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IsolationForestTest {

    private static double[][] clusterWithOutliers() {
        Random random = new Random(1);
        double[][] data = new double[305][];
        for (int i = 0; i < 300; i++) {
            data[i] = new double[] {random.nextGaussian() * 0.3, random.nextGaussian() * 0.3};
        }
        for (int i = 0; i < 5; i++) {
            data[300 + i] = new double[] {4 + i * 0.5, -4 - i * 0.3};
        }
        return data;
    }

    @Test
    void outliersScoreHigherThanTheNormalCluster() {
        double[][] data = clusterWithOutliers();
        IsolationForest forest = IsolationForest.train(data, 100, 256, 42);
        double inliers = 0;
        for (int i = 0; i < 300; i++) {
            inliers += forest.score(data[i]);
        }
        inliers /= 300;
        for (int i = 300; i < 305; i++) {
            assertThat(forest.score(data[i])).isGreaterThan(0.6).isGreaterThan(inliers + 0.15);
        }
        assertThat(inliers).isLessThan(0.5);
    }

    @Test
    void sameSeedGivesSameModel() {
        double[][] data = clusterWithOutliers();
        double a = IsolationForest.train(data, 50, 128, 7).score(new double[] {1, 1});
        double b = IsolationForest.train(data, 50, 128, 7).score(new double[] {1, 1});
        assertThat(a).isEqualTo(b);
    }

    @Test
    void averagePathLengthMatchesThePaper() throws Exception {
        var method = IsolationForest.class.getDeclaredMethod("averagePathLength", int.class);
        method.setAccessible(true);
        assertThat((double) method.invoke(null, 256)).isCloseTo(10.24, within(0.01));
        assertThat((double) method.invoke(null, 2)).isEqualTo(1.0);
        assertThat((double) method.invoke(null, 1)).isEqualTo(0.0);
    }
}
