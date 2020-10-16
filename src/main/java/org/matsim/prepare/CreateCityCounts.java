package org.matsim.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.data.time.Day;
import picocli.CommandLine;
import scala.Int;
import scala.util.parsing.combinator.testing.Str;

import javax.lang.model.util.Types;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CommandLine.Command(
        name = "createCityCounts",
        description = "Aggregate and convert counts from inner city"
)
public class CreateCityCounts implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger(CreateCityCounts.class);

    @CommandLine.Option(names = {"--input"}, description = "Input folder with zip files",
            defaultValue = "../../shared-svn/komodnext/data/counts")
    private Path input;

    public static void main(String[] args) throws IOException {
        System.exit(new CommandLine(new CreateCityCounts()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        List<Map<String, Map<String, DayCounts>>> collect = Files.list(input)
                .filter(f -> f.getFileName().toString().endsWith(".zip"))
                .collect(Collectors.toList()) // need to collect first
                .parallelStream()
                .map(this::readCounts)
                .collect(Collectors.toList());

        for (Map<String, Map<String, DayCounts>> collectTmp : collect) {
            for (Map.Entry<String, Map<String, DayCounts>> collect2Tmp : collectTmp.entrySet()) {

                log.info("**********************************************************************************************************************************");
                log.info("* Stattion ID: " + collect2Tmp.getKey());

                for (Map.Entry<String, DayCounts> collect3Tmp : collect2Tmp.getValue().entrySet()) {

                    log.info("* Month: " + collect3Tmp.getKey());
                    log.info("* Counts per hour: " + collect3Tmp.getValue().getCounts());
                }
                log.info("**********************************************************************************************************************************");
            }
        }
        return 0;
    }

    /**
     * Read one month of count data from for all sensors for zip file.
     */
    private Map<String, Map<String, DayCounts>> readCounts(Path zip) {

        Map<String, Map<String, DayCounts>> result = new HashMap<>();

        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;

                String id = entry.getName().split("_")[2].substring(0, 15);
                String monthNumber = entry.getName().split("Verarbeitet-19")[1].split("01_")[0];

                Map<String, DayCounts> month = readCsvCounts(in, monthNumber);

                result.put(id, month);
                log.info("Finished reading {}", entry.getName());
            }
        } catch (IOException e) {
            log.error("Could not read zip file {}", zip, e);
        }
        return result;
    }

    /**
     * Read counts from CSV data for one station. One month per file.
     *
     * @return map of date to counts for whole day.
     */
    private Map<String, DayCounts> readCsvCounts(ZipInputStream in, String monthNumber) throws IOException {

        Map<Integer, List<Double>> tempCountSum = new HashMap<>();
        Map<String, DayCounts> result = new HashMap<>();
        List<String> holidays2019 = Arrays.asList("01.01.2019", "19.04.2019", "22.04.2019", "01.05.2019",
                "30.05.2019", "10.06.2019", "20.06.2019", "03.10.2019", "01.11.2019", "25.12.2019",
                "26.12.2019");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        List<Integer> weekendDaysList = Arrays.asList(1, 5, 6, 7);
        Integer[] hourCountsTmp = new Integer[24];
        Double countMean;

        InputStreamReader reader = new InputStreamReader(in);

        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                .withDelimiter(';')
                .withFirstRecordAsHeader()
        );

        for (CSVRecord record : parser) {
            if (!isWeekend(LocalDate.parse(record.get("Time").split(" ")[0], formatter), weekendDaysList) || !holidays2019.contains(record.get("Time").split(" ")[0])) {

                Integer hour = Integer.parseInt(record.get("Time").split(" ")[1].split(":")[0]);
                Double value = Double.valueOf(record.get("processed_all_vol").replaceAll(",", "."));
                tempCountSum.computeIfAbsent(hour, k -> new ArrayList<>()).add(value);
            }
        }

        for (Map.Entry<Integer, List<Double>> meanCounts : tempCountSum.entrySet()) {
            countMean = 0.0;
            for (Double value : meanCounts.getValue()) {
                countMean += value;
            }
            countMean = countMean/(meanCounts.getValue().size());
            hourCountsTmp[meanCounts.getKey()] = countMean.intValue();
        }
        DayCounts dayCount = new DayCounts(hourCountsTmp);
        result.put(monthNumber, dayCount);
        return result;
    }

    private boolean isWeekend(LocalDate date, List<Integer> weekendDaysList) {
        return weekendDaysList.contains(date.getDayOfWeek().getValue());
    }


    /**
     * Contains counts for one day.
     */
    private static class DayCounts {

        /**
         * Counts for each hour of the day.
         */
        Integer[] counts;

        public DayCounts(Integer[] counts) {
            this.counts = counts;
        }

        public String getCounts() {
            String ret = "[";
            for (Integer count : counts) {
                ret = ret + count.toString() + ", ";
            }
            ret = ret.substring(0, ret.length() - 2);
            ret = ret + "]";
            return ret;
        }
    }
}
