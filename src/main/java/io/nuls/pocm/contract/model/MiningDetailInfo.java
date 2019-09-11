package io.nuls.pocm.contract.model;

import java.math.BigInteger;
/**
 * 挖矿详细信息
 * @author: Long
 * @date: 2019-03-15
 */
public class MiningDetailInfo {
    //对应的抵押编号
    private long depositNumber;
    // 挖矿金额
    private BigInteger miningAmount=BigInteger.ZERO;

    //采矿获得的Token的接收地址
    private String receiverMiningAddress;

    // 挖矿次数
    private int miningCount;

    // 下次挖矿的奖励周期
    private int nextStartMiningCycle;

    //来源地址
    private String depositorAddress;

    //领取奖励是否结束，当Token数量不足并且已经领取了属于自己的那部分之后，该字段标记为true，不能再领取奖励
    private boolean rewardsEnd=false;

    //当最后Token可分配的余额不足时，此字段记录该挖矿剩余可领取的奖励数额
    private BigInteger canRewarsAmountWhenFinal=BigInteger.ZERO;

    public MiningDetailInfo(String miningAddress ,String depositorAddress,long depositNumber){
        this.receiverMiningAddress=miningAddress;
        this.depositorAddress=depositorAddress;
        this.depositNumber=depositNumber;
        this.miningCount=0;
    }

    public BigInteger getMiningAmount() {
        return miningAmount;
    }

    public void setMiningAmount(BigInteger miningAmount) {
        this.miningAmount = miningAmount;
    }

    public String getReceiverMiningAddress() {
        return receiverMiningAddress;
    }

    public void setReceiverMiningAddress(String receiverMiningAddress) {
        this.receiverMiningAddress = receiverMiningAddress;
    }

    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }

    public int getMiningCount() {
        return miningCount;
    }

    public void setMiningCount(int miningCount) {
        this.miningCount = miningCount;
    }

    public int getNextStartMiningCycle() {
        return nextStartMiningCycle;
    }

    public void setNextStartMiningCycle(int nextStartMiningCycle) {
        this.nextStartMiningCycle = nextStartMiningCycle;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    public boolean isRewardsEnd() {
        return rewardsEnd;
    }

    public void setRewardsEnd(boolean rewardsEnd) {
        this.rewardsEnd = rewardsEnd;
    }

    public BigInteger getCanRewarsAmountWhenFinal() {
        return canRewarsAmountWhenFinal;
    }

    public void setCanRewarsAmountWhenFinal(BigInteger canRewarsAmountWhenFinal) {
        this.canRewarsAmountWhenFinal = canRewarsAmountWhenFinal;
    }

    @Override
    public String toString(){
        return "{depositNumber:"+depositNumber+",miningAmount:"+miningAmount.toString()+",receiverMiningAddress:"+receiverMiningAddress
                +",miningCount:"+miningCount+",nextStartMiningCycle:"+nextStartMiningCycle
                +",depositorAddress:"+depositorAddress+",rewardsEnd:"+rewardsEnd+",canRewarsAmountWhenFinal:"+canRewarsAmountWhenFinal+"}";
    }

}
