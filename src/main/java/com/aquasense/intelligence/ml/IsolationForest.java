package com.aquasense.intelligence.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Isolation Forest (Liu, Ting and Zhou, 2008), implemented without external dependencies.
 * Idea: an anomalous point is isolated with few random partitions. Trees are built with random
 * subsets of the data; the average depth at which a point gets isolated defines its anomaly score
 * s(x) = 2^(-E[h(x)] / c(n)), between 0 and 1: close to 1 is anomalous, around 0.5 or less is normal.
 */
public final class IsolationForest {

    private static final double EULER = 0.5772156649;

    private final List<Node> trees;
    private final int sampleSize;

    private IsolationForest(List<Node> trees, int sampleSize) {
        this.trees = trees;
        this.sampleSize = sampleSize;
    }

    public static IsolationForest train(double[][] data, int treeCount, int maxSample, long seed) {
        if (data.length < 2) {
            throw new IllegalArgumentException("Isolation Forest needs at least 2 samples");
        }
        Random random = new Random(seed);
        int sample = Math.min(maxSample, data.length);
        int heightLimit = (int) Math.ceil(Math.log(sample) / Math.log(2));
        List<Node> trees = new ArrayList<>(treeCount);
        for (int t = 0; t < treeCount; t++) {
            trees.add(build(subsample(data, sample, random), 0, heightLimit, random));
        }
        return new IsolationForest(trees, sample);
    }

    public double score(double[] point) {
        double total = 0;
        for (Node tree : trees) {
            total += pathLength(tree, point, 0);
        }
        return Math.pow(2, -(total / trees.size()) / averagePathLength(sampleSize));
    }

    public int treeCount() {
        return trees.size();
    }

    public int sampleSize() {
        return sampleSize;
    }

    /** c(n): average length of an unsuccessful search in a binary search tree with n elements. */
    static double averagePathLength(int n) {
        if (n <= 1) {
            return 0;
        }
        if (n == 2) {
            return 1;
        }
        return 2 * (Math.log(n - 1) + EULER) - 2.0 * (n - 1) / n;
    }

    private static double pathLength(Node node, double[] point, int depth) {
        if (node.isLeaf()) {
            return depth + averagePathLength(node.size);
        }
        return pathLength(point[node.feature] < node.split ? node.left : node.right, point, depth + 1);
    }

    private static Node build(double[][] rows, int depth, int heightLimit, Random random) {
        if (depth >= heightLimit || rows.length <= 1) {
            return Node.leaf(rows.length);
        }
        int features = rows[0].length;
        int[] order = shuffledIndexes(features, random);
        for (int feature : order) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double[] row : rows) {
                min = Math.min(min, row[feature]);
                max = Math.max(max, row[feature]);
            }
            if (max > min) {
                double split = min + random.nextDouble() * (max - min);
                List<double[]> left = new ArrayList<>();
                List<double[]> right = new ArrayList<>();
                for (double[] row : rows) {
                    (row[feature] < split ? left : right).add(row);
                }
                return Node.split(feature, split, build(left.toArray(double[][]::new), depth + 1, heightLimit, random),
                    build(right.toArray(double[][]::new), depth + 1, heightLimit, random));
            }
        }
        return Node.leaf(rows.length);
    }

    private static double[][] subsample(double[][] data, int size, Random random) {
        int[] indexes = shuffledIndexes(data.length, random);
        double[][] sample = new double[size][];
        for (int i = 0; i < size; i++) {
            sample[i] = data[indexes[i]];
        }
        return sample;
    }

    private static int[] shuffledIndexes(int n, Random random) {
        int[] indexes = new int[n];
        Arrays.setAll(indexes, i -> i);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = indexes[i];
            indexes[i] = indexes[j];
            indexes[j] = tmp;
        }
        return indexes;
    }

    private static final class Node {
        final int feature;
        final double split;
        final Node left;
        final Node right;
        final int size;

        private Node(int feature, double split, Node left, Node right, int size) {
            this.feature = feature;
            this.split = split;
            this.left = left;
            this.right = right;
            this.size = size;
        }

        static Node leaf(int size) {
            return new Node(-1, 0, null, null, size);
        }

        static Node split(int feature, double split, Node left, Node right) {
            return new Node(feature, split, left, right, 0);
        }

        boolean isLeaf() {
            return left == null;
        }
    }
}
