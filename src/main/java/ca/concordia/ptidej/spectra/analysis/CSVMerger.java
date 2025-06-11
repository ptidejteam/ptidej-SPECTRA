////
////public class CSVMerger {
////    public static void main(String[] args) throws Exception {
////        String xmlFilePath = "Jprofiler/calltree.csv.xml";
////        String allObjectsCsvPath = "Jprofiler/allobjects.csv";
////        String energyCsvPath = "joularjx-result/48797-1743146886347/all/total/methods/joularJX-48797-all-methods-energy.csv";
////        String outputFilePath = "Output/unified_profiling_data.xlsx";
////
////        // Parse XML and extract method data
////        Map<String, MethodData> methodDataMap = parseXMLData(xmlFilePath);
////
////        // Merge with AllObjects data
////        mergeAllObjectsData(methodDataMap, allObjectsCsvPath);
////
////        // Merge with Energy data
////        mergeEnergyData(methodDataMap, energyCsvPath);
////
////        // Write to Excel
////        writeToExcel(methodDataMap, outputFilePath);
////
////        System.out.println("Data successfully merged and written to Excel: " + outputFilePath);
////    }
////
////    private static class MethodData {
////        String methodName;
////        String methodSignature; // Store the full method signature
////        long invocations = 0;
////        long executionTime = 0; // Total execution time
////        long instanceCount = 0;
////        long sizeBytes = 0;
////        double energyConsumption = 0.0;
////
////        public MethodData(String methodName, String methodSignature) {
////            this.methodName = methodName;
////            this.methodSignature = methodSignature;
////        }
////
////        public void combineAllObjectsData(long instanceCount, long sizeBytes) {
////            this.instanceCount = instanceCount;
////            this.sizeBytes = sizeBytes;
////        }
////
////        public void combineEnergyData(double energyConsumption) {
////            this.energyConsumption = energyConsumption;
////        }
////
////        public void combineXMLData(long invocations, long executionTime) {
////            this.invocations = invocations;
////            this.executionTime = executionTime;
////        }
////    }
////
////    public static Map<String, MethodData> parseXMLData(String xmlFilePath) throws Exception {
////        Map<String, MethodData> methodDataMap = new HashMap<>();
////
////        File xmlFile = new File(xmlFilePath);
////        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
////        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
////        Document doc = dBuilder.parse(xmlFile);
////        doc.getDocumentElement().normalize();
////
////        NodeList nodeList = doc.getElementsByTagName("node");
////
////        for (int i = 0; i < nodeList.getLength(); i++) {
////            Node node = nodeList.item(i);
////            if (node.getNodeType() == Node.ELEMENT_NODE) {
////                Element element = (Element) node;
////                String className = element.getAttribute("class");
////                String methodName = element.getAttribute("methodName");
////                String methodSignature = className + "." + methodName; // Combine class and method name
////
////                long time = Long.parseLong(element.getAttribute("time"));
////                long count = Long.parseLong(element.getAttribute("count"));
////
////                MethodData methodData = methodDataMap.computeIfAbsent(methodSignature, k -> new MethodData(methodName, methodSignature));
////                methodData.combineXMLData(count, time);
////            }
////        }
////        return methodDataMap;
////    }
////
////
////    public static void mergeAllObjectsData(Map<String, MethodData> methodDataMap, String allObjectsCsvPath) throws IOException {
////        try (Reader reader = new FileReader(allObjectsCsvPath);
////             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {
////
////            for (CSVRecord csvRecord : csvParser) {
////                String fullMethodName = csvRecord.get("Name");
////                String className = extractClassName(fullMethodName);
////
////                long instanceCount = Long.parseLong(csvRecord.get("Instance Count"));
////                long sizeBytes = Long.parseLong(csvRecord.get("Size (bytes)"));
////
////                methodDataMap.values().stream()
////                        .filter(md -> md.methodSignature.startsWith(className + "."))
////                        .forEach(md -> md.combineAllObjectsData(instanceCount, sizeBytes));
////            }
////        } catch (Exception e) {
////            System.err.println("Error merging AllObjects data: " + e.getMessage());
////        }
////    }
////
////
////    public static void mergeEnergyData(Map<String, MethodData> methodDataMap, String energyCsvPath) throws IOException {
////        try (Reader reader = new FileReader(energyCsvPath);
////             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
////                     .withHeader("method_name", "energy(Joules)")
////                     .withIgnoreSurroundingSpaces()
////                     .withIgnoreEmptyLines()
////                     .withTrim()
////                     .withSkipHeaderRecord(true))) {
////
////            for (CSVRecord csvRecord : csvParser) {
////                String fullMethodName = csvRecord.get("method_name");
////                double energyConsumption = Double.parseDouble(csvRecord.get("energy(Joules)"));
////
////                // Attempt to find exact matching MethodData by method name/signature
////                methodDataMap.entrySet().stream()
////                        .filter(entry -> {
////                            String signature = entry.getKey();
////
////                            return fullMethodName.equals(signature); // Strict comparison
////                        })
////                        .forEach(entry -> {
////                            System.out.println("Match found for method: " + entry.getKey());
////                            entry.getValue().combineEnergyData(energyConsumption);
////                        });
////            }
////        } catch (Exception e) {
////            System.err.println("Error merging Energy data: " + e.getMessage());
////        }
////    }
////
////
////    private static String extractClassName(String fullMethodName) {
////        String methodName = fullMethodName.split("\\(")[0].split("\\$")[0].trim();
////        int lastDotIndex = methodName.lastIndexOf('.');
////        if (lastDotIndex > 0) {
////            return methodName.substring(0, lastDotIndex);
////        } else {
////            return methodName;
////        }
////    }
////
////    private static void writeToExcel(Map<String, MethodData> methodDataMap, String filePath) throws IOException {
////        try (Workbook workbook = new XSSFWorkbook()) {
////            Sheet sheet = workbook.createSheet("Unified Data");
////
////            Row headerRow = sheet.createRow(0);
////            String[] headers = {"Method Name", "Method Signature", "Invocations", "Execution Time", "Instance Count", "Size (bytes)", "Energy (J)"};
////            createHeaderRow(headerRow, headers, workbook);
////
////            int rowNum = 1;
////            for (MethodData methodData : methodDataMap.values()) {
////                Row row = sheet.createRow(rowNum++);
////                row.createCell(0).setCellValue(methodData.methodName);
////                row.createCell(1).setCellValue(methodData.methodSignature);
////                row.createCell(2).setCellValue(methodData.invocations);
////                row.createCell(3).setCellValue(methodData.executionTime);
////                row.createCell(4).setCellValue(methodData.instanceCount);
////                row.createCell(5).setCellValue(methodData.sizeBytes);
////                row.createCell(6).setCellValue(methodData.energyConsumption);
////            }
////
////            autoSizeColumns(sheet, headers.length);
////
////            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
////                workbook.write(fileOut);
////            }
////        } catch (IOException e) {
////            System.err.println("Error writing to Excel: " + e.getMessage());
////        }
////    }
////
////    private static void createHeaderRow(Row headerRow, String[] headers, Workbook workbook) {
////        CellStyle headerStyle = workbook.createCellStyle();
////        Font font = workbook.createFont();
////        font.setBold(true);
////        headerStyle.setFont(font);
////
////        for (int i = 0; i < headers.length; i++) {
////            Cell cell = headerRow.createCell(i);
////            cell.setCellValue(headers[i]);
////            cell.setCellStyle(headerStyle);
////        }
////    }
////
////    private static void autoSizeColumns(Sheet sheet, int numColumns) {
////        for (int i = 0; i < numColumns; i++) {
////            sheet.autoSizeColumn(i);
////        }
////    }
////}
//
package ca.concordia.ptidej.spectra.analysis;

import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;


public class CSVMerger {
    public static void main(String[] args) throws Exception {
        String xmlFilePath = "Jprofiler/calltree.csv.xml";
        String allObjectsCsvPath = "Jprofiler/allobjects.csv";
        String energyCsvPath = "joularjx-result/83572-1749145423233/all/total/methods/joularJX-83572-all-methods-energy.csv";

        String xmlEnergyOutputPath = "Output/unified_profiling_data.xlsx";
        String allObjectsEnergyOutputPath = "Output/allobjects_energy_data.xlsx";

        // Parse XML and extract method data
        Map<String, MethodData> methodDataMap = parseXMLData(xmlFilePath);

        // Merge with Energy data and output XML-Energy
        Map<String, MethodData> xmlEnergyData = mergeAndFilterEnergyData(methodDataMap, energyCsvPath, true);
        writeToExcel(xmlEnergyData, xmlEnergyOutputPath);

        // Merge with AllObjects data and output AllObjects-Energy
        Map<String, MethodData> allObjectsEnergyData = mergeAndFilterAllObjectsEnergyData(allObjectsCsvPath, energyCsvPath);
        writeToExcel(allObjectsEnergyData, allObjectsEnergyOutputPath);

        System.out.println("Data successfully merged and written to Excel files.");
    }

    private static class MethodData {
        String methodSignature;
        long invocations = 0;
        long executionTime = 0;
        long instanceCount = 0;
        long sizeBytes = 0;
        double energyConsumption = 0.0;

        public MethodData(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        public void combineAllObjectsData(long instanceCount, long sizeBytes) {
            this.instanceCount = instanceCount;
            this.sizeBytes = sizeBytes;
        }

        public void combineEnergyData(double energyConsumption) {
            this.energyConsumption = energyConsumption;
        }

        public void combineXMLData(long invocations, long executionTime) {
            this.invocations = invocations;
            this.executionTime = executionTime;
        }
    }

    public static Map<String, MethodData> parseXMLData(String xmlFilePath) throws Exception {
        Map<String, MethodData> methodDataMap = new HashMap<>();

        File xmlFile = new File(xmlFilePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList nodeList = doc.getElementsByTagName("node");

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String className = element.getAttribute("class");
                String methodName = element.getAttribute("methodName");
                String methodSignature = className + "." + methodName;

                long time = Long.parseLong(element.getAttribute("time"));
                long count = Long.parseLong(element.getAttribute("count"));

                MethodData methodData = methodDataMap.computeIfAbsent(methodSignature, MethodData::new);
                methodData.combineXMLData(count, time);
            }
        }
        return methodDataMap;
    }

    public static Map<String, MethodData> mergeAndFilterEnergyData(Map<String, MethodData> methodDataMap, String energyCsvPath, boolean isXML) throws IOException {
        Map<String, MethodData> mergedData = new HashMap<>();

        try (Reader reader = new FileReader(energyCsvPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withHeader("method_name", "energy(Joules)")
                     .withIgnoreSurroundingSpaces()
                     .withIgnoreEmptyLines()
                     .withTrim()
                     .withSkipHeaderRecord(true))) {

            for (CSVRecord csvRecord : csvParser) {
                String fullMethodName = csvRecord.get("method_name");
                double energyConsumption = Double.parseDouble(csvRecord.get("energy(Joules)"));

                if (isXML) {
                    methodDataMap.forEach((signature, methodData) -> {
                        if (fullMethodName.equals(signature)) {
                            MethodData newData = mergedData.computeIfAbsent(signature, MethodData::new);
                            newData.combineXMLData(methodData.invocations, methodData.executionTime);
                            newData.combineEnergyData(energyConsumption);
                        }
                    });
                }
            }
        }
        return mergedData;
    }

    public static Map<String, MethodData> mergeAndFilterAllObjectsEnergyData(String allObjectsCsvPath, String energyCsvPath) throws IOException {
        Map<String, MethodData> mergedData = new HashMap<>();

        // 1. Read AllObjects CSV
        Map<String, Map<String, Long>> allObjectsData = new HashMap<>();
        try (Reader reader = new FileReader(allObjectsCsvPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {

            for (CSVRecord csvRecord : csvParser) {
                String fullMethodName = csvRecord.get("Name");
                String className = extractClassName(fullMethodName);

                long instanceCount = Long.parseLong(csvRecord.get("Instance Count"));
                long sizeBytes = Long.parseLong(csvRecord.get("Size (bytes)"));

                Map<String, Long> classData = allObjectsData.computeIfAbsent(className, k -> new HashMap<>());
                classData.put("instanceCount", instanceCount);
                classData.put("sizeBytes", sizeBytes);
            }
        }

        // 2. Sum energy per class from JoularJX CSV
        Map<String, Double> energyByClass = new HashMap<>();
        try (Reader reader = new FileReader(energyCsvPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withHeader("method_name", "energy(Joules)")
                     .withIgnoreSurroundingSpaces()
                     .withIgnoreEmptyLines()
                     .withTrim()
                     .withSkipHeaderRecord(true))) {

            for (CSVRecord csvRecord : csvParser) {
                String fullMethodName = csvRecord.get("method_name");
                String className = extractClassName(fullMethodName);
                double energy = Double.parseDouble(csvRecord.get("energy(Joules)"));
                energyByClass.merge(className, energy, Double::sum);
            }
        }

        // 3. Merge based on class
        for (Map.Entry<String, Map<String, Long>> entry : allObjectsData.entrySet()) {
            String className = entry.getKey();
            Map<String, Long> values = entry.getValue();
            double energy = energyByClass.getOrDefault(className, 0.0);

            MethodData data = new MethodData(className);
            data.combineAllObjectsData(values.getOrDefault("instanceCount", 0L), values.getOrDefault("sizeBytes", 0L));
            data.combineEnergyData(energy);
            mergedData.put(className, data);
        }

        return mergedData;
    }

    private static String extractClassName(String fullMethodName) {
        String methodName = fullMethodName.split("\\(")[0].split("\\$")[0].trim();
        int lastDotIndex = methodName.lastIndexOf('.');
        return (lastDotIndex > 0) ? methodName.substring(0, lastDotIndex) : methodName;
    }

    private static void writeToExcel(Map<String, MethodData> methodDataMap, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Method Signature", "Invocations", "Execution Time", "Instance Count", "Size (bytes)", "Energy (J)"};
            createHeaderRow(headerRow, headers, workbook);

            int rowNum = 1;
            for (MethodData methodData : methodDataMap.values()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(methodData.methodSignature);
                row.createCell(1).setCellValue(methodData.invocations);
                row.createCell(2).setCellValue(methodData.executionTime);
                row.createCell(3).setCellValue(methodData.instanceCount);
                row.createCell(4).setCellValue(methodData.sizeBytes);
                row.createCell(5).setCellValue(methodData.energyConsumption);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    private static void createHeaderRow(Row headerRow, String[] headers, Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
}
