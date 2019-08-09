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
     * 抵押详细信息列表
     */
    private Map<Long,DepositDetailInfo> depositDetailInfos =new HashMap<Long,DepositDetailInfo>();

    public DepositInfo(){
        this.depositTotalAmount=BigInteger.ZERO;
        this.depositCount=0;
    }

    public DepositInfo(DepositInfo info){
        this.depositorAddress=info.depositorAddress;
        this.depositTotalAmount=info.depositTotalAmount;
        this.depositAvailableTotalAmount=info.depositAvailableTotalAmount;
        this.depositLockedTotalAmount=info.depositLockedTotalAmount;
        this.depositCount=info.depositCount;
        this.depositDetailInfos=info.depositDetailInfos;
    }

    public BigInteger getDepositTotalAmount() {
        return depositTotalAmount;
    }

    /**
     * 设置抵押总金额的同时，计算锁定总金额和可用总金额
     * @param depositTotalAmount 抵押总金额
     */
    public void setDepositTotalAmount(BigInteger depositTotalAmount) {
        this.depositTotalAmount = depositTotalAmount;
        BigDecimal bigDecimalValue =new BigDecimal(depositTotalAmount);
        this.depositLockedTotalAmount = PocmUtil.LOCKED_PERCENT.multiply(bigDecimalValue).toBigInteger();
        this.depositAvailableTotalAmount=this.depositTotalAmount.subtract(this.depositLockedTotalAmount);
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

    public Map<Long, DepositDetailInfo> getDepositDetailInfos() {
        return depositDetailInfos;
    }

    public void setDepositDetailInfos(Map<Long, DepositDetailInfo> depositDetailInfos) {
        this.depositDetailInfos = depositDetailInfos;
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

    /**
     * 根据抵押编号获取抵押详细信息
     * @param depositNumber
     * @return
     */
    public DepositDetailInfo getDepositDetailInfoByNumber(long depositNumber){
        DepositDetailInfo info=depositDetailInfos.get(depositNumber);
        require(info != null, "未找到此抵押编号的抵押详细信息");
        return info;
    }

    /**
     *
     * @param depositNumber
     */
    public void removeDepositDetailInfoByNumber(long depositNumber){
        depositDetailInfos.remove(depositNumber);
    }

    public void clearDepositDetailInfos(){
        depositDetailInfos.clear();
        depositCount=0;
        depositTotalAmount=BigInteger.ZERO;
    }


    @Override
    public String toString(){
        return  "{depositTotalAmount:"+depositTotalAmount+",depositAvailableTotalAmount:"+depositAvailableTotalAmount
                +",depositLockedTotalAmount:"+depositLockedTotalAmount+",depositorAddress:"+depositorAddress
                +",depositCount:"+depositCount+",depositDetailInfos:"+convertMapToString()+"}";
    }

    private  String convertMapToString(){
        String detailinfo ="{";
        String temp="";
        for (Long key : depositDetailInfos.keySet()) {
            DepositDetailInfo detailInfo=  depositDetailInfos.get(key);
            temp =detailInfo.toString();
            detailinfo=detailinfo+temp+",";
        }
        detailinfo=detailinfo.substring(0,detailinfo.length()-1);
        detailinfo=detailinfo+"}";

        return detailinfo;
    }

}
