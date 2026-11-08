package com.aquasense.intelligence.deep;

import java.util.Arrays;
import java.util.Random;

/**
 * Single-layer LSTM recurrent neural network with a dense output, implemented in Java with no dependencies.
 *
 * <p>For every step t: z = Wx·x(t) + Wh·h(t-1) + b, with the gates i (input), f (forget), g (candidate) and
 * o (output); c(t) = f·c(t-1) + i·g and h(t) = o·tanh(c(t)). The output is y = Wy·h(T) + by (one prediction per horizon).
 * It is trained with backpropagation through time (BPTT), mean squared error, Adam and gradient clipping.
 * All parameters live in a single array, which simplifies the optimizer and the numerical gradient check.
 */
public final class LstmNetwork {

    private final int inputs;
    private final int hidden;
    private final int outputs;
    private final double[] params;
    private final int oWx;
    private final int oWh;
    private final int oB;
    private final int oWy;
    private final int oBy;

    public LstmNetwork(int inputs, int hidden, int outputs, long seed) {
        this.inputs = inputs;
        this.hidden = hidden;
        this.outputs = outputs;
        int gates = 4 * hidden;
        oWx = 0;
        oWh = oWx + gates * inputs;
        oB = oWh + gates * hidden;
        oWy = oB + gates;
        oBy = oWy + outputs * hidden;
        params = new double[oBy + outputs];
        Random random = new Random(seed);
        double limitX = Math.sqrt(6.0 / (inputs + hidden));
        double limitH = Math.sqrt(6.0 / (2.0 * hidden));
        double limitY = Math.sqrt(6.0 / (hidden + outputs));
        for (int k = oWx; k < oWh; k++) {
            params[k] = (random.nextDouble() * 2 - 1) * limitX;
        }
        for (int k = oWh; k < oB; k++) {
            params[k] = (random.nextDouble() * 2 - 1) * limitH;
        }
        for (int j = 0; j < hidden; j++) {
            params[oB + hidden + j] = 1.0;
        }
        for (int k = oWy; k < oBy; k++) {
            params[k] = (random.nextDouble() * 2 - 1) * limitY;
        }
    }

    public int parameterCount() {
        return params.length;
    }

    double[] parameters() {
        return params;
    }

    public double[] predict(double[][] sequence) {
        return forward(sequence).y;
    }

    /** Mean squared error of one sample; if {@code grad} is not null, it accumulates its gradient. */
    double lossAndGradient(double[][] sequence, double[] target, double[] grad) {
        Trace tr = forward(sequence);
        double loss = 0;
        double[] dy = new double[outputs];
        for (int k = 0; k < outputs; k++) {
            double e = tr.y[k] - target[k];
            loss += e * e;
            dy[k] = 2 * e / outputs;
        }
        loss /= outputs;
        if (grad != null) {
            backward(sequence, tr, dy, grad);
        }
        return loss;
    }

    private Trace forward(double[][] xs) {
        int steps = xs.length;
        int gates = 4 * hidden;
        Trace tr = new Trace(steps, hidden);
        double[] h = new double[hidden];
        double[] c = new double[hidden];
        double[] z = new double[gates];
        for (int t = 0; t < steps; t++) {
            double[] x = xs[t];
            for (int r = 0; r < gates; r++) {
                double s = params[oB + r];
                int wx = oWx + r * inputs;
                for (int k = 0; k < inputs; k++) {
                    s += params[wx + k] * x[k];
                }
                int wh = oWh + r * hidden;
                for (int k = 0; k < hidden; k++) {
                    s += params[wh + k] * h[k];
                }
                z[r] = s;
            }
            double[] hNew = new double[hidden];
            double[] cNew = new double[hidden];
            for (int j = 0; j < hidden; j++) {
                double ig = sigmoid(z[j]);
                double fg = sigmoid(z[hidden + j]);
                double gg = Math.tanh(z[2 * hidden + j]);
                double og = sigmoid(z[3 * hidden + j]);
                cNew[j] = fg * c[j] + ig * gg;
                hNew[j] = og * Math.tanh(cNew[j]);
                tr.i[t][j] = ig;
                tr.f[t][j] = fg;
                tr.g[t][j] = gg;
                tr.o[t][j] = og;
            }
            tr.cPrev[t] = c;
            tr.hPrev[t] = h;
            tr.c[t] = cNew;
            c = cNew;
            h = hNew;
        }
        tr.hLast = h;
        tr.y = new double[outputs];
        for (int k = 0; k < outputs; k++) {
            double s = params[oBy + k];
            for (int j = 0; j < hidden; j++) {
                s += params[oWy + k * hidden + j] * h[j];
            }
            tr.y[k] = s;
        }
        return tr;
    }

    private void backward(double[][] xs, Trace tr, double[] dy, double[] grad) {
        double[] dh = new double[hidden];
        for (int k = 0; k < outputs; k++) {
            grad[oBy + k] += dy[k];
            for (int j = 0; j < hidden; j++) {
                grad[oWy + k * hidden + j] += dy[k] * tr.hLast[j];
                dh[j] += params[oWy + k * hidden + j] * dy[k];
            }
        }
        double[] dc = new double[hidden];
        double[] dz = new double[4 * hidden];
        for (int t = xs.length - 1; t >= 0; t--) {
            for (int j = 0; j < hidden; j++) {
                double tc = Math.tanh(tr.c[t][j]);
                double o = tr.o[t][j];
                double i = tr.i[t][j];
                double f = tr.f[t][j];
                double g = tr.g[t][j];
                double dO = dh[j] * tc;
                double dC = dc[j] + dh[j] * o * (1 - tc * tc);
                dz[j] = dC * g * i * (1 - i);
                dz[hidden + j] = dC * tr.cPrev[t][j] * f * (1 - f);
                dz[2 * hidden + j] = dC * i * (1 - g * g);
                dz[3 * hidden + j] = dO * o * (1 - o);
                dc[j] = dC * f;
            }
            double[] x = xs[t];
            double[] hPrev = tr.hPrev[t];
            double[] dhPrev = new double[hidden];
            for (int r = 0; r < 4 * hidden; r++) {
                double d = dz[r];
                if (d == 0) {
                    continue;
                }
                grad[oB + r] += d;
                int wx = oWx + r * inputs;
                for (int k = 0; k < inputs; k++) {
                    grad[wx + k] += d * x[k];
                }
                int wh = oWh + r * hidden;
                for (int k = 0; k < hidden; k++) {
                    grad[wh + k] += d * hPrev[k];
                    dhPrev[k] += params[wh + k] * d;
                }
            }
            dh = dhPrev;
        }
    }

    /**
     * Trains with Adam in mini-batches and early stopping: the epoch with the lowest validation error is kept.
     */
    public TrainingLog train(double[][][] trainX, double[][] trainY, double[][][] valX, double[][] valY, Options options) {
        double[] m = new double[params.length];
        double[] v = new double[params.length];
        double[] best = params.clone();
        double bestVal = Double.MAX_VALUE;
        int bestEpoch = 0;
        int step = 0;
        int sinceBest = 0;
        Random random = new Random(options.seed());
        int[] order = new int[trainX.length];
        for (int k = 0; k < order.length; k++) {
            order[k] = k;
        }
        double[] trainCurve = new double[options.maxEpochs()];
        double[] valCurve = new double[options.maxEpochs()];
        int epochs = 0;
        for (int epoch = 0; epoch < options.maxEpochs(); epoch++) {
            shuffle(order, random);
            double epochLoss = 0;
            for (int start = 0; start < order.length; start += options.batchSize()) {
                int end = Math.min(order.length, start + options.batchSize());
                double[] grad = new double[params.length];
                for (int k = start; k < end; k++) {
                    epochLoss += lossAndGradient(trainX[order[k]], trainY[order[k]], grad);
                }
                int n = end - start;
                double norm = 0;
                for (int p = 0; p < grad.length; p++) {
                    grad[p] /= n;
                    norm += grad[p] * grad[p];
                }
                norm = Math.sqrt(norm);
                double clip = norm > options.clipNorm() ? options.clipNorm() / norm : 1;
                step++;
                double b1 = 1 - Math.pow(0.9, step);
                double b2 = 1 - Math.pow(0.999, step);
                for (int p = 0; p < params.length; p++) {
                    double gp = grad[p] * clip + options.weightDecay() * params[p];
                    m[p] = 0.9 * m[p] + 0.1 * gp;
                    v[p] = 0.999 * v[p] + 0.001 * gp * gp;
                    params[p] -= options.learningRate() * (m[p] / b1) / (Math.sqrt(v[p] / b2) + 1e-8);
                }
            }
            trainCurve[epoch] = epochLoss / order.length;
            valCurve[epoch] = meanLoss(valX, valY);
            epochs = epoch + 1;
            if (valCurve[epoch] < bestVal - 1e-6) {
                bestVal = valCurve[epoch];
                bestEpoch = epoch + 1;
                System.arraycopy(params, 0, best, 0, params.length);
                sinceBest = 0;
            } else if (++sinceBest >= options.patience()) {
                break;
            }
        }
        System.arraycopy(best, 0, params, 0, params.length);
        return new TrainingLog(epochs, bestEpoch, Arrays.copyOf(trainCurve, epochs), Arrays.copyOf(valCurve, epochs), bestVal);
    }

    public double meanLoss(double[][][] xs, double[][] ys) {
        if (xs.length == 0) {
            return 0;
        }
        double total = 0;
        for (int k = 0; k < xs.length; k++) {
            total += lossAndGradient(xs[k], ys[k], null);
        }
        return total / xs.length;
    }

    private static void shuffle(int[] order, Random random) {
        for (int k = order.length - 1; k > 0; k--) {
            int j = random.nextInt(k + 1);
            int tmp = order[k];
            order[k] = order[j];
            order[j] = tmp;
        }
    }

    private static double sigmoid(double x) {
        return 1 / (1 + Math.exp(-x));
    }

    public record Options(int maxEpochs, int batchSize, double learningRate, double clipNorm, double weightDecay,
                          int patience, long seed) {
    }

    public record TrainingLog(int epochs, int bestEpoch, double[] trainLoss, double[] validationLoss, double bestValidationLoss) {
    }

    private static final class Trace {
        final double[][] i;
        final double[][] f;
        final double[][] g;
        final double[][] o;
        final double[][] c;
        final double[][] cPrev;
        final double[][] hPrev;
        double[] hLast;
        double[] y;

        Trace(int steps, int hidden) {
            i = new double[steps][hidden];
            f = new double[steps][hidden];
            g = new double[steps][hidden];
            o = new double[steps][hidden];
            c = new double[steps][];
            cPrev = new double[steps][];
            hPrev = new double[steps][];
        }
    }
}
