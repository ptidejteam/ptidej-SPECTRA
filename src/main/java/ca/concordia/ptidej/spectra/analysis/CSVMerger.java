package ca.concordia.ptidej.spectra.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public static boolean runCSVMerger(String fileName) {
        final String xmlFilePath = Constants.XML_FILE_PATH;
        final String allObjectsCsvPath = Constants.ALL_OBJECTS_CSV_PATH;
        final String hotspotsCsvPath = Constants.HOTSPOTS_CSV_PATH;
        final String energyCsvPath = Constants.ENERGY_CSV_PATH;
        final String xmlEnergyIntersectionOutputPath = Constants
                .getXmlEnergyOutputPath(fileName, "intersection");
        final String xmlEnergyUnionOutputPath = Constants
                .getXmlEnergyOutputPath(fileName, "union");
//        final String allObjectsEnergyOutputPath = Constants
//                .getAllObjectsEnergyOutputPath(fileName);
//        final String hotSpotEnergyOutputPath = Constants
//                .getHotSpotEnergyOutputPath(fileName);

        try {
            // Parse XML and extract method data
            final Map<String, MethodData> methodDataMap = parseXMLData(xmlFilePath);

            // Merge with Energy data and output XML-Energy "intersection"
            final Map<String, MethodData> xmlEnergyIntersectionData = mergeAndFilterEnergyData(
                    methodDataMap, energyCsvPath, true);
            writeToExcel(xmlEnergyIntersectionData, xmlEnergyIntersectionOutputPath);

            // Merge with Energy data and output XML-Energy "union"
            final Map<String, MethodData> xmlEnergyUnionData = mergeUnionEnergyData(
                    methodDataMap, energyCsvPath);
            writeToExcel(xmlEnergyUnionData, xmlEnergyUnionOutputPath);


            // Merge with AllObjects data and output AllObjects-Energy
//            final Map<String, MethodData> allObjectsEnergyData =
//                    mergeAndFilterAllObjectsEnergyData(
//                            allObjectsCsvPath, energyCsvPath);
//            writeToExcel(allObjectsEnergyData, allObjectsEnergyOutputPath);
//
//            // Merge hotspots.csv with energyCsvPath and output to Excel ======
//            final Map<String, MethodData> hotspotsData = parseHotspotsCsv(
//                    hotspotsCsvPath);
//            final Map<String, MethodData> mergedHotspotsEnergy = mergeHotspotsWithEnergy(
//                    hotspotsData, energyCsvPath);
//            writeToExcel(mergedHotspotsEnergy, hotSpotEnergyOutputPath);
        } catch (Exception e) {
            System.err.println(
                    "Error during CSVMerger execution: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println(
                "Data successfully merged and written to Excel files.");
        return true;
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

    public static Map<String, MethodData> parseXMLData(String xmlFilePath)
            throws Exception {
        Map<String, MethodData> methodDataMap = new HashMap<>();

        final File xmlFile = new File(xmlFilePath);
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
                String methodSignature = className + "." + methodName
                        + convertJVMDescriptor(jvmSignature);

                if (methodSignature.contains("$$Lambda")) {
                    methodSignature.replace("\\$\\$Lambda.*", ".lambda");
                }

                methodSignature = normalizeSignature(methodSignature);


                long time = Long.parseLong(element.getAttribute("selfTime"));
                long count = Long.parseLong(element.getAttribute("count"));


                MethodData methodData = methodDataMap
                        .computeIfAbsent(methodSignature, MethodData::new);
                methodData.combineXMLData(count, time);
            }
        }

        return methodDataMap;
    }

    private static String convertJVMDescriptor(String jvmSignature) {
        final int start = jvmSignature.indexOf('(');
        int end = jvmSignature.indexOf(')');
        if (start < 0 || end < 0)
            return "()";

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
                case 'O':
                    result.append("object");
                    i++;
                    break;
                case 'L':
                    int semicolon = params.indexOf(';', i);
                    String className = params.substring(i + 1, semicolon)
                            .replace('/', '.');
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
                    String arrayType = convertJVMDescriptor(
                            params.substring(i, i + 1));
                    result.append(arrayType);
                    for (int d = 0; d < arrayDepth; d++)
                        result.append("[]");
                    i++;
                    break;
                default:
                    i++; // skip unknown
            }
            if (i < params.length())
                result.append(", ");
        }

        // Remove trailing comma
        if (result.length() > 1 && result.charAt(result.length() - 2) == ',') {
            result.setLength(result.length() - 2);
        }
        result.append(")");
        return result.toString();
    }

    public static Map<String, MethodData> mergeAndFilterEnergyData(
            Map<String, MethodData> methodDataMap, String energyCsvPath,
            boolean isXML) throws IOException {

        Map<String, MethodData> mergedData = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0)
                    continue;

                String fullMethodName = line.substring(0, lastCommaIndex)
                        .trim();
                String energyStr = line.substring(lastCommaIndex + 1).trim();

                double energyConsumption;
                try {
                    energyConsumption = Double.parseDouble(energyStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                fullMethodName = normalizeSignature(fullMethodName);


                // Match using human-readable signature
                String finalFullMethodName = fullMethodName;
                methodDataMap.forEach((signature, methodData) -> {
                    if (finalFullMethodName.equals(signature)) {
                        MethodData newData = mergedData
                                .computeIfAbsent(signature, MethodData::new);
                        newData.combineXMLData(methodData.invocations,
                                methodData.executionTime);
                        newData.combineEnergyData(energyConsumption);
                    }
                });
            }
        }

        return mergedData;
    }
    public static Map<String, MethodData> mergeUnionEnergyData(
            Map<String, MethodData> profilerData, String energyCsvPath) throws IOException {

        Map<String, MethodData> merged = new HashMap<>();
        Map<String, Double> energyMap = new HashMap<>();

        // Step 1: Parse JoularJX CSV into energyMap
        try (BufferedReader br = new BufferedReader(new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int lastComma = line.lastIndexOf(',');
                if (lastComma < 0) continue;

                String method = normalizeSignature(line.substring(0, lastComma).trim());
                String energyStr = line.substring(lastComma + 1).trim();

                try {
                    double energy = Double.parseDouble(energyStr);
                    energyMap.put(method, energy);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Step 2: Create union of keys (from profiler + JoularJX)
        for (String method : profilerData.keySet()) {
            merged.put(method, new MethodData(method));
        }
        for (String method : energyMap.keySet()) {
            merged.putIfAbsent(method, new MethodData(method));
        }

        // Step 3: Fill in data
        for (String method : merged.keySet()) {
            MethodData unionEntry = merged.get(method);

            MethodData profilerEntry = profilerData.get(method);
            if (profilerEntry != null) {
                unionEntry.combineXMLData(profilerEntry.invocations, profilerEntry.executionTime);
            } else {
                unionEntry.invocations = -1;
                unionEntry.executionTime = -1;
            }

            Double energy = energyMap.get(method);
            if (energy != null) {
                unionEntry.combineEnergyData(energy);
            } else {
                unionEntry.energyConsumption = -1;
            }
        }

        return merged;
    }


    public static Map<String, MethodData> mergeAndFilterAllObjectsEnergyData(
            String allObjectsCsvPath, String energyCsvPath) throws IOException {

        Map<String, MethodData> mergedData = new HashMap<>();

        // 1. Read AllObjects CSV
        Map<String, Map<String, Long>> allObjectsData = new HashMap<>();
        try (Reader reader = new FileReader(allObjectsCsvPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader().withTrim())) {

            for (CSVRecord csvRecord : csvParser) {
                String fullMethodName = csvRecord.get("Name");
                String className = extractClassName(fullMethodName);

                long instanceCount = Long
                        .parseLong(csvRecord.get("Instance Count"));
                long sizeBytes = Long.parseLong(csvRecord.get("Size (bytes)"));

                Map<String, Long> classData = allObjectsData
                        .computeIfAbsent(className, k -> new HashMap<>());
                classData.put("instanceCount", instanceCount);
                classData.put("sizeBytes", sizeBytes);
            }
        }

        // 2. Sum energy per class from JoularJX CSV (manual parsing)
        Map<String, Double> energyByClass = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0)
                    continue; // skip invalid

                String fullMethodName = line.substring(0, lastCommaIndex)
                        .trim();
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
        for (Map.Entry<String, Map<String, Long>> entry : allObjectsData
                .entrySet()) {

            String className = entry.getKey();
            if (!energyByClass.containsKey(className)) {
                continue;
            }

            Map<String, Long> values = entry.getValue();
            double energy = energyByClass.getOrDefault(className, 0.0);

            MethodData data = new MethodData(className);
            data.combineAllObjectsData(values.getOrDefault("instanceCount", 0L),
                    values.getOrDefault("sizeBytes", 0L));
            data.combineEnergyData(energy);
            mergedData.put(className, data);
        }

        return mergedData;
    }

    private static Map<String, MethodData> parseHotspotsCsv(
            String hotspotsCsvPath) throws IOException {
        Map<String, MethodData> hotspotsMap = new HashMap<>();

        try (Reader reader = new FileReader(hotspotsCsvPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader().withTrim())) {

            for (CSVRecord record : csvParser) {
                String methodSignature = record.get("Hot Spot").trim();
                String selfTimeStr = record.get("Self Time (microseconds)")
                        .trim();
                String invocationsCount = record.get("Invocations").trim();
                long selfTime = 0;
                try {
                    selfTime = Long.parseLong(selfTimeStr);
                } catch (NumberFormatException e) {
                    // skip or set default
                }

                MethodData methodData = new MethodData(methodSignature);
                methodData.executionTime = selfTime; // Using executionTime field to store self time here
                methodData.invocations = invocationsCount.equals("n/a") ? 0
                        : Long.parseLong(invocationsCount);
                hotspotsMap.put(methodSignature, methodData);
            }
        }

        return hotspotsMap;
    }

    //  Merge hotspots data with energy data from joularJX CSV
    private static Map<String, MethodData> mergeHotspotsWithEnergy(
            Map<String, MethodData> hotspotsData, String energyCsvPath)
            throws IOException {

        Map<String, MethodData> merged = new HashMap<>();
        Map<String, Double> energyMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new FileReader(energyCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex < 0)
                    continue;

                String method = line.substring(0, lastCommaIndex).trim();
                String energyStr = line.substring(lastCommaIndex + 1).trim();

                try {
                    double energy = Double.parseDouble(energyStr);
                    method = normalizeSignature(method);
                    energyMap.put(method, energy);
                } catch (NumberFormatException e) {
                }
            }
        }

        for (Map.Entry<String, MethodData> entry : hotspotsData.entrySet()) {
            String method = normalizeSignature(entry.getKey());

            MethodData hotspotData = entry.getValue();

            if (energyMap.containsKey(method)) {
                MethodData combined = new MethodData(method);
                combined.executionTime = hotspotData.executionTime; // self time
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
        return (lastDotIndex > 0) ? methodName.substring(0, lastDotIndex + 1)
                + methodName.substring(lastDotIndex + 1)
                : methodName;
    }


    private static void writeToExcel(Map<String, MethodData> methodDataMap,
                                     String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            Row headerRow = sheet.createRow(0);
            boolean isAllObjects = filePath.contains("allObjects");

            String[] headers = isAllObjects
                    ? new String[]{"Class Name", "Energy (J)",
                    "Instance Count", "Size " + "(bytes)"}
                    : new String[]{"Method Signature", "Invocations",
                    "Execution Time", "Energy (J)"};

            createHeaderRow(headerRow, headers, workbook);
            int rowNum = 1;
            for (MethodData methodData : methodDataMap.values()) {
                if (isAllObjects) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(methodData.methodSignature);
                    row.createCell(1)
                            .setCellValue(methodData.energyConsumption);
                    row.createCell(2).setCellValue(methodData.instanceCount);
                    row.createCell(3).setCellValue(methodData.sizeBytes);
                } else {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(methodData.methodSignature);
                    row.createCell(1).setCellValue(methodData.invocations ==-1 ? "-" : String.valueOf(methodData.invocations));
                    row.createCell(2).setCellValue(methodData.executionTime ==-1 ? "-" : String.valueOf(methodData.executionTime));
                    row.createCell(3).setCellValue(methodData.energyConsumption ==-1 ? "-" : String.valueOf(methodData.energyConsumption));

                }

            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    private static void createHeaderRow(Row headerRow, String[] headers,
                                        Workbook workbook) {
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

    public final class Constants {
        public static final String PROJECT_ROOT = "/Users/mac/Documents/RA/SPECTRA";
        public static final String XML_FILE_PATH =  PROJECT_ROOT + "/Output/JProfiler/calltree.csv.xml";
        public static final String ALL_OBJECTS_CSV_PATH = PROJECT_ROOT + "/Output/Jprofiler/allobjects.csv";
        public static final String HOTSPOTS_CSV_PATH = PROJECT_ROOT + "/Output/Jprofiler/hotspots.csv";
        public static final String ENERGY_CSV_PATH = PROJECT_ROOT + "/Output/Joularjx/data/joularJX-123-all-methods-energy.csv";

        private Constants() {
        }

        public static String getXmlEnergyOutputPath(String fileName, String type) {
            return String.format(PROJECT_ROOT + "/Results/%s.%s.%s.spectra.xlsx", fileName, type, timestamp);

        }

        public static String getAllObjectsEnergyOutputPath(String fileName, String type ) {
            return String.format(PROJECT_ROOT + "/Results/%s.%s.%s.spectra.xlsx", fileName, type, timestamp);
        }

        public static String getHotSpotEnergyOutputPath(String fileName, String type) {
            return String.format(PROJECT_ROOT + "/Results/%s.%s.%s.spectra.xlsx", fileName, type, timestamp);
        }
        static String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                "yyMMdd'H'HHmm"));
    }

    private static String normalizeSignature(String raw) {
        if (raw == null) return null;

        // Remove module/version prefixes (java.base@21.0.2/)
        raw = raw.replaceAll("[a-zA-Z0-9_.-]+@[0-9.]+/", "");

        // Normalize $$Lambda classes
        raw = raw.replaceAll("\\$\\$Lambda.*", ".lambda");

        // Normalize <init> constructors
        raw = raw.replace("<init>", "constructor");

        // Normalize arrays
        raw = raw.replaceAll("\\[\\s*\\]", "[]");

        // Remove extra spaces
        raw = raw.replaceAll(",\\s+", ",");

        return raw.trim();
    }
}
