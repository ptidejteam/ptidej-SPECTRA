package ca.concordia.ptidej.spectra.example;

public class OverloadTest {

	public static int add(int x, int y) {
		long end = System.currentTimeMillis() + 1000;
		while (System.currentTimeMillis() < end) {
			Math.pow(x, 2); // Dummy CPU load
		}
		System.out.println("Executing add(int, int)");
		return x + y;
	}

	public static int add(int x, int y, int z) {
		long end = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < end) {
			Math.log(y + z); // Dummy CPU load
		}
		System.out.println("Executing add(int, int, int)");
		return x + y + z;
	}

	public static double add(double x, double y) {
		long end = System.currentTimeMillis() + 1000;
		while (System.currentTimeMillis() < end) {
			Math.sin(x + y); // Dummy CPU load
		}
		System.out.println("Executing add(double, double)");
		return x + y;
	}

	public static void main(String[] args) throws InterruptedException {
		System.out.println("Starting OverloadedTestApp...");

		add(10, 20);
		add(10.5, 20.5);
		add(1, 2, 3);
		add(50, 60); // Call one again to accumulate more data

		System.out.println("OverloadedTestApp finished.");
	}
}