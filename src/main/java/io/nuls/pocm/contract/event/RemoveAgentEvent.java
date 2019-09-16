package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

/**
 * @author: tag0313
 * @date: 2019-09-16
 */
public class RemoveAgentEvent implements Event {
    private String hash;

    public RemoveAgentEvent() {
    }

    public RemoveAgentEvent(String hash) {
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
