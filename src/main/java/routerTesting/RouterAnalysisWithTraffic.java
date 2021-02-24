package routerTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.ParallelEventsManager;

public class RouterAnalysisWithTraffic {
	private final static int EVENT_QUEUE_SIZE = 1048576 * 32;
	public final static double TIME_BIN_SIZE = 900;
	public final static double TOTAL_TIME_BIN = 96;
	private final String eventsFile;

	public RouterAnalysisWithTraffic(String eventsFile) {
		this.eventsFile = eventsFile;
	}

	public Map<Double, Map<String, Double>> processEventsFile() {
		ParallelEventsManager eventManager = new ParallelEventsManager(false, EVENT_QUEUE_SIZE);
		CarTrafficExtractor carTrafficExtractor = new CarTrafficExtractor();
		eventManager.addHandler(carTrafficExtractor);
		eventManager.initProcessing();
		new MatsimEventsReader(eventManager).readFile(eventsFile);
		System.out.println("Event processing complete");
		Map<Double, Map<String, List<Double>>> rawData = carTrafficExtractor.getRawData();
		return getTravelTimeMap(rawData);
	}

	public synchronized Map<Double, Map<String, Double>> getTravelTimeMap(
			Map<Double, Map<String, List<Double>>> rawData) {
		Map<Double, Map<String, Double>> linkTravelTimeMap = new HashMap<>();

		// Calculate
		Set<Double> rawDataKeySet = new HashSet<>();
		rawDataKeySet.addAll(rawData.keySet());
		for (Double timeBin : rawDataKeySet) {
			linkTravelTimeMap.put(timeBin, new HashMap<>());
			Map<String, List<Double>> rawDataForThisTimeBin = rawData.get(timeBin);
			Map<String, Double> linkTravelTimeMapAtThisTimeBin = linkTravelTimeMap.get(timeBin);
			Set<String> linkIds = new HashSet<>();
			linkIds.addAll(rawDataForThisTimeBin.keySet());
			for (String linkId : linkIds) {
				double sum = 0;
				List<Double> travelTimes = rawDataForThisTimeBin.get(linkId);
				for (Double travelTime : travelTimes) {
					sum += travelTime;
				}
				double meanValue = sum / travelTimes.size();
				linkTravelTimeMapAtThisTimeBin.put(linkId, meanValue);
			}
		}
		return linkTravelTimeMap;
	}

	private class CarTrafficExtractor implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler,
			LinkEnterEventHandler, LinkLeaveEventHandler {
		private final Map<Double, Map<String, List<Double>>> rawData = new HashMap<>();
		private final List<LinkTravelDataEntry> incompleteEntries = new ArrayList<>();

		public CarTrafficExtractor() {
			// Empty now
		}

		@Override
		public void reset(int iter) {
			for (double i = 0; i < TOTAL_TIME_BIN + 1; i++) {
				rawData.put(i, new HashMap<>());
			}
		}

		@Override
		public void handleEvent(VehicleEntersTrafficEvent event) {
			LinkTravelDataEntry linkTravelDataEntry = new LinkTravelDataEntry(event.getLinkId().toString(),
					event.getVehicleId().toString(), event.getTime());
			incompleteEntries.add(linkTravelDataEntry);
		}

		@Override
		public void handleEvent(LinkEnterEvent event) {
			LinkTravelDataEntry linkTravelDataEntry = new LinkTravelDataEntry(event.getLinkId().toString(),
					event.getVehicleId().toString(), event.getTime());
			incompleteEntries.add(linkTravelDataEntry);
		}

		@Override
		public void handleEvent(LinkLeaveEvent event) {
			String vehicleId = event.getVehicleId().toString();
			String linkId = event.getLinkId().toString();
			int indexToRemove = 0;
			for (int i = 0; i < incompleteEntries.size(); i++) {
				LinkTravelDataEntry linkTravelDataEntry = incompleteEntries.get(i);
				if (vehicleId.equals(linkTravelDataEntry.getVehicleId())
						&& linkId.equals(linkTravelDataEntry.getLinkId())) {
					linkTravelDataEntry.setExitTime(event.getTime());
					double timeBin = Math.min(TOTAL_TIME_BIN,
							Math.floor(linkTravelDataEntry.getEnterTime() / TIME_BIN_SIZE));
					List<Double> travelTimes = rawData.get(timeBin).getOrDefault(linkId, new ArrayList<>());
					travelTimes.add(linkTravelDataEntry.getTravelTime());
					rawData.get(timeBin).put(linkId, travelTimes);
					indexToRemove = i;
					break;
				}
			}
			incompleteEntries.remove(indexToRemove);
		}

		@Override
		public void handleEvent(VehicleLeavesTrafficEvent event) {
			String vehicleId = event.getVehicleId().toString();
			String linkId = event.getLinkId().toString();
			int indexToRemove = 0;
			for (int i = 0; i < incompleteEntries.size(); i++) {
				LinkTravelDataEntry linkTravelDataEntry = incompleteEntries.get(i);
				if (vehicleId.equals(linkTravelDataEntry.getVehicleId())
						&& linkId.equals(linkTravelDataEntry.getLinkId())) {
					linkTravelDataEntry.setExitTime(event.getTime());
					double timeBin = Math.min(TOTAL_TIME_BIN,
							Math.floor(linkTravelDataEntry.getEnterTime() / TIME_BIN_SIZE));
					List<Double> travelTimes = rawData.get(timeBin).getOrDefault(linkId, new ArrayList<>());
					travelTimes.add(linkTravelDataEntry.getTravelTime());
					rawData.get(timeBin).put(linkId, travelTimes);
					indexToRemove = i;
					break;
				}
			}
			incompleteEntries.remove(indexToRemove);
		}

		public Map<Double, Map<String, List<Double>>> getRawData() {
			return rawData;
		}
	}

	private class LinkTravelDataEntry {
		private String linkId;
		private String vehicleId;
		private double enterTime;
		private double exitTime;

		public LinkTravelDataEntry(String linkId, String vehicleId, double enterTime) {
			this.enterTime = enterTime;
			this.linkId = linkId;
			this.vehicleId = vehicleId;
		}

		public void setExitTime(double exitTime) {
			this.exitTime = exitTime;
		}

		public String getLinkId() {
			return linkId;
		}

		public String getVehicleId() {
			return vehicleId;
		}

		public double getEnterTime() {
			return enterTime;
		}

		public double getTravelTime() {
			return exitTime - enterTime;
		}

	}

}
