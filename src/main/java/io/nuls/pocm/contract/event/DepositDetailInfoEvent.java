package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;
import io.nuls.pocm.contract.model.DepositDetailInfo;
import io.nuls.pocm.contract.model.DepositInfo;

/**
 * 抵押信息事件
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositDetailInfoEvent extends DepositDetailInfo implements Event {

    public DepositDetailInfoEvent(DepositDetailInfo info){
        super(info);
    }

}
