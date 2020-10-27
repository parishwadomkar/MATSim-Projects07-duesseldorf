package org.matsim.prepare;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NodeMatcher {

    Map<String, MatchedLinkID> parseNodeMatching(String filePath) throws IOException {

        Map<String, MatchedLinkID> result = new HashMap<>();

        try (var reader = new FileReader(filePath)) {

            CSVParser records = CSVFormat
                    .newFormat(';')
                    .withAllowMissingColumnNames()
                    .withFirstRecordAsHeader()
                    .parse(reader);

            for (CSVRecord record : records)

                if (StringUtils.isNoneBlank(record.get("Node_from_R1"))) {

                    var dzNumber_1 = record.get("DZ_Nr") + "_R1";
                    var fromID_1 = record.get("Node_from_R1");
                    var toID_1 = record.get("Node_to_R1");
                    var linkID_1 = record.get("Link_ID_R1");

                    var matchedCount1 = new MatchedLinkID(fromID_1, toID_1, linkID_1);

                    result.put(dzNumber_1, matchedCount1);

                    var dzNumber_2 = record.get("DZ_Nr") + "_R2";
                    var fromID_2 = record.get("Node_from_R2");
                    var toID_2 = record.get("Node_to_R2");
                    var linkID_2 = record.get("Link_ID_R2");

                    var matchedCount2 = new MatchedLinkID(fromID_2, toID_2, linkID_2);

                    result.put(dzNumber_2, matchedCount2);

                }
        }

        return result;
    }

    @Getter
    @RequiredArgsConstructor
    static class MatchedLinkID {

        private final String fromID;
        private final String toID;
        private final String linkID;

        public String getLinkID() {
            return linkID;
        }

        public String getFromID() {
            return fromID;
        }

        public String getToID() {
            return toID;
        }

        @Override
        public String toString() {

            return this.linkID;

        }
    }
}
