package ca.concordia.ptidej.spectra.analysis;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CSVMerger {
    public static void runCSVMerger() {
        String xmlFilePath = "Output/JProfiler/calltree.csv.xml";
        String allObjectsCsvPath = "Output/Jprofiler/allobjects.csv";
        String hotspotsCsvPath = "Output/Jprofiler/hotspots.csv";


        String energyCsvPath = "Output/Joularjx/data/all/total/methods/joularJX-123-all-methods-energy.csv";


        String xmlEnergyOutputPath = "Results/callTree_energy_data.xlsx";
        String allObjectsEnergyOutputPath = "Results/allobjects_energy_data.xlsx";
        String hotSpotEnergyOutputPath = "Results/hotspots_energy_data.xlsx";

        try {
            // Parse XML and extract method data
            Map<String, MethodData> methodDataMap = parseXMLData(xmlFilePath);

            // Merge with Energy data and output XML-Energy
            Map<String, MethodData> xmlEnergyData = mergeAndFilterEnergyData(methodDataMap, energyCsvPath, true);
            writeToExcel(xmlEnergyData, xmlEnergyOutputPath);

            // Merge with AllObjects data and output AllObjects-Energy
            Map<String, MethodData> allObjectsEnergyData = mergeAndFilterAllObjectsEnergyData(allObjectsCsvPath, energyCsvPath);
            writeToExcel(allObjectsEnergyData, allObjectsEnergyOutputPath);


            // Merge hotspots.csv with energyCsvPath and output to Excel ======
            Map<String, MethodData> hotspotsData = parseHotspotsCsv(hotspotsCsvPath);
            Map<String, MethodData> mergedHotspotsEnergy = mergeHotspotsWithEnergy(hotspotsData, energyCsvPath);
            writeToExcel(mergedHotspotsEnergy, hotSpotEnergyOutputPath);
        } catch (Exception e) {
            System.err.println("Error during CSVMerger execution: " + e.getMessage());
            e.printStackTrace();
        }

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
                String jvmSignature = element.getAttribute("methodSignature");

                // Convert JVM signature to human-readable
                String methodSignature = className + "." + methodName + convertJVMDescriptor(jvmSignature);

                long time = Long.parseLong(element.getAttribute("selfTime"));
                long count = Long.parseLong(element.getAttribute("count"));

                MethodData methodData = methodDataMap.computeIfAbsent(methodSignature, MethodData::new);
                methodData.combineXMLData(count, time);
            }
        }

        return methodDataMap;
    }

    private static String convertJVMDescriptor(String jvmSignature) {
        int start = jvmSignature.indexOf('(');
        int end = jvmSignature.indexOf(')');
        if (start < 0 || end < 0) return "()";

        String params = jvmSignature.substring(start + 1, end);
        StringBuilder result = new StringBuilder("(");

        int i = 0;
        while (i < params.length()) {
            char c = params.charAt(i);
            switch (c) {
                case 'B':
                    result.append("byte");
                    i++;
                    break;
                case 'C':
                    result.append("char");
                    i++;
                    break;
                case 'D':
                    result.append("double");
                    i++;
                    break;
                case 'F':
                    result.append("float");
                    i++;
                    break;
                case 'I':
                    result.append("int");
                    i++;
                    break;
                case 'J':
                    result.append("long");
                    i++;
                    break;
                case 'S':
                    result.append("short");
                    i++;
                    break;
                case 'Z':
                    result.append("boolean");
                    i++;
                    break;
                case 'L':
                    int semicolon = params.indexOf(';', i);
                    String className = params.substring(i + 1, semicolon).replace('/', '.');
                    result.append(className);
                    i = semicolon + 1;
                    break;
                case '[':
                    // Handle arrays
                    int arrayDepth = 0;
                    while (params.charAt(i) == '[') {
                        arrayDepth++;
                        i++;
                    }
                    String arrayType = convertJVMDescriptor(params.substring(i, i + 1));
                    result.append(arrayType);
                    for (int d = 0; d < arrayDepth; d++) result.append("[]");
                    i++;
                    break;
                default:
                    i++; // skip unknown
            }
            if (i < params.length()) result.append(", ");
        }

        // Remove trailing comma
        if (result.length() > 1 && result.charAt(result.length() - 2) == ',') {
            result.setLength(result.length() - 2);
        }
        result.append(")");
        return result.toString();
    }

    public static Map<String, MethodData> mergeAndFilterEnergyData(Map<String, MethodData> methodDataMap, String energyCsvPath, boolean isXML) throws IOException {

        Map<String, MethodData> mergedData = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0) continue;

                String fullMethodName = line.substring(0, lastCommaIndex).trim();
                String energyStr = line.substring(lastCommaIndex + 1).trim();

                double energyConsumption;
                try {
                    energyConsumption = Double.parseDouble(energyStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                // Match using human-readable signature
                methodDataMap.forEach((signature, methodData) -> {
                    if (fullMethodName.equals(signature)) {
                        MethodData newData = mergedData.computeIfAbsent(signature, MethodData::new);
                        newData.combineXMLData(methodData.invocations, methodData.executionTime);
                        newData.combineEnergyData(energyConsumption);
                    }
                });
            }
        }

        return mergedData;
    }


    public static Map<String, MethodData> mergeAndFilterAllObjectsEnergyData(String allObjectsCsvPath, String energyCsvPath) throws IOException {

        Map<String, MethodData> mergedData = new HashMap<>();

        // 1. Read AllObjects CSV
        Map<String, Map<String, Long>> allObjectsData = new HashMap<>();
        try (Reader reader = new FileReader(allObjectsCsvPath); CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {

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

        // 2. Sum energy per class from JoularJX CSV (manual parsing)
        Map<String, Double> energyByClass = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0) continue; // skip invalid

                String fullMethodName = line.substring(0, lastCommaIndex).trim();
                String energyStr = line.substring(lastCommaIndex + 1).trim();

                double energy;
                try {
                    energy = Double.parseDouble(energyStr);
                } catch (NumberFormatException e) {
                    // Skip malformed energy values
                    continue;
                }

                String className = extractClassName(fullMethodName);
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

    private static Map<String, MethodData> parseHotspotsCsv(String hotspotsCsvPath) throws IOException {
        Map<String, MethodData> hotspotsMap = new HashMap<>();

        try (Reader reader = new FileReader(hotspotsCsvPath); CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {

            for (CSVRecord record : csvParser) {
                String methodSignature = record.get("Hot Spot").trim();
                String selfTimeStr = record.get("Self Time (microseconds)").trim();
                String invocationsCount = record.get("Invocations").trim();
                long selfTime = 0;
                try {
                    selfTime = Long.parseLong(selfTimeStr);
                } catch (NumberFormatException e) {
                    // skip or set default
                }

                MethodData methodData = new MethodData(methodSignature);
                methodData.executionTime = selfTime;  // Using executionTime field to store self time here
                methodData.invocations = Long.parseLong(invocationsCount);
                hotspotsMap.put(methodSignature, methodData);
            }
        }

        return hotspotsMap;
    }

    // ====== NEW METHOD: Merge hotspots data with energy data from joularJX CSV ======
    private static Map<String, MethodData> mergeHotspotsWithEnergy(Map<String, MethodData> hotspotsData, String energyCsvPath) throws IOException {

        Map<String, MethodData> merged = new HashMap<>();
        Map<String, Double> energyMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0) continue;

                String method = line.substring(0, lastCommaIndex).trim();
                String energyStr = line.substring(lastCommaIndex + 1).trim();

                try {
                    double energy = Double.parseDouble(energyStr);
                    energyMap.put(method, energy);
                } catch (NumberFormatException e) {
                }
            }
        }

        for (Map.Entry<String, MethodData> entry : hotspotsData.entrySet()) {
            String method = entry.getKey();
            MethodData hotspotData = entry.getValue();

            if (energyMap.containsKey(method)) {
                MethodData combined = new MethodData(method);
                combined.executionTime = hotspotData.executionTime;  // self time
                combined.energyConsumption = energyMap.get(method);
                combined.invocations = hotspotData.invocations;
                merged.put(method, combined);
            }
        }

        return merged;
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
            boolean isAllObjects = filePath.equals("Results/allobjects_energy_data.xlsx");

            String[] headers = isAllObjects
                    ? new String[] { "Method Signature", "Invocations", "Execution " +
                    "Time",  "Energy (J)",  "Instance Count","Size (bytes)" }
                    : new String[] { "Method Signature", "Invocations", "Execution Time","Energy (J)" };

            createHeaderRow(headerRow, headers, workbook);
            int rowNum = 1;
            for (MethodData methodData : methodDataMap.values()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(methodData.methodSignature);
                row.createCell(1).setCellValue(methodData.invocations);
                row.createCell(2).setCellValue(methodData.executionTime);
                row.createCell(3).setCellValue(methodData.energyConsumption);
                if(isAllObjects) row.createCell(4).setCellValue(methodData.instanceCount);

                if( isAllObjects) row.createCell(5).setCellValue(methodData.sizeBytes);
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
