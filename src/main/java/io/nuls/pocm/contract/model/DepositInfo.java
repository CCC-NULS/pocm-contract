package io.nuls.pocm.contract.model;

import io.nuls.pocm.contract.util.PocmUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.require;

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
     * @param depositTotalAmount 抵押总金额
     */
    public void setDepositTotalAmount(BigInteger depositTotalAmount, BigDecimal percent) {
        this.depositTotalAmount = depositTotalAmount;
        BigDecimal bigDecimalValue =new BigDecimal(depositTotalAmount);
        this.depositAvailableTotalAmount = percent.multiply(bigDecimalValue).toBigInteger();
        this.depositLockedTotalAmount=this.depositTotalAmount.subtract(this.depositAvailableTotalAmount);
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
