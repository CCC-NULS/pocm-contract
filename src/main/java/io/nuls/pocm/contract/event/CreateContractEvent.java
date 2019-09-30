package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;

import java.math.BigDecimal;
import java.math.BigInteger;

public class CreateContractEvent implements Event {
    private String tokenAddress;
    private BigDecimal cycleRewardTokenAmount;
    private int awardingCycle;
    private BigInteger minimumDepositNULS;
    private int minimumLocked;
    private boolean openConsensus;
    private int lockedTokenDay;
    private String authorizationCode;
    private String rewardHalvingCycle;
    private String maximumDepositAddressCount;

    public CreateContractEvent(String tokenAddress, BigDecimal cycleRewardTokenAmount, int awardingCycle,
                               BigInteger minimumDepositNULS, int minimumLocked, boolean openConsensus, int lockedTokenDay,
                               String authorizationCode, String rewardHalvingCycle, String maximumDepositAddressCount){
        this.tokenAddress=tokenAddress;
        this.cycleRewardTokenAmount=cycleRewardTokenAmount;
        this.awardingCycle=awardingCycle;
        this.minimumDepositNULS=minimumDepositNULS;
        this.minimumLocked=minimumLocked;
        this.openConsensus=openConsensus;
        this.lockedTokenDay = lockedTokenDay;
        this.authorizationCode=authorizationCode;
        this.rewardHalvingCycle=rewardHalvingCycle;
        this.maximumDepositAddressCount=maximumDepositAddressCount;
    }

    public String getTokenAddress() {
        return tokenAddress;
    }

    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }

    public BigDecimal getCycleRewardTokenAmount() {
        return cycleRewardTokenAmount;
    }

    public void setCycleRewardTokenAmount(BigDecimal cycleRewardTokenAmount) {
        this.cycleRewardTokenAmount = cycleRewardTokenAmount;
    }

    public int getAwardingCycle() {
        return awardingCycle;
    }

    public void setAwardingCycle(int awardingCycle) {
        this.awardingCycle = awardingCycle;
    }

    public BigInteger getMinimumDepositNULS() {
        return minimumDepositNULS;
    }

    public void setMinimumDepositNULS(BigInteger minimumDepositNULS) {
        this.minimumDepositNULS = minimumDepositNULS;
    }

    public int getMinimumLocked() {
        return minimumLocked;
    }

    public void setMinimumLocked(int minimumLocked) {
        this.minimumLocked = minimumLocked;
    }

    public boolean isOpenConsensus() {
        return openConsensus;
    }

    public void setOpenConsensus(boolean openConsensus) {
        this.openConsensus = openConsensus;
    }

    public int getLockedTokenDay() {
        return lockedTokenDay;
    }

    public void setLockedTokenDay(int lockedTokenDay) {
        this.lockedTokenDay = lockedTokenDay;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getRewardHalvingCycle() {
        return rewardHalvingCycle;
    }

    public void setRewardHalvingCycle(String rewardHalvingCycle) {
        this.rewardHalvingCycle = rewardHalvingCycle;
    }

    public String getMaximumDepositAddressCount() {
        return maximumDepositAddressCount;
    }

    public void setMaximumDepositAddressCount(String maximumDepositAddressCount) {
        this.maximumDepositAddressCount = maximumDepositAddressCount;
    }
}
