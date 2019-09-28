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

    /**
     * 由后台统一领取但是未发放的奖励金额
     */
    private BigInteger unTranferReceivedMining=BigInteger.ZERO;

    //后台统一领取时挖矿次数
    private int unTranferMiningCount=0;


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


    public BigInteger getUnTranferReceivedMining() {
        return unTranferReceivedMining;
    }

    public void setUnTranferReceivedMining(BigInteger unTranferReceivedMining) {
        this.unTranferReceivedMining = unTranferReceivedMining;
    }

    public int getUnTranferMiningCount() {
        return unTranferMiningCount;
    }

    public void setUnTranferMiningCount(int unTranferMiningCount) {
        this.unTranferMiningCount = unTranferMiningCount;
    }

    @Override
    public String toString(){
        return "{depositNumber:"+depositNumber+",miningAmount:"+miningAmount.toString()+",receiverMiningAddress:"+receiverMiningAddress
                +",miningCount:"+miningCount+",nextStartMiningCycle:"+nextStartMiningCycle
                +",depositorAddress:"+depositorAddress+",unTranferReceivedMining:"+unTranferReceivedMining.toString()+",unTranferMiningCount:"+unTranferMiningCount+"}";
    }

}
