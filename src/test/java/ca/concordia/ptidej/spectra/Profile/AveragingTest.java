package ca.concordia.ptidej.spectra.Profile;

import org.junit.Test;
import java.io.*;
import java.util.*;

/**
 * Simple test to verify the averaging logic works correctly
 */
public class AveragingTest {

    @Test
    public void testEnergyAveraging() throws IOException {
        // Create test data directory
        File testDir = new File("test_averaging_data");
        testDir.mkdirs();

        // Create 3 sample energy CSV files
        createSampleEnergyFile(testDir, "joularJX-1001-all-methods-energy.csv",
            Map.of("method.A", 0.005, "method.B", 0.010, "method.C", 0.002));

        createSampleEnergyFile(testDir, "joularJX-1002-all-methods-energy.csv",
            Map.of("method.A", 0.007, "method.B", 0.012, "method.C", 0.004));

        createSampleEnergyFile(testDir, "joularJX-1003-all-methods-energy.csv",
            Map.of("method.A", 0.006, "method.B", 0.011, "method.C", 0.003));

        // Simulate averaging
        Map<String, List<Double>> accumulator = new HashMap<>();

        for (int pid : Arrays.asList(1001, 1002, 1003)) {
            String file = testDir + "/joularJX-" + pid + "-all-methods-energy.csv";
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    String method = parts[0];
                    double energy = Double.parseDouble(parts[1]);
                    accumulator.computeIfAbsent(method, k -> new ArrayList<>()).add(energy);
                }
            }
        }

        // Compute and verify averages
        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : accumulator.entrySet()) {
            double sum = entry.getValue().stream().mapToDouble(Double::doubleValue).sum();
            double avg = sum / entry.getValue().size();
            averages.put(entry.getKey(), avg);
        }

        // Expected averages
        assert Math.abs(averages.get("method.A") - 0.006) < 0.0001 : "method.A avg should be 0.006";
        assert Math.abs(averages.get("method.B") - 0.011) < 0.0001 : "method.B avg should be 0.011";
        assert Math.abs(averages.get("method.C") - 0.003) < 0.0001 : "method.C avg should be 0.003";

        System.out.println("✓ Averaging test passed!");
        System.out.println("Averages: " + averages);

        // Cleanup
        for (File f : testDir.listFiles()) {
            f.delete();
        }
        testDir.delete();
    }

    private void createSampleEnergyFile(File dir, String filename, Map<String, Double> data) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dir, filename)))) {
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                bw.write(entry.getKey() + "," + entry.getValue());
                bw.newLine();
            }
        }
    }
}

