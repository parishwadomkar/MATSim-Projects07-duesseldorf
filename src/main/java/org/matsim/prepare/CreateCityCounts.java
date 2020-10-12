package org.matsim.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // list of months, mapped to station mapped to day

        // TODO: transform and aggregate
        // station id as top level

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

                Map<String, DayCounts> month = readCsvCounts(in);

                // TODO which month is contained in the zip file name
                result.put(entry.getName(), month);

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
    private Map<String, DayCounts> readCsvCounts(ZipInputStream in) throws IOException {

        // reader must not be closed
        InputStreamReader reader = new InputStreamReader(in);
        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                .withDelimiter(';')
                .withFirstRecordAsHeader()
        );

        Map<String, DayCounts> result = new HashMap<>();

        for (CSVRecord record : parser) {

            // TODO: aggregate by hours
//                System.out.println(record);
        }

        return result;
    }


    /**
     * Contains counts for one day.
     */
    private static class DayCounts {

        /**
         * Counts for each hour of the day.
         */
        double[] counts = new double[24];

    }

}
