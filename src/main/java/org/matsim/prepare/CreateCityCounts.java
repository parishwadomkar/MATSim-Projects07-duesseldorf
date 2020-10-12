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
import java.util.List;
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

        List<Object> counts = Files.list(input)
                .filter(f -> f.getFileName().toString().endsWith(".zip"))
                .collect(Collectors.toList()) // need to collect first
                .parallelStream()
                .map(this::readCounts)
                .collect(Collectors.toList());

        return 0;
    }

    /**
     * Read count data from for all sensors for zip file.
     */
    private Object readCounts(Path zip) {

        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;

                readCsvCounts(entry.getName(), in);
                log.info("Finished reading {}", entry.getName());
            }

        } catch (IOException e) {
            log.error("Could not read zip file {}", zip, e);
        }


        return null;
    }

    /**
     * Read counts from CSV data.
     */
    private void readCsvCounts(String name, ZipInputStream in) throws IOException {

        // reader must not be closed
        InputStreamReader reader = new InputStreamReader(in);
        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                .withDelimiter(';')
                .withFirstRecordAsHeader()
        );

        for (CSVRecord record : parser) {

//                System.out.println(record);
        }
    }


}
