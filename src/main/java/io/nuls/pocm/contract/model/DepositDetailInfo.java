package io.nuls.pocm.contract.model;

import io.nuls.pocm.contract.util.PocmUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 抵押详细信息
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositDetailInfo {

    //抵押编号
    private long depositNumber;
    // 抵押金额（单位：na），=可参与POCM的抵押金额+锁定金额
    private BigInteger depositAmount=BigInteger.ZERO;

    //可参与POCM的抵押金额（单位：na）=抵押金额*0.9
    private BigInteger availableAmount=BigInteger.ZERO;
    //锁定金额（单位：na） =抵押金额*0.1
    private BigInteger lockedAmount=BigInteger.ZERO;

    // 最后一次抵押开始高度
    private long depositHeight;

    //此抵押金额采矿获得的Token的分配地址（为空则默认为自身地址）
    private String miningAddress;

    public DepositDetailInfo(){
    }

    public DepositDetailInfo(DepositDetailInfo info){
        this.depositNumber=info.depositNumber;
        this.depositAmount=info.depositAmount;
        this.availableAmount=info.availableAmount;
        this.lockedAmount=info.lockedAmount;
        this.depositHeight=info.depositHeight;
        this.miningAddress=info.miningAddress;
    }

    public BigInteger getDepositAmount() {
        return depositAmount;
    }

    /**
     * 设置抵押金额的同时，计算锁定金额和可用金额
     * @param depositAmount 抵押金额
     */
    public void setDepositAmount(BigInteger depositAmount , BigDecimal percent) {
        this.depositAmount = depositAmount;
        BigDecimal bigDecimalValue =new BigDecimal(depositAmount);
        this.availableAmount =percent.multiply(bigDecimalValue).toBigInteger();
        this.lockedAmount=this.depositAmount.subtract(this.availableAmount);

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

    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = availableAmount;
    }

    public BigInteger getLockedAmount() {
        return lockedAmount;
    }

    public void setLockedAmount(BigInteger lockedAmount) {
        this.lockedAmount = lockedAmount;
    }

    @Override
    public String toString(){
        return "{depositNumber:"+depositNumber+",depositHeight:"+depositHeight
                +",miningAddress:"+miningAddress+",depositAmount:"+depositAmount
                +",availableAmount:"+availableAmount+",lockedAmount:"+lockedAmount+"}";
    }
}
