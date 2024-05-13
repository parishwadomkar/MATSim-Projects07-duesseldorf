package org.matsim;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class ModifyPlansFile {

	public static void main(String[] args) {
		try {
			String inputFilePath = "C:\\Users\\omkarp\\IdeaProjects\\MATSim-Projects07-duesseldorf\\Inputfiles\\duesseldorf-v1.7-1pct.plans.xml.gz";
			String outputFilePath = "C:\\Users\\omkarp\\IdeaProjects\\MATSim-Projects07-duesseldorf\\Inputfiles\\mod_duf-v1.7-1pct.plans.xml";

			// Decompress the .gz file first
			File inputFile = new File(inputFilePath);
			InputStream fileIn = new FileInputStream(inputFile);
			GZIPInputStream gzis = new GZIPInputStream(fileIn);
			FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath);

			byte[] buffer = new byte[1024];
			int len;
			int carToEVCarCount = 0;
			int chargingActivitiesAdded = 0;
			int vehicleIdsAssigned = 0;

			while ((len = gzis.read(buffer)) > 0) {
				fileOutputStream.write(buffer, 0, len);
			}
			gzis.close();
			fileOutputStream.close();

			// Now parse the decompressed XML file
			File decompressedFile = new File(outputFilePath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(decompressedFile);
			doc.getDocumentElement().normalize();

			NodeList legs = doc.getElementsByTagName("leg");
			for (int i = 0; i < legs.getLength(); i++) {
				Node leg = legs.item(i);
				if (leg.getNodeType() == Node.ELEMENT_NODE) {
					Element legElement = (Element) leg;
					if ("car".equals(legElement.getAttribute("mode"))) {
						legElement.setAttribute("mode", "EV_car");
						carToEVCarCount++;
						String personId = ((Element) leg.getParentNode().getParentNode()).getAttribute("id");
						legElement.setAttribute("vehicleId", "veh_" + personId);
						vehicleIdsAssigned++;
					}
				}
			}

			NodeList activities = doc.getElementsByTagName("activity");
			for (int i = 0; i < activities.getLength(); i++) {
				Node activity = activities.item(i);
				if (activity.getNodeType() == Node.ELEMENT_NODE) {
					Element actElement = (Element) activity;
					String type = actElement.getAttribute("type");

					// Check if it's a home, work, leisure or shopping activity where charging might occur
					if (type.contains("home") || type.contains("work") || type.contains("leisure") || type.contains("shopping")) {
						Element chargingActivity = (Element) actElement.cloneNode(true);
						chargingActivity.setAttribute("type", "charging");
						chargingActivity.setAttribute("start_time", actElement.getAttribute("end_time"));
						chargingActivity.setAttribute("end_time", ""); // Handle this in your simulation specific logic

						Node nextSibling = actElement.getNextSibling();
						if (nextSibling != null) {
							actElement.getParentNode().insertBefore(chargingActivity, nextSibling);
						} else {
							actElement.getParentNode().appendChild(chargingActivity);
						}
						chargingActivitiesAdded++;
					}
				}
			}

			// Write the content into XML file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(outputFilePath));
			transformer.transform(source, result);

			System.out.println("Modifying the plans file:");
			System.out.println("Total 'car' to 'EV_car' modifications: " + carToEVCarCount);
			System.out.println("Total charging activities added: " + chargingActivitiesAdded);
			System.out.println("Total vehicle IDs assigned: " + vehicleIdsAssigned);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

// this code modifies the plans file by revising the car to- 'EV_car'; adds charging activities while car is parked/stationary and vehicle IDs to legs.
