package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;
import io.nuls.pocm.contract.model.DepositDetailInfo;

import java.math.BigInteger;

/**
 * 抵押信息事件
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositDetailInfoEvent extends DepositDetailInfo implements Event {
    //本次抵押的金额
    private BigInteger depositValue;
    public DepositDetailInfoEvent(DepositDetailInfo info,BigInteger value){
        super(info);
        this.depositValue=value;
    }

    public BigInteger getDepositValue() {
        return depositValue;
    }

    public void setDepositValue(BigInteger depositValue) {
        this.depositValue = depositValue;
    }
}
