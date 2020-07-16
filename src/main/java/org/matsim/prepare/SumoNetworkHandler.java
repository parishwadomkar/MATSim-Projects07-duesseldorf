package org.matsim.prepare;

import org.matsim.api.core.v01.Coord;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Parses specific information from sumo network file.
 */
class SumoNetworkHandler extends DefaultHandler {

    final double[] netOffset = new double[2];

    /**
     * All junctions.
     */
    final Map<String, Junction> junctions = new HashMap<>();

    /**
     * Edges mapped by id.
     */
    final Map<String, Edge> edges = new HashMap<>();

    /**
     * Map lane id to their edge.
     */
    final Map<String, Edge> lanes = new HashMap<>();

    /**
     * All connections mapped by the origin (from).
     */
    final Map<String, List<Connection>> connections = new HashMap<>();

    /**
     * Parsed link types.
     */
    final Map<String, Type> types = new HashMap<>();

    /**
     * Stores current parsed edge.
     */
    private Edge tmpEdge = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {


        switch (qName) {

            case "location":
                String[] netOffsets = attributes.getValue("netOffset").split(",");
                netOffset[0] = Double.parseDouble(netOffsets[0]);
                netOffset[1] = Double.parseDouble(netOffsets[1]);

                break;

            case "type":

                String typeId = attributes.getValue("id");

                types.put(typeId, new Type(typeId, attributes.getValue("allow"), attributes.getValue("disallow"),
                        Double.parseDouble(attributes.getValue("speed"))));

                break;

            case "edge":

                String shape = attributes.getValue("shape");
                tmpEdge = new Edge(
                        attributes.getValue("id"),
                        attributes.getValue("from"),
                        attributes.getValue("to"),
                        attributes.getValue("type"),
                        shape == null ? new String[0] : shape.split(" ")
                );

                break;

            case "lane":
                Lane lane = new Lane(
                        attributes.getValue("id"),
                        Integer.parseInt(attributes.getValue("index")),
                        Double.parseDouble(attributes.getValue("length")),
                        Double.parseDouble(attributes.getValue("speed"))
                );

                tmpEdge.lanes.add(lane);
                lanes.put(lane.id, tmpEdge);

                break;

            case "param":

                if (tmpEdge == null)
                    break;

                String value = attributes.getValue("value");

                switch (attributes.getValue("key")) {
                    case "origId":
                        tmpEdge.origId = value;
                        break;
                    case "origFrom":
                        tmpEdge.origFrom = value;
                        break;
                    case "origTo":
                        tmpEdge.origTo = value;
                        break;
                }

                break;

            case "junction":

                String inc = attributes.getValue("incLanes");

                List<String> lanes = Arrays.asList(inc.split(" "));
                String id = attributes.getValue("id");
                junctions.put(id, new Junction(
                        id,
                        attributes.getValue("type"),
                        lanes,
                        new double[]{Double.parseDouble(attributes.getValue("x")), Double.parseDouble(attributes.getValue("y"))}
                ));

                break;

            case "connection":

                // aggregate edges split by sumo again
                String from = attributes.getValue("from");
                String origin = from.split("#")[0];
                if (origin.startsWith("-"))
                    origin = origin.substring(1);

                connections.computeIfAbsent(origin, k -> new ArrayList<>())
                        .add(new Connection(from, attributes.getValue("to"),
                                Integer.parseInt(attributes.getValue("fromLane")),
                                Integer.parseInt(attributes.getValue("toLane")),
                                attributes.getValue("dir")));

                break;


        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("edge".equals(qName)) {
            edges.put(tmpEdge.id, tmpEdge);
            tmpEdge = null;
        }
    }

    public Coord createCoord(double[] xy) {
        return new Coord(xy[0] - netOffset[0], xy[1] - netOffset[1]);
    }

    /**
     * Edge from the SUMO network.
     */
    static final class Edge {

        final String id;
        final String from;
        final String to;
        final String type;
        final List<double[]> shape = new ArrayList<>();

        final List<Lane> lanes = new ArrayList<>();

        String origId;

        @Nullable
        String origFrom;

        @Nullable
        String origTo;

        public Edge(String id, String from, String to, String type, String[] shape) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.type = type;

            for (String coords : shape) {
                String[] split = coords.split(",");
                this.shape.add(new double[]{Double.parseDouble(split[0]), Double.parseDouble(split[1])});
            }
        }


        @Override
        public String toString() {
            return "Edge{" +
                    "id='" + id + '\'' +
                    ", from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", origId='" + origId + '\'' +
                    ", origFrom='" + origFrom + '\'' +
                    ", origTo='" + origTo + '\'' +
                    '}';
        }
    }

    static final class Lane {

        final String id;
        final int index;
        final double length;
        final double speed;

        Lane(String id, int index, double length, double speed) {
            this.id = id;
            this.index = index;
            this.length = length;
            this.speed = speed;
        }
    }

    static final class Junction {

        final String id;
        final String type;
        final List<String> incLanes;
        final double[] coord;

        Junction(String id, String type, List<String> incLanes, double[] coord) {
            this.id = id;
            this.type = type;
            this.incLanes = incLanes;
            this.coord = coord;
        }
    }

    static final class Connection {

        final String from;
        final String to;
        final int fromLane;
        final int toLane;

        // could be enum probably
        final String dir;

        Connection(String from, String to, int fromLane, int toLane, String dir) {
            this.from = from;
            this.to = to;
            this.fromLane = fromLane;
            this.toLane = toLane;
            this.dir = dir;
        }

        @Override
        public String toString() {
            return "Connection{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", fromLane=" + fromLane +
                    ", toLane=" + toLane +
                    ", dir='" + dir + '\'' +
                    '}';
        }
    }


    static final class Type {

        final String id;
        final Set<String> allow = new HashSet<>();
        final Set<String> disallow = new HashSet<>();
        final double speed;

        /**
         * Set if id is highway.[type]
         */
        final String highway;

        Type(String id, String allow, String disallow, double speed) {
            this.id = id;
            this.speed = speed;
            if (allow != null)
                Collections.addAll(this.allow, allow.split(" "));

            if (disallow != null)
                Collections.addAll(this.disallow, disallow.split(" "));

            if (id.startsWith("highway.")) {
                // split compound types
                if (id.contains("|"))
                    id = id.split("\\|")[0];

                highway = id.substring(8);
            }
            else
                highway = null;

        }
    }
}
