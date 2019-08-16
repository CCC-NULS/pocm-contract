package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

import java.math.BigDecimal;
import java.math.BigInteger;

public class CreateContractEvent implements Event {
    private String tokenAddress;
    private BigInteger cycleRewardTokenAmount;
    private int awardingCycle;
    private BigInteger minimumDepositNULS;
    private int minimumLocked;
    private boolean openConsensus;
    private String authorizationCode;
    private String rewardHalvingCycle;
    private String maximumDepositAddressCount;

    public CreateContractEvent(String tokenAddress, BigInteger cycleRewardTokenAmount, int awardingCycle,
                               BigInteger minimumDepositNULS, int minimumLocked, boolean openConsensus,
                               String authorizationCode, String rewardHalvingCycle, String maximumDepositAddressCount){
        this.tokenAddress=tokenAddress;
        this.cycleRewardTokenAmount=cycleRewardTokenAmount;
        this.awardingCycle=awardingCycle;
        this.minimumDepositNULS=minimumDepositNULS;
        this.minimumLocked=minimumLocked;
        this.openConsensus=openConsensus;
        this.authorizationCode=authorizationCode;
        this.rewardHalvingCycle=rewardHalvingCycle;
        this.maximumDepositAddressCount=maximumDepositAddressCount;
    }


}
