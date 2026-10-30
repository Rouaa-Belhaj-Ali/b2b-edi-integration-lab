package b2blab;

import java.util.*;

/** A document stored by the trading-network layer: envelope data plus content parts. */
final class BizDoc {
    final String id, docType, sender, receiver;
    String status = "NEW";
    final Map<String, Object> content = new LinkedHashMap<>();

    BizDoc(String id, String docType, String sender, String receiver) {
        this.id = id; this.docType = docType; this.sender = sender; this.receiver = receiver;
    }

    @Override public String toString() { return id + " (" + docType + ", " + sender + ", " + status + ")"; }
}
