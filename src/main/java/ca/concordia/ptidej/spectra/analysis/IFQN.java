package ca.concordia.ptidej.spectra.analysis;

//import java.util.stream.Collectors;
//
//public class CSVMerger {
//    public static void main(String[] args) throws Exception {
//        String xmlFilePath = "Jprofiler/calltree.csv.xml";
//        String allObjectsCsvPath = "Jprofiler/allobjects.csv";
//        String energyCsvPath = "joularjx-result/48797-1743146886347/all/total/methods/joularJX-48797-all-methods-energy.csv";
//
//        String xmlEnergyOutputPath = "Output/unified_profiling_data.xlsx";
//        String allObjectsEnergyOutputPath = "Output/allobjects_energy_data.xlsx";
//
//        // Parse XML and extract method data
//        Map<String, MethodData> methodDataMap = parseXMLData(xmlFilePath);
//
//        // Merge with Energy data and output XML-Energy
//        Map<String, MethodData> xmlEnergyData = mergeAndFilterEnergyData(methodDataMap, energyCsvPath, true);
//        writeToExcel(xmlEnergyData, xmlEnergyOutputPath);
//
//        // Merge with AllObjects data and output AllObjects-Energy
//        Map<String, MethodData> allObjectsEnergyData = mergeAndFilterAllObjectsEnergyData(allObjectsCsvPath, energyCsvPath);
//        writeToExcel(allObjectsEnergyData, allObjectsEnergyOutputPath);
//
//        System.out.println("Data successfully merged and written to Excel files.");
//    }
//
//    private static class MethodData {
//        String methodSignature; // Store the full method signature
//        long invocations = 0;
//        long executionTime = 0; // Total execution time
//        long instanceCount = 0;
//        long sizeBytes = 0;
//        double energyConsumption = 0.0;
//
//        public MethodData(String methodSignature) {
//            this.methodSignature = methodSignature;
//        }
//
//        public void combineAllObjectsData(long instanceCount, long sizeBytes) {
//            this.instanceCount = instanceCount;
//            this.sizeBytes = sizeBytes;
//        }
//
//        public void combineEnergyData(double energyConsumption) {
//            this.energyConsumption = energyConsumption;
//        }
//
//        public void combineXMLData(long invocations, long executionTime) {
//            this.invocations = invocations;
//            this.executionTime = executionTime;
//        }
//    }
//
//    public static Map<String, MethodData> parseXMLData(String xmlFilePath) throws Exception {
//        Map<String, MethodData> methodDataMap = new HashMap<>();
//
//        File xmlFile = new File(xmlFilePath);
//        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//        Document doc = dBuilder.parse(xmlFile);
//        doc.getDocumentElement().normalize();
//
//        NodeList nodeList = doc.getElementsByTagName("node");
//
//        for (int i = 0; i < nodeList.getLength(); i++) {
//            Node node = nodeList.item(i);
//            if (node.getNodeType() == Node.ELEMENT_NODE) {
//                Element element = (Element) node;
//                String className = element.getAttribute("class");
//                String methodName = element.getAttribute("methodName");
//                String methodSignature = className + "." + methodName;
//
//                long time = Long.parseLong(element.getAttribute("time"));
//                long count = Long.parseLong(element.getAttribute("count"));
//
//                MethodData methodData = methodDataMap.computeIfAbsent(methodSignature, MethodData::new);
//                methodData.combineXMLData(count, time);
//            }
//        }
//        return methodDataMap;
//    }
//
//    public static Map<String, MethodData> mergeAndFilterEnergyData(Map<String, MethodData> methodDataMap, String energyCsvPath, boolean isXML) throws IOException {
//        Map<String, MethodData> mergedData = new HashMap<>();
//        // Read energy.csv
//        try (Reader reader = new FileReader(energyCsvPath);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
//                     .withHeader("method_name", "energy(Joules)")
//                     .withIgnoreSurroundingSpaces()
//                     .withIgnoreEmptyLines()
//                     .withTrim()
//                     .withSkipHeaderRecord(true))) {
//
//            for (CSVRecord csvRecord : csvParser) {
//                String fullMethodName = csvRecord.get("method_name");
//                double energyConsumption = Double.parseDouble(csvRecord.get("energy(Joules)"));
//
//                if(isXML){
//                    // Try to find matching MethodData by methodSignature
//                    methodDataMap.forEach((signature, methodData) -> {
//                        if (fullMethodName.equals(signature)) {
//                            MethodData newData = mergedData.computeIfAbsent(signature, MethodData::new);
//                            newData.methodSignature = signature;
//                            newData.combineXMLData(methodData.invocations, methodData.executionTime);
//                            newData.combineEnergyData(energyConsumption);
//                        }
//                    });
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Error merging Energy data: " + e.getMessage());
//        }
//        return mergedData;
//    }
//
//    public static Map<String, MethodData> mergeAndFilterAllObjectsEnergyData(String allObjectsCsvPath, String energyCsvPath) throws IOException {
//        Map<String, MethodData> mergedData = new HashMap<>();
//
//        // Read AllObjects.csv
//        Map<String, Map<String, Long>> allObjectsData = new HashMap<>();
//        try (Reader reader = new FileReader(allObjectsCsvPath);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {
//
//            for (CSVRecord csvRecord : csvParser) {
//                String fullMethodName = csvRecord.get("Name");
//                String className = extractClassName(fullMethodName);
//
//                long instanceCount = Long.parseLong(csvRecord.get("Instance Count"));
//                long sizeBytes = Long.parseLong(csvRecord.get("Size (bytes)"));
//
//                allObjectsData.computeIfAbsent(className, k -> new HashMap<>())
//                        .put("instanceCount", instanceCount);
//                allObjectsData.get(className).put("sizeBytes", sizeBytes);
//            }
//        } catch (Exception e) {
//            System.err.println("Error reading AllObjects data: " + e.getMessage());
//            return mergedData; // Exit if AllObjects data cannot be read
//        }
//
//        // Read energy.csv
//        try (Reader reader = new FileReader(energyCsvPath);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
//                     .withHeader("method_name", "energy(Joules)")
//                     .withIgnoreSurroundingSpaces()
//                     .withIgnoreEmptyLines()
//                     .withTrim()
//                     .withSkipHeaderRecord(true))) {
//
//            for (CSVRecord csvRecord : csvParser) {
//                String fullMethodName = csvRecord.get("method_name");
//                String className = extractClassName(fullMethodName);
//                double energyConsumption = Double.parseDouble(csvRecord.get("energy(Joules)"));
//
//                allObjectsData.forEach((objClassName, values) -> {
//                    // Try to match className exactly
//                    if (objClassName.equals(className)) {
//                        MethodData newData = mergedData.computeIfAbsent(objClassName, MethodData::new);
//                        newData.methodSignature = objClassName;
//                        newData.instanceCount = values.getOrDefault("instanceCount", 0L);
//                        newData.sizeBytes = values.getOrDefault("sizeBytes", 0L);
//                        newData.combineEnergyData(energyConsumption);
//                    }
//                });
//            }
//        } catch (Exception e) {
//            System.err.println("Error merging Energy data: " + e.getMessage());
//        }
//
//        return mergedData;
//    }
//
//    private static String extractClassName(String fullMethodName) {
//        String methodName = fullMethodName.split("\\(")[0].split("\\$")[0].trim();
//        int lastDotIndex = methodName.lastIndexOf('.');
//        if (lastDotIndex > 0) {
//            return methodName.substring(0, lastDotIndex);
//        } else {
//            return methodName;
//        }
//    }
//
//    private static void writeToExcel(Map<String, MethodData> methodDataMap, String filePath) throws IOException {
//        try (Workbook workbook = new XSSFWorkbook()) {
//            Sheet sheet = workbook.createSheet("Data");
//
//            Row headerRow = sheet.createRow(0);
//            String[] headers = {"Method Signature", "Invocations", "Execution Time", "Instance Count", "Size (bytes)", "Energy (J)"};
//            createHeaderRow(headerRow, headers, workbook);
//
//            int rowNum = 1;
//            for (MethodData methodData : methodDataMap.values()) {
//                Row row = sheet.createRow(rowNum++);
//                row.createCell(0).setCellValue(methodData.methodSignature);
//                row.createCell(1).setCellValue(methodData.invocations);
//                row.createCell(2).setCellValue(methodData.executionTime);
//                row.createCell(3).setCellValue(methodData.instanceCount);
//                row.createCell(4).setCellValue(methodData.sizeBytes);
//                row.createCell(5).setCellValue(methodData.energyConsumption);
//            }
//
//            autoSizeColumns(sheet, headers.length);
//
//            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
//                workbook.write(fileOut);
//            }
//        } catch (IOException e) {
//            System.err.println("Error writing to Excel: " + e.getMessage());
//        }
//    }
//
//    private static void createHeaderRow(Row headerRow, String[] headers, Workbook workbook) {
//        CellStyle headerStyle = workbook.createCellStyle();
//        Font font = workbook.createFont();
//        font.setBold(true);
//        headerStyle.setFont(font);
//
//        for (int i = 0; i < headers.length; i++) {
//            Cell cell = headerRow.createCell(i);
//            cell.setCellValue(headers[i]);
//            cell.setCellStyle(headerStyle);
//        }
//    }
//
//    private static void autoSizeColumns(Sheet sheet, int numColumns) {
//        for (int i = 0; i < numColumns; i++) {
//            sheet.autoSizeColumn(i);
//        }
//    }
//}
// IFQN Interface
public interface IFQN {
    String getName();
}
