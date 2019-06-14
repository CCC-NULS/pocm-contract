package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;
import io.nuls.pocm.contract.model.MiningInfo;

/**
 * 挖矿信息事件
 * @author: Long
 * @date: 2019-03-15
 */
public class MiningInfoEvent extends MiningInfo implements Event {

    public MiningInfoEvent(MiningInfo info){
        super(info);
    }

}
