package ca.concordia.ptidej.spectra.example;
 
import org.junit.Test;
 
/**
 * OPTIMIZED / LOW ENERGY VARIANT
 * 
 * This variant uses efficient algorithms (O(n) time, O(1) space) and
 * avoids unnecessary allocations to minimize energy footprint.
 */
public class TestWorkloadOptimized {
 
    public static void main(String[] args) throws InterruptedException {
        new TestWorkloadOptimized().workloadTest();
    }
 
    @Test
    public void workloadTest() throws InterruptedException {
        System.out.println("[TestWorkloadOptimized] Starting optimized workload...");
        for (int i = 0; i < 10000; i++) {
            // Method A: Iterative approach (O(n) time, O(1) space)
            long fib = methodA(15);
 
            // Method B: Space-efficient approach (Reusing buffers, O(1) space)
            double trace = methodB(500);
 
            System.out.println("Iteration " + i + ": Fib=" + fib + " Trace=" + trace);
        }
    }
 
    // Method A: Iterative Fibonacci - O(n)
    // Extremely efficient compared to recursion.
    public static long methodA(int n) {
        if (n <= 1)
            return n;
        long prev = 0, curr = 1;
        for (int i = 2; i <= n; i++) {
            long temp = curr;
            curr = prev + curr;
            prev = temp;
        }
        return curr;
    }
 
    // Method B: Space Efficient - O(1) space
    // Calculates the result without allocating massive arrays, saving memory and GC energy.
    public static double methodB(int size) {
        double totalTrace = 0;
 
        // Instead of allocating a 2D array, we calculate values on the fly
        // This eliminates matrix allocation and GC overhead.
        for (int k = 0; k < 5; k++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    // Same logic as High variant but without the O(N^2) memory footprint
                    totalTrace += Math.sqrt(i * j + 1) * 0.5; // used a constant instead of random for stability
                }
            }
        }
 
        return totalTrace;
    }
}
