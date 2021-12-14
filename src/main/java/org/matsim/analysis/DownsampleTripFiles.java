package org.matsim.analysis;

import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DownsampleTripFiles {
	public static void main(String[] args) {
		BufferedReader baseReader = IOUtils.getBufferedReader(args[0]);
		Random random = MatsimRandom.getLocalInstance();
		random.setSeed(1234);
		File path = new File(args[0]).getParentFile();
		BufferedWriter baseWriter = IOUtils.getBufferedWriter(path.getAbsolutePath().toString() + "/downsampled.output_trips.csv.gz");
		List<String> sampledTrips = new ArrayList<>();
		try {
			String line = baseReader.readLine();
			baseWriter.write(line); // write the header
			line = baseReader.readLine();
			while (line != null) {
				if (random.nextDouble() <= 0.1) {
					sampledTrips.add(line.split(";")[2]);
					baseWriter.write(line+"\n");
				}
				line = baseReader.readLine();
			}
			baseWriter.flush();
			baseWriter.close();
			baseReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
