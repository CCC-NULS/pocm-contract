package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

import java.math.BigDecimal;

public class CreateContractEvent implements Event {
    private String tokenAddress;
    private BigDecimal price;
    private int awardingCycle;
    private BigDecimal minimumDepositNULS;
    private int minimumLocked;
    private boolean openConsensus;
    private String authorizationCode;
    private String rewardHalvingCycle;
    private String maximumDepositAddressCount;

    public CreateContractEvent(String tokenAddress, BigDecimal price,int awardingCycle,
                               BigDecimal minimumDepositNULS,int minimumLocked, boolean openConsensus,
                               String authorizationCode,String rewardHalvingCycle, String maximumDepositAddressCount){
        this.tokenAddress=tokenAddress;
        this.price=price;
        this.awardingCycle=awardingCycle;
        this.minimumDepositNULS=minimumDepositNULS;
        this.minimumLocked=minimumLocked;
        this.openConsensus=openConsensus;
        this.authorizationCode=authorizationCode;
        this.rewardHalvingCycle=rewardHalvingCycle;
        this.maximumDepositAddressCount=maximumDepositAddressCount;
    }


}
