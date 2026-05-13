package ca.concordia.ptidej.spectra.example;

import org.junit.Test;

public class TestWorkload {
    public static void main(String[] args) throws InterruptedException {
        TestWorkload test = new TestWorkload();
        test.workloadTest();
    }

    @Test
    public void workloadTest() throws InterruptedException {
        // Loop calling methodA and methodB multiple times
        double res = 0.0;
        for (int i = 0; i < 2500; i++) {
//            double a = methodA(10_000); // numeric accumulation work
//            double b = methodB(150); // small matrix-like work
            res += methodC(i);
//            if (i % 500 == 0) {
//                System.out.println("Iteration=" + i + " A=" + String.format("%.6f", a) + " B=" + String.format("%.6f", b));
//            }
        }
        System.out.println("Final result: " + res);
    }

    // Method A: repeated floating-point computations
    public static double methodA(int iterations) {
        double acc = 0.0;
        for (int i = 1; i <= iterations; i++) {
            double x = i;
            // combine sqrt, sin, cos and log for varied CPU math
            acc += Math.sqrt(x) * Math.sin(x) - Math.cos(x) * Math.log(x + 1);
            // small extra mix to avoid trivial optimization
            acc += Math.pow(Math.sin(x / (i % 7 + 1.0)), 2);
        }
        return acc;
    }

    // Method B: deterministic "matrix" diagonal trace calculation
    // computes trace = sum_i sum_k A[i][k] * B[k][i] without storing full result
    // matrix
    public static double methodB(int size) {
        // build two small deterministic matrices in memory-efficient form
        double[][] A = new double[size][size];
        double[][] B = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // deterministic values that vary with indices
                A[i][j] = (i + 1.0) / (j + 2.0) + Math.sin(i * 0.1 + j * 0.03);
                B[i][j] = (j + 1.0) / (i + 2.0) + Math.cos(j * 0.07 + i * 0.02);
            }
        }

        // compute only diagonal elements of the product (trace) to reduce work/storage
        double trace = 0.0;
        for (int i = 0; i < size; i++) {
            double diag = 0.0;
            for (int k = 0; k < size; k++) {
                diag += A[i][k] * B[k][i];
            }
            trace += diag;
        }
        return trace;
    }

    public static double methodC(int num) {

        return Math.sin(num) * Math.sqrt(num);
    }
}
