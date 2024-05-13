package org.matsim;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;

public class GenerateVehicleSpecifications {

	public static void main(String[] args) {
		String plansFilePath = "C:\\Users\\omkarp\\IdeaProjects\\MATSim-Projects07-duesseldorf\\Inputfiles\\mod_duf-v1.7-1pct.plans.xml";
		String vehicleFilePath = "C:\\Users\\omkarp\\IdeaProjects\\MATSim-Projects07-duesseldorf\\Inputfiles\\vehicles.xml";
		HashSet<String> personIds = new HashSet<>();

		try {
			File inputFile = new File(plansFilePath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();

			NodeList persons = doc.getElementsByTagName("person");
			System.out.println("Total persons: " + persons.getLength());

			for (int i = 0; i < persons.getLength(); i++) {
				Node personNode = persons.item(i);
				if (personNode.getNodeType() == Node.ELEMENT_NODE) {
					Element personElement = (Element) personNode;
					NodeList legs = personElement.getElementsByTagName("leg");
					boolean hasEV = false;
					for (int j = 0; j < legs.getLength(); j++) {
						Node legNode = legs.item(j);
						if (legNode.getNodeType() == Node.ELEMENT_NODE) {
							Element legElement = (Element) legNode;
							if ("EV_car".equals(legElement.getAttribute("mode"))) {
								hasEV = true;
								break; // Assumes each person only needs to be counted once
							}
						}
					}
					if (hasEV) {
						personIds.add(personElement.getAttribute("id"));
					}
				}
			}

			System.out.println("Total EV users: " + personIds.size());

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(vehicleFilePath))) {
				writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				writer.write("<vehicleDefinitions xmlns=\"http://www.matsim.org/files/dtd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.matsim.org/files/dtd http://www.matsim.org/files/dtd/vehicleDefinitions_v1.0.xsd\">\n");

				for (String id : personIds) {
					writer.write(String.format("<vehicle id=\"veh_%s\" type=\"EV_car\">\n", id));
					writer.write("    <attribute name=\"initialSoC\" class=\"java.lang.Double\">100.0</attribute>\n");
					writer.write("</vehicle>\n");
				}

				writer.write("</vehicleDefinitions>");
				System.out.println("Vehicle specifications generated for " + personIds.size() + " users!");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
