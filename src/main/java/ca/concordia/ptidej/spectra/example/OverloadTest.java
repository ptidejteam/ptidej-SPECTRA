package ca.concordia.ptidej.spectra.example;



public class OverloadTest {
	public static int add(int x, int y) {
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end) {
			Math.pow(x, 2); // Dummy CPU load
		}
		System.out.println("Executing add(int, int) in OverloadTest");
		return x + y;
	}

	public static int add(int x, int y, int z) {
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end) {
			Math.log(y + z); // Dummy CPU load
		}
		System.out.println("Executing add(int, int, int) in OverloadTest");
		return x + y + z;
	}

	public double add(double x, double y) {
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end) {
			Math.sin(x + y); // Dummy CPU load
		}
		System.out.println("Executing add(double, double) in OverloadTest");
		return x + y;
	}

	public int add(String x, Double y, int z) {
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end) {
			Math.log(y + z); // Dummy CPU load
		}
		System.out.println("Executing add(String, int, int) in OverloadTest");
		return (int) (y + z);
	}

	public static void main(String[] args) throws InterruptedException {
		System.out.println("Starting OverloadedTestApp...");
		OverloadTest test1 = new OverloadTest();
		OverloadTest test2 = new OverloadTest();


		OverloadTest.add(10, 20);
		test1.add(10.5, 20.5);
		add(1, 2, 3);
		test1.add(10.5, 20.5);

		test2.add("test", 11.2, 10);

		OverloadTest.add(50, 60); // Call one again to accumulate more data

		System.out.println("OverloadedTestApp finished.");
	}
}
