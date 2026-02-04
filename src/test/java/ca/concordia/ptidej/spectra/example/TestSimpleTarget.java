package ca.concordia.ptidej.spectra.example;

import org.junit.Test;

public class TestSimpleTarget {
    int size = 10000;
    int[][] counter = new int[size][size];
    @Test
    public void simpleTestTarget() {

        System.out.println("This is a simple test");


        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                increment_value(i,j);
            }
        }

        System.out.println("The simple Test is done");

    }

    public void increment_value(int i, int j) {
        counter[i][j] += 1;
    }
}
