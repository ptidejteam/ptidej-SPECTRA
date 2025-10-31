package ca.concordia.ptidej.spectra.analysis;

import java.io.BufferedWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

public class CsvResultWriter extends org.noureddine.joularjx.result.CsvResultWriter {
    private String outputPath;

    public CsvResultWriter(String outputPath) {
        this.outputPath = outputPath;
    }

    public CsvResultWriter() {

    }

    public void write(List<ResultData> results) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("Method,ExecutionTime,Invocations");
            writer.newLine();
            // Write data
            for (ResultData data : results) {
                writer.write(data.getMethod() + "," + data.getExecutionTime() + "," + data.getInvocations());
                writer.newLine();
            }
        }
    }

    public String getOutputPath() {
        return outputPath;
    }
}

 class ResultData {
    private final String method;
    private final double executionTime;
    private final long invocations;

    public ResultData(String method, double executionTime, long invocations) {
        this.method = method;
        this.executionTime = executionTime;
        this.invocations = invocations;
    }

    public String getMethod() { return method; }
    public double getExecutionTime() { return executionTime; }
    public long getInvocations() { return invocations; }
}
