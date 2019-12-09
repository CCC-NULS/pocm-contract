package io.nuls.pocm.contract.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 抵押信息
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositInfo {

    //抵押者地址
    private String  depositorAddress;

    // 抵押总金额
    private BigInteger depositTotalAmount;

    //抵押可用总金额 =抵押总金额*0.9
    private BigInteger depositAvailableTotalAmount;

    //抵押锁定总金额 =抵押总金额*0.1
    private BigInteger depositLockedTotalAmount;

    //抵押笔数
    private int depositCount;

    /**
     * 抵押详细信息
     */
    private DepositDetailInfo depositDetailInfo;

    public DepositInfo(){
        this.depositTotalAmount=BigInteger.ZERO;
        this.depositCount=0;
    }

    public BigInteger getDepositTotalAmount() {
        return depositTotalAmount;
    }

    /**
     * 设置抵押总金额的同时，计算锁定总金额和可用总金额
     * @param incDepositTotalAmount 抵押总金额
     */
    public void setDepositTotalAmount(BigInteger incDepositTotalAmount, BigDecimal percent) {
        this.depositTotalAmount = this.depositTotalAmount.add(incDepositTotalAmount);
        BigDecimal bigDecimalValue =new BigDecimal(incDepositTotalAmount);
        BigInteger incAvailableTotalAmount=percent.multiply(bigDecimalValue).toBigInteger();
        this.depositAvailableTotalAmount =this.depositAvailableTotalAmount.add(incAvailableTotalAmount);
        this.depositLockedTotalAmount=this.depositLockedTotalAmount.add(incDepositTotalAmount.subtract(incAvailableTotalAmount));
    }


    /**
     * 更新抵押总金额
     * @param redDepositTotalAmount  退出的总金额
     * @param redAvailableTotalAmount 退出的可用抵押金额
     * @param redLockedTotalAmount  退出的锁定抵押金额
     */
    public void updateDepositTotalAmount(BigInteger redDepositTotalAmount, BigInteger redAvailableTotalAmount, BigInteger redLockedTotalAmount) {
        this.depositTotalAmount = this.depositTotalAmount.subtract(redDepositTotalAmount);
        this.depositAvailableTotalAmount =this.depositAvailableTotalAmount.subtract(redAvailableTotalAmount);
        this.depositLockedTotalAmount=this.depositLockedTotalAmount.add(redLockedTotalAmount);
    }



    public BigInteger getDepositAvailableTotalAmount() {
        return depositAvailableTotalAmount;
    }

    public void setDepositAvailableTotalAmount(BigInteger depositAvailableTotalAmount) {
        this.depositAvailableTotalAmount = depositAvailableTotalAmount;
    }

    public BigInteger getDepositLockedTotalAmount() {
        return depositLockedTotalAmount;
    }

    public void setDepositLockedTotalAmount(BigInteger depositLockedTotalAmount) {
        this.depositLockedTotalAmount = depositLockedTotalAmount;
    }

    public DepositDetailInfo getDepositDetailInfo() {
        return depositDetailInfo;
    }

    public void setDepositDetailInfo(DepositDetailInfo depositDetailInfo) {
        this.depositDetailInfo = depositDetailInfo;
    }

    public int getDepositCount() {
        return depositCount;
    }

    public void setDepositCount(int depositCount) {
        this.depositCount = depositCount;
    }


    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }

    public void clearDepositDetailInfos(){
        depositCount=0;
        depositTotalAmount=BigInteger.ZERO;
    }

    @Override
    public String toString(){
        return  "{depositTotalAmount:"+depositTotalAmount+",depositAvailableTotalAmount:"+depositAvailableTotalAmount
                +",depositLockedTotalAmount:"+depositLockedTotalAmount+",depositorAddress:"+depositorAddress
                +",depositCount:"+depositCount+",depositDetailInfo:"+depositDetailInfo.toString()+"}";
    }

}
