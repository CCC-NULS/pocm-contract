package io.nuls.pocm.contract.model;

import java.math.BigInteger;

/**
 * 抵押详细信息
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositDetailInfo {

    //抵押编号
    private long depositNumber;
    // 抵押金额（单位：na）
    private BigInteger depositAmount=BigInteger.ZERO;
    // 抵押开始高度
    private long depositHeight;

    //此抵押金额采矿获得的Token的分配地址（为空则默认为自身地址）
    private String miningAddress;

    public BigInteger getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigInteger depositAmount) {
        this.depositAmount = depositAmount;
    }

    public long getDepositHeight() {
        return depositHeight;
    }

    public void setDepositHeight(long depositHeight) {
        this.depositHeight = depositHeight;
    }

    public String getMiningAddress() {
        return miningAddress;
    }

    public void setMiningAddress(String miningAddress) {
        this.miningAddress = miningAddress;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    @Override
    public String toString(){
        return "{depositNumber:"+depositNumber+",depositHeight:"+depositHeight
                +",miningAddress:"+miningAddress+",depositAmount:"+depositAmount+"}";
    }
}
