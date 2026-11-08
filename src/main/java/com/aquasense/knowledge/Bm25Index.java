package com.aquasense.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * In-memory BM25 index (Okapi BM25, k1 = 1.2, b = 0.75). It is deterministic, needs no external services
 * and is enough for a small curated knowledge base: every result can be explained by the matching terms.
 */
public final class Bm25Index {

    static final double K1 = 1.2;
    static final double B = 0.75;

    private final List<Map<String, Integer>> termFrequencies = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private double averageLength;

    public Bm25Index(List<String> documents) {
        long total = 0;
        for (String document : documents) {
            List<String> terms = TextAnalyzer.terms(document);
            Map<String, Integer> tf = new HashMap<>();
            terms.forEach(t -> tf.merge(t, 1, Integer::sum));
            tf.keySet().forEach(t -> documentFrequency.merge(t, 1, Integer::sum));
            termFrequencies.add(tf);
            lengths.add(terms.size());
            total += terms.size();
        }
        averageLength = documents.isEmpty() ? 0 : (double) total / documents.size();
    }

    public int size() {
        return lengths.size();
    }

    /** BM25 score of every document for the query (0 if it shares no terms). */
    public double[] score(String query) {
        double[] scores = new double[size()];
        int n = size();
        for (String term : new HashSet<>(TextAnalyzer.terms(query))) {
            Integer df = documentFrequency.get(term);
            if (df == null) {
                continue;
            }
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (int i = 0; i < n; i++) {
                Integer f = termFrequencies.get(i).get(term);
                if (f != null) {
                    double norm = K1 * (1 - B + B * lengths.get(i) / averageLength);
                    scores[i] += idf * (f * (K1 + 1)) / (f + norm);
                }
            }
        }
        return scores;
    }
}
