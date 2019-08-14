package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

public class QuitDepositEvent implements Event {

    //抵押者地址
    private String  depositorAddress;

    public QuitDepositEvent(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }
}
