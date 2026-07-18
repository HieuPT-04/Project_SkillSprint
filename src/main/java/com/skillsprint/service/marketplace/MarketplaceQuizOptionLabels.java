package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

final class MarketplaceQuizOptionLabels {

    private static final char FIRST_LABEL = 'A';

    private MarketplaceQuizOptionLabels() {
    }

    static void relabel(List<ObjectNode> options) {
        for (int index = 0; index < options.size(); index++) {
            options.get(index).put("label", labelAt(index));
        }
    }

    static String labelAt(int index) {
        return String.valueOf((char) (FIRST_LABEL + index));
    }
}
