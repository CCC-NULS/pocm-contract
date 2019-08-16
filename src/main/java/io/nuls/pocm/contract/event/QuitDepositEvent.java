package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

import java.util.List;
import java.util.Set;

public class QuitDepositEvent implements Event {

    //对应的抵押编号
    private List<Long> depositNumbers;
    //抵押者地址
    private String  depositorAddress;

    public QuitDepositEvent(List<Long> depositNumbers,String depositorAddress) {
        this.depositNumbers=depositNumbers;
        this.depositorAddress = depositorAddress;
    }
}
