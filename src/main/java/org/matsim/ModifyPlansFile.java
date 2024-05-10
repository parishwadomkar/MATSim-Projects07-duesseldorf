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
			FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath.replace(".xml", ".xml"));

			byte[] buffer = new byte[1024];
			int len;
			while ((len = gzis.read(buffer)) > 0) {
				fileOutputStream.write(buffer, 0, len);
			}
			gzis.close();
			fileOutputStream.close();

			// Now parse the decompressed XML file
			File decompressedFile = new File(outputFilePath.replace(".xml", ".xml"));
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(decompressedFile);
			doc.getDocumentElement().normalize();

			// Modify all elements where mode="car"
			NodeList nodeList = doc.getElementsByTagName("leg");
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) node;
					if ("car".equals(element.getAttribute("mode"))) {
						element.setAttribute("mode", "EV_car");
					}
				}
			}

			// Write the content into XML file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(outputFilePath));
			transformer.transform(source, result);

			System.out.println("Done modifying the plans file!");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
