package io.nuls.pocm.contract.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 奖励周期内的抵押信息
 * @author: Long
 * @date: 2019-04-19
 */
public class RewardCycleInfo {

    //奖励周期
    private int rewardingCylce;

    //当前奖励周期的单价（最小单位）
    private BigInteger currentPrice;
    //抵押总量
    private BigInteger availableDepositAmount;

    //距离上次统计相差的奖励周期数
    private int differCycleValue;

    //奖励基数=currentPrice/availableDepositAmount * differCycleValue +上一次统计的奖励周期rewardBase值
    private BigDecimal rewardBase;

    public BigInteger getAvailableDepositAmount() {
        return availableDepositAmount;
    }

    public void setAvailableDepositAmount(BigInteger availableDepositAmount) {
        this.availableDepositAmount = availableDepositAmount;
    }

    public int getRewardingCylce() {
        return rewardingCylce;
    }

    public void setRewardingCylce(int rewardingCylce) {
        this.rewardingCylce = rewardingCylce;
    }

    public int getDifferCycleValue() {
        return differCycleValue;
    }

    public void setDifferCycleValue(int differCycleValue) {
        this.differCycleValue = differCycleValue;
    }

    public BigInteger getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigInteger currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getRewardBase() {
        return rewardBase;
    }

    public void setRewardBase(BigDecimal rewardBase) {
        this.rewardBase = rewardBase;
    }

    @Override
    public String toString(){
        return "{rewardingCylce:"+rewardingCylce+",currentPrice:"+currentPrice.toString()+",availableDepositAmount:"+availableDepositAmount
                +",differCycleValue:"+differCycleValue+",rewardBase:"+rewardBase.toPlainString()+"}";
    }
}
