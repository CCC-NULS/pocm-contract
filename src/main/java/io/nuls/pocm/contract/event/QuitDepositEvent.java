package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

import java.util.List;

public class QuitDepositEvent implements Event {

    //对应的抵押编号
    private List<Long> depositNumbers;
    //抵押者地址
    private String  depositorAddress;

    public QuitDepositEvent(List<Long> depositNumbers,String depositorAddress) {
        this.depositNumbers=depositNumbers;
        this.depositorAddress = depositorAddress;
    }

    public List<Long> getDepositNumbers() {
        return depositNumbers;
    }

    public void setDepositNumbers(List<Long> depositNumbers) {
        this.depositNumbers = depositNumbers;
    }

    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }
}
