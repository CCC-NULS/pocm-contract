package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;
import io.nuls.pocm.contract.model.CurrentMingInfo;

import java.util.List;

public class CurrentMiningInfoEvent implements Event {

    List<CurrentMingInfo> mingInfosList;

    public CurrentMiningInfoEvent(List<CurrentMingInfo> list){
        this.mingInfosList=list;
    }

    public List<CurrentMingInfo> getMingInfosList() {
        return mingInfosList;
    }

    public void setMingInfosList(List<CurrentMingInfo> mingInfosList) {
        this.mingInfosList = mingInfosList;
    }
}


