/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.pocm.contract;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Block;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.pocm.contract.event.DepositInfoEvent;
import io.nuls.pocm.contract.event.ErrorEvent;
import io.nuls.pocm.contract.event.MiningInfoEvent;
import io.nuls.pocm.contract.manager.ConsensusManager;
import io.nuls.pocm.contract.manager.TotalDepositManager;
import io.nuls.pocm.contract.manager.deposit.DepositOthersManager;
import io.nuls.pocm.contract.model.*;
import io.nuls.pocm.contract.ownership.Ownable;
import io.nuls.pocm.contract.token.PocmToken;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.sdk.Utils.*;
import static io.nuls.pocm.contract.util.PocmUtil.*;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class Pocm extends Ownable implements Contract {
    private final BigDecimal HLAVING = new BigDecimal("2");
    // 合约创建高度
    private final long createHeight;
    // 初始价格，每个奖励周期所有的NULS抵押数平分XX个token
    private BigDecimal initialPrice;

    // 奖励发放周期（参数类型为数字，每过XXXX块发放一次）
    private int awardingCycle;
    // 奖励减半周期（可选参数，若选择，则参数类型为数字，每XXXXX块奖励减半）
    private int rewardHalvingCycle;
    // 最低抵押na数量(1亿个na等于1个NULS）
    private BigInteger minimumDeposit;
    // 最短锁定区块（参数类型为数字，XXXXX块后才可退出抵押）
    private int minimumLocked;
    // 最大抵押地址数量（可选参数）
    private int maximumDepositAddressCount;

    //用户抵押信息(key为抵押者地址）
    private Map<String, DepositInfo> depositUsers = new HashMap<String, DepositInfo>();

    // 用户挖矿信息(key为接收挖矿Token地址）
    private Map<String, MiningInfo> mingUsers = new HashMap<String, MiningInfo>();

    // 总抵押金额管理器
    private TotalDepositManager totalDepositManager;
    // 总抵押地址数量
    private int totalDepositAddressCount;

    //每个奖励周期的抵押金额索引，k-v：奖励周期-List序号
    private Map<Integer, Integer> totalDepositIndex = new LinkedHashMap<Integer, Integer>();
    //抵押金额列表，与索引表联合使用
    private List<RewardCycleInfo> totalDepositList = new LinkedList<RewardCycleInfo>();
    //上一次抵押数量有变动的奖励周期
    private int lastCalcCycle = 0;

    //下一次奖励减半的高度
    private long nextRewardHalvingHeight = 0L;

    // 当前价格，当前奖励周期所有的NULS抵押数平分XX个token
    private BigDecimal currentPrice;

    private static long NUMBER = 1L;

    // 共识管理器
    private ConsensusManager consensusManager;

    //发放token的合约地址
    private Address tokenContractAddress;

    //token的名字
    private  String name;
    //token的symbol
    private String symbol;
    //token的精度
    private int decimals;

    //token的总分配量
    private BigInteger totalAllocation;

    //已经分配的Token数量
    private BigInteger allocationAmount=BigInteger.ZERO;

    private  boolean isGetTotal=false;
    //是否接受抵押
    private boolean isAcceptDeposit=false;
    //是否开启合约共识功能
    private boolean openConsensus=false;

    public Pocm(@Required String tokenAddress, @Required BigDecimal price, @Required int awardingCycle,
                @Required BigDecimal minimumDepositNULS, @Required int minimumLocked, @Required boolean openConsensus,
                String rewardHalvingCycle, String maximumDepositAddressCount) {
        tokenContractAddress = new Address(tokenAddress);
        // 检查 price 小数位不得大于decimals
        require(price.compareTo(BigDecimal.ZERO) > 0, "价格应该大于0");
        decimals= Integer.parseInt(tokenContractAddress.callWithReturnValue("decimals","",null,BigInteger.ZERO));
        require(checkMaximumDecimals(price, decimals), "最多" + decimals + "位小数");
        require(minimumLocked > 0, "最短锁定区块值应该大于0");
        require(awardingCycle > 0, "奖励发放周期应该大于0");
        int rewardHalvingCycleForInt = 0;
        int maximumDepositAddressCountForInt = 0;
        if (rewardHalvingCycle != null && rewardHalvingCycle.trim().length() > 0) {
            require(canConvertNumeric(rewardHalvingCycle.trim(), String.valueOf(Integer.MAX_VALUE)), "奖励减半周期输入不合法，应该输入小于2147483647的数字字符");
            rewardHalvingCycleForInt = Integer.parseInt(rewardHalvingCycle.trim());
            require(rewardHalvingCycleForInt >= 0, "奖励减半周期应该大于等于0");
        }
        if (maximumDepositAddressCount != null && maximumDepositAddressCount.trim().length() > 0) {
            require(canConvertNumeric(maximumDepositAddressCount.trim(), String.valueOf(Integer.MAX_VALUE)), "最低抵押数量输入不合法，应该输入小于2147483647的数字字符");
            maximumDepositAddressCountForInt = Integer.parseInt(maximumDepositAddressCount.trim());
            require(maximumDepositAddressCountForInt >= 0, "最低抵押数量应该大于等于0");
        }
        this.createHeight = Block.number();
        this.totalDepositAddressCount = 0;
        this.initialPrice = price;
        this.awardingCycle = awardingCycle;
        this.rewardHalvingCycle = rewardHalvingCycleForInt;
        this.minimumDeposit = toNa(minimumDepositNULS);
        this.minimumLocked = minimumLocked;
        this.maximumDepositAddressCount = maximumDepositAddressCountForInt;
        this.nextRewardHalvingHeight = this.createHeight + this.rewardHalvingCycle;
        this.currentPrice = price;

        name= tokenContractAddress.callWithReturnValue("name","",null,BigInteger.ZERO);
        symbol= tokenContractAddress.callWithReturnValue("symbol","",null,BigInteger.ZERO);

        totalDepositManager = new TotalDepositManager();
        if(openConsensus) {
            openConsensus();
        }
    }

    @Override
    public void _payable() {
        revert("Do not accept direct transfers.");
    }

    /**
     * 共识奖励收益处理
     * 创建的节点的100%佣金比例，收益地址只有当前合约地址
     * (底层系统调用，不能被任何人调用)
     *
     * @param args 区块奖励地址明细 eg. [[address, amount], [address, amount], ...]
     */
    @Override
    @Payable
    public void _payable(String[][] args) {
        consensusManager._payable(args);
    }

    /**
     * 开启共识功能
     */
    public void openConsensus() {
        onlyOwner();
        require(!openConsensus, "已开启共识功能");
        this.openConsensus = true;
        consensusManager = new ConsensusManager();
        totalDepositManager.setOpenConsensus(true);
        totalDepositManager.setConsensusManager(consensusManager);
    }

    /**
     * 添加其他节点的共识信息
     * @param agentHash 其他共识节点的hash
     */
    public void addOtherAgent(String agentHash) {
        onlyOwner();
        require(openConsensus, "未开启共识功能");
        consensusManager.addOtherAgent(agentHash);
    }

    /**
     * 合约拥有者委托共识节点
     */
    public void depositConsensusManuallyByOwner() {
        onlyOwner();
        require(openConsensus, "未开启共识功能");
        consensusManager.depositManually();
    }

    /**
     * 合约拥有者获取共识奖励金额
     */
    public void transferConsensusRewardByOwner() {
        onlyOwner();
        require(openConsensus, "未开启共识功能");
        consensusManager.transferConsensusReward(owner);
    }

    /**
     * 为自己抵押获取Token
     *
     * @return
     */
    @Payable
    public void depositForOwn() {
        require(isAllocationToken(),"此POCM合约未预分配Token,暂不接受抵押");
        require(isAcceptDeposit(),"预分配的Token数量已经奖励完毕，不再接受抵押");
        String userStr = Msg.sender().toString();
        DepositInfo info = depositUsers.get(userStr);
        if (info == null) {
            if (maximumDepositAddressCount > 0) {
                require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
            }
            info = new DepositInfo();
            depositUsers.put(userStr, info);
            totalDepositAddressCount += 1;
        }
        BigInteger value = Msg.value();
        long currentHeight = Block.number();
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + minimumDeposit);
        long depositNumber = NUMBER++;

        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(userStr);
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value));
        info.setDepositCount(info.getDepositCount() + 1);

        //将抵押数加入队列中
        this.putDepositToMap(detailInfo.getAvailableAmount(), currentHeight);


        //初始化挖矿信息
        initMingInfo(currentHeight, userStr, userStr, depositNumber);
        totalDepositManager.add(detailInfo.getAvailableAmount());
        emit(new DepositInfoEvent(info));
    }

    /**
     * 为他人抵押挖取Token
     *
     * @param miningAddress 指定挖出Token的接受地址
     * @return
     */
    @Payable
    public void depositForOther(@Required Address miningAddress) {
        require(isAllocationToken(),"此POCM合约未预分配Token,暂不接受抵押");
        require(isAcceptDeposit(),"预分配的Token数量已经奖励完毕，不再接受抵押");
        String userStr = Msg.sender().toString();
        DepositInfo info = depositUsers.get(userStr);
        if (info == null) {
            if (maximumDepositAddressCount > 0) {
                require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
            }
            info = new DepositInfo();
            depositUsers.put(userStr, info);
            totalDepositAddressCount += 1;
        }

        BigInteger value = Msg.value();
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + minimumDeposit);
        long depositNumber = NUMBER++;
        long currentHeight = Block.number();
        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(miningAddress.toString());
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value));
        info.setDepositCount(info.getDepositCount() + 1);

        //将抵押数加入队列中
        this.putDepositToMap(detailInfo.getAvailableAmount(), currentHeight);

        //初始化挖矿信息
        initMingInfo(currentHeight, miningAddress.toString(), userStr, depositNumber);
        totalDepositManager.add(detailInfo.getAvailableAmount());
        emit(new DepositInfoEvent(info));
    }

    /**
     * 退出抵押挖矿，当抵押编号为0时退出全部抵押
     *
     * @param number 抵押编号
     * @return
     */
    public void quit(String number) {
        long currentHeight = Block.number();
        long depositNumber = 0;
        if (number != null && number.trim().length() > 0) {
            require(canConvertNumeric(number.trim(), String.valueOf(Long.MAX_VALUE)), "抵押编号输入不合法，应该输入数字字符");
            depositNumber = Long.valueOf(number.trim());
        }
        Address user = Msg.sender();
        String userString = user.toString();
        DepositInfo depositInfo = getDepositInfo(userString);
        // 发放奖励
        this.receive(depositInfo);
        BigInteger depositAvailableTotalAmount;
        BigInteger depositTotalAmount;
        MiningInfo miningInfo;

        //表示退出全部的抵押
        if (depositNumber == 0) {
            miningInfo = mingUsers.get(depositInfo.getDepositorAddress());
            if (miningInfo == null) {
                miningInfo = new MiningInfo();
            }
            long result = checkAllDepositLocked(depositInfo);
            require(result == -1, "挖矿的NULS没有全部解锁");
            depositAvailableTotalAmount = depositInfo.getDepositAvailableTotalAmount();
            depositTotalAmount=depositInfo.getDepositTotalAmount();
            Map<Long, DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();
            delMingInfo(depositDetailInfos);
            //从队列中退出抵押金额
            for (Long key : depositDetailInfos.keySet()) {
                DepositDetailInfo detailInfo = depositDetailInfos.get(key);
                this.quitDepositToMap(detailInfo.getAvailableAmount(), currentHeight, detailInfo.getDepositHeight());
            }
            depositInfo.clearDepositDetailInfos();
        } else {
            //退出某一次抵押
            DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfoByNumber(depositNumber);
            long unLockedHeight = checkDepositLocked(detailInfo);
            require(unLockedHeight == -1, "挖矿锁定中, 解锁高度是 " + unLockedHeight);
            //删除挖矿信息
            miningInfo = mingUsers.get(detailInfo.getMiningAddress());
            miningInfo.removeMiningDetailInfoByNumber(depositNumber);
            if (miningInfo.getMiningDetailInfos().size() == 0) {
                mingUsers.remove(detailInfo.getMiningAddress());
            }
            depositInfo.removeDepositDetailInfoByNumber(depositNumber);
            // 退押金
            depositAvailableTotalAmount = detailInfo.getAvailableAmount();
            depositTotalAmount=detailInfo.getDepositAmount();
            depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(detailInfo.getDepositAmount()));
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);
            //从队列中退出抵押金额
            this.quitDepositToMap(depositAvailableTotalAmount, currentHeight, detailInfo.getDepositHeight());
        }
        boolean isEnoughBalance = totalDepositManager.subtract(depositAvailableTotalAmount);
        require(isEnoughBalance, "余额不足以退还押金，请联系项目方");
        if (depositInfo.getDepositDetailInfos().size() == 0) {
            totalDepositAddressCount -= 1;
            //TODO 退出后是否保留该账户的挖矿记录
            depositUsers.remove(userString);
        }
        emit(new MiningInfoEvent(miningInfo));
        user.transfer(depositTotalAmount);
    }

    /**
     * 领取奖励,领取为自己抵押挖矿的Token
     */
    public void receiveAwards() {
        Address user = Msg.sender();
        MiningInfo miningInfo = mingUsers.get(user.toString());
        require(miningInfo != null, "没有为自己抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(user.toString());
        this.receive(depositInfo);
        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     * 由挖矿接收地址发起领取奖励;当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     *
     * @return
     */
    public void receiveAwardsForMiningAddress() {
        List<String> alreadyReceive = new ArrayList<String>();
        Address user = Msg.sender();
        MiningInfo info = mingUsers.get(user.toString());
        require(info != null, "没有替" + user.toString() + "用户抵押挖矿的挖矿信息");
        Map<Long, MiningDetailInfo> detailInfos = info.getMiningDetailInfos();
        for (Long key : detailInfos.keySet()) {
            MiningDetailInfo detailInfo = detailInfos.get(key);
            if (!alreadyReceive.contains(detailInfo.getDepositorAddress())) {
                DepositInfo depositInfo = getDepositInfo(detailInfo.getDepositorAddress());
                this.receive(depositInfo);
                alreadyReceive.add(detailInfo.getDepositorAddress());
            }
        }
        emit(new MiningInfoEvent(info));
    }

    /**
     * 合约创建者清空剩余余额
     */
    public void clearContract() {
        onlyOwner();
        BigInteger balance = Msg.address().balance();
        require(balance.compareTo(ONE_NULS) <= 0, "余额不得大于1NULS");
        require(balance.compareTo(BigInteger.ZERO) >0, "余额为零，无需清空");
        contractCreator.transfer(balance);
    }

    /**
     * 查找用户挖矿信息
     */
    @View
    public MiningInfo getMingInfo(@Required Address address) {
        return getMiningInfo(address.toString());
    }

    /**
     * 查找用户的抵押信息
     *
     * @return
     */
    @View
    public DepositInfo getDepositInfo(@Required Address address) {
        return getDepositInfo(address.toString());
    }


    /**
     * 当前价格
     */
    @View
    public String currentPrice() {
        int size = totalDepositList.size();
        if (size > 0) {
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(size - 1);
            BigInteger intAmount = cycleInfoTmp.getAvailableDepositAmount();
            if (intAmount.compareTo(BigInteger.ZERO) == 0) {
                return "Unknown";
            }
            String amount = toNuls(intAmount).toString();
            BigDecimal bigAmount = new BigDecimal(amount);
            BigDecimal currentPrice = cycleInfoTmp.getCurrentPrice().divide(bigAmount, decimals(), BigDecimal.ROUND_DOWN);
            return currentPrice.toPlainString() + " " + name() + "/NULS .";
        } else {
            return "Unknown";
        }
    }

    /**
     * 单价的精度不能超过定义的精度
     *
     * @param price    单价
     * @param decimals 精度
     * @return
     */
    private static boolean checkMaximumDecimals(BigDecimal price, int decimals) {
        BigInteger a = price.movePointRight(decimals).toBigInteger().multiply(BigInteger.TEN);
        BigInteger b = price.movePointRight(decimals + 1).toBigInteger();
        if (a.compareTo(b) != 0) {
            return false;
        }
        return true;
    }


    /**
     * 根据挖矿地址从队列中获取挖矿信息
     *
     * @param userStr
     * @return
     */
    private MiningInfo getMiningInfo(String userStr) {
        MiningInfo miningInfo = mingUsers.get(userStr);
        require(miningInfo != null, "没有为此用户挖矿的挖矿信息");
        return miningInfo;
    }

    /**
     * 根据抵押地址从队列中获取抵押信息
     *
     * @param userStr
     * @return
     */
    private DepositInfo getDepositInfo(String userStr) {
        DepositInfo depositInfo = depositUsers.get(userStr);
        require(depositInfo != null, "此用户未参与抵押");
        return depositInfo;
    }

    /**
     * 检查抵押是否在锁定中
     *
     * @param depositInfo
     * @return
     */
    private long checkAllDepositLocked(DepositInfo depositInfo) {
        long result;
        Map<Long, DepositDetailInfo> infos = depositInfo.getDepositDetailInfos();
        for (Long key : infos.keySet()) {
            result = checkDepositLocked(infos.get(key));
            if (result != -1) {
                return result;
            }
        }
        return -1;
    }

    /**
     * 检查抵押是否在锁定中
     *
     * @param detailInfo
     * @return
     */
    private long checkDepositLocked(DepositDetailInfo detailInfo) {
        long currentHeight = Block.number();
        long unLockedHeight = detailInfo.getDepositHeight() + minimumLocked + 1;
        if (unLockedHeight > currentHeight) {
            // 锁定中
            return unLockedHeight;
        }
        //已解锁
        return -1;
    }

    //public void transferForTest(String to,String value){
    //    String[][] args = new String[2][];
    //    args[0]=new String[]{to};
    //    args[1]=new String[]{value};
    //    tokenContractAddress.call("transfer","",args,BigInteger.ZERO);
    //}

    /**
     * 领取奖励
     *
     * @param depositInfo
     * @return 返回请求地址的挖矿信息
     */
    private void receive(DepositInfo depositInfo) {
        Map<String, BigInteger> mingResult = new HashMap<String, BigInteger>();
        //预分配的Token已经奖励完，不再进行奖励计算
        if(allocationAmount.compareTo(totalAllocation)==0){
            return;
        }
        // 奖励计算, 计算每次挖矿的高度是否已达到奖励减半周期的范围，若达到，则当次奖励减半，以此类推
        BigInteger thisMiningAmount = this.calcMining(depositInfo, mingResult);

        Set<String> set = new HashSet<String>(mingResult.keySet());
        for (String address : set) {
            Address user = new Address(address);
            BigInteger mingValue = mingResult.get(address);
          //  addBalance(user, mingValue);
            if(mingValue.compareTo(BigInteger.ZERO)>0){
              //  user.transfer(mingValue);
                String[][] args = new String[2][];
                args[0]=new String[]{address.toString()};
                args[1]=new String[]{mingValue.toString()};
                tokenContractAddress.call("transfer","",args,BigInteger.ZERO);
                allocationAmount=allocationAmount.add(mingValue);
            }
         //   emit(new TransferEvent(null, user, mingValue));
        }

      //  this.setTotalSupply(this.getTotalSupply().add(thisMining));
    }

    /**
     * 计算奖励数额
     *
     * @param depositInfo
     * @param mingResult
     * @return
     */
    private BigInteger calcMining(DepositInfo depositInfo, Map<String, BigInteger> mingResult) {
        BigInteger mining = BigInteger.ZERO;
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight);
        List<Long> calcRewardIds= new ArrayList<Long>();
        Map<Long,BigInteger> rewardForKey =new HashMap<Long, BigInteger>();
        Map<Long, DepositDetailInfo> detailInfos = depositInfo.getDepositDetailInfos();
        for (Long key : detailInfos.keySet()) {
            DepositDetailInfo detailInfo = detailInfos.get(key);
            BigInteger miningTmp = BigInteger.ZERO;
            MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            //说明未到领取奖励的高度
            if (nextStartMiningCycle > currentRewardCycle) {
                continue;
            }
            BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
            BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
            miningTmp = miningTmp.add(availableDepositAmountNULS.multiply(sumPrice).toBigInteger());

            mining = mining.add(miningTmp);

            calcRewardIds.add(key);
            rewardForKey.put(key,miningTmp);
        }

        //检查奖励的Token总额是否超过的预分配的Token数量
        BigInteger allocationAmountTmp=allocationAmount.add(mining);
        BigDecimal precent=BigDecimal.ONE;
        if(allocationAmountTmp.compareTo(totalAllocation)>0){
            isAcceptDeposit=false;
            //超过总Token，按比例分配
            BigInteger remainAmount=totalAllocation.subtract(allocationAmount);
            BigDecimal b_remainAmount= new BigDecimal(remainAmount);
            BigDecimal b_thisMining =new BigDecimal(mining);
            precent =b_remainAmount.divide(b_thisMining,decimals(),BigDecimal.ROUND_DOWN);
            allocationAmount=totalAllocation;
        }else{
            allocationAmount=allocationAmountTmp;
        }

        BigInteger miningNew = BigInteger.ZERO;
        for(Long key:calcRewardIds){
            DepositDetailInfo detailInfo = detailInfos.get(key);
            MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            BigInteger mingValueOld=rewardForKey.get(key);
            BigDecimal b_mingValue =new BigDecimal(mingValueOld);
            BigInteger mingValueNew =precent.multiply(b_mingValue).toBigInteger();
            mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(mingValueNew));
            mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount() + currentRewardCycle - nextStartMiningCycle + 1);
            mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
            miningInfo.setTotalMining(miningInfo.getTotalMining().add(mingValueNew));
            miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(mingValueNew));

            if (mingResult.containsKey(mingDetailInfo.getReceiverMiningAddress())) {
                mingValueNew = mingResult.get(mingDetailInfo.getReceiverMiningAddress()).add(mingValueNew);
            }
            mingResult.put(mingDetailInfo.getReceiverMiningAddress(), mingValueNew);
            miningNew.add(mingValueNew);
        }
        return miningNew;
    }

    /**
     * 删除挖矿信息
     *
     * @param infos
     */
    private void delMingInfo(Map<Long, DepositDetailInfo> infos) {
        for (Long key : infos.keySet()) {
            DepositDetailInfo detailInfo = infos.get(key);
            MiningInfo miningInfo = mingUsers.get(detailInfo.getMiningAddress());
            miningInfo.removeMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            if (miningInfo.getMiningDetailInfos().size() == 0) {
                mingUsers.remove(detailInfo.getMiningAddress());
            }
        }
    }

    /**
     * 初始化挖矿信息
     *
     * @param miningAddress
     * @param depositorAddress
     * @param depositNumber
     * @return
     */
    private void initMingInfo(long currentHeight, String miningAddress, String depositorAddress, long depositNumber) {
        MiningDetailInfo mingDetailInfo = new MiningDetailInfo(miningAddress, depositorAddress, depositNumber);
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 2);
        MiningInfo mingInfo = mingUsers.get(miningAddress);
        //该Token地址为第一次挖矿
        if (mingInfo == null) {
            mingInfo = new MiningInfo();
            mingInfo.getMiningDetailInfos().put(depositNumber, mingDetailInfo);
            mingUsers.put(miningAddress, mingInfo);
        } else {
            mingInfo.getMiningDetailInfos().put(depositNumber, mingDetailInfo);
        }
    }


    /**
     * 在加入抵押时将抵押金额加入队列中
     *
     * @param depositValue
     * @param currentHeight
     */
    private void putDepositToMap(BigInteger depositValue, long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //检查下一个奖励周期的总抵押数是否在队列中
        if (!totalDepositIndex.containsKey(currentCycle + 1)) {
            moveLastDepositToCurrentCycle(currentHeight + this.awardingCycle);
        }
        int putCycle = currentCycle + 2;

        boolean isContainsKey = totalDepositIndex.containsKey(putCycle);
        RewardCycleInfo cycleInfo = new RewardCycleInfo();
        if (!isContainsKey) {
            //计算奖励减半
            long rewardingHeight = putCycle * this.awardingCycle + this.createHeight;
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= rewardingHeight) {
                this.currentPrice = this.currentPrice.divide(this.HLAVING, decimals(), BigDecimal.ROUND_DOWN);
                this.nextRewardHalvingHeight += this.rewardHalvingCycle;
            }

            if (this.lastCalcCycle == 0) {
                cycleInfo.setAvailableDepositAmount(depositValue);
                cycleInfo.setRewardingCylce(putCycle);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);
                totalDepositList.add(cycleInfo);
            } else {
                RewardCycleInfo lastCycleInfo = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
                cycleInfo.setAvailableDepositAmount(depositValue.add(lastCycleInfo.getAvailableDepositAmount()));
                cycleInfo.setRewardingCylce(putCycle);
                cycleInfo.setDifferCycleValue(putCycle - lastCycleInfo.getRewardingCylce());
                cycleInfo.setCurrentPrice(this.currentPrice);
                totalDepositList.add(cycleInfo);
            }
            totalDepositIndex.put(putCycle, totalDepositList.size() - 1);
            this.lastCalcCycle = putCycle;
        } else {
            int alreadyTotalDepositIndex = totalDepositIndex.get(putCycle);
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(alreadyTotalDepositIndex);
            cycleInfoTmp.setAvailableDepositAmount(depositValue.add(cycleInfoTmp.getAvailableDepositAmount()));
        }
    }

    /**
     * 将当前高度的奖励周期加入队列
     *
     * @param currentHeight
     */
    private void moveLastDepositToCurrentCycle(long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //若当前高度的奖励周期在队列中，则直接退出此方法
        if (totalDepositIndex.containsKey(currentCycle)) {
            return;
        } else {
            //当前高度已经达到奖励减半高度,将所有的减半周期高度对于的奖励高度加入队列
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= currentHeight) {
                this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, currentHeight);
            }
        }

        //此时再检查是否当前高度的奖励周期在队列中
        if (!totalDepositIndex.containsKey(currentCycle)) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            RewardCycleInfo cycleInfoTmp;
            if (totalDepositList.size() > 0) {
                //取队列中最后一个奖励周期的信息
                cycleInfoTmp = totalDepositList.get(totalDepositList.size() - 1);
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount());
                cycleInfo.setDifferCycleValue(currentCycle - cycleInfoTmp.getRewardingCylce());
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);
            } else {
                cycleInfo.setAvailableDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);
            }
            lastCalcCycle = currentCycle;
            totalDepositList.add(cycleInfo);
            totalDepositIndex.put(currentCycle, totalDepositList.size() - 1);
        }
    }

    /**
     * 抵押数额没有变动的情况下，将奖励减半周期所在高度的奖励周期抵押数加入队列
     */
    private void moveLastDepositToHalvingCycle(long startRewardHalvingHeight, long currentHeight) {
        int rewardingCycle = this.lastCalcCycle;
        long height = startRewardHalvingHeight;
        while (height <= currentHeight) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            this.currentPrice = this.currentPrice.divide(this.HLAVING, decimals(), BigDecimal.ROUND_DOWN);
            rewardingCycle = this.calcRewardCycle(height);
            boolean isContainsKey = totalDepositIndex.containsKey(rewardingCycle);
            if (isContainsKey) {
                continue;
            }
            if (this.lastCalcCycle != 0) {
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount());
                cycleInfo.setDifferCycleValue(rewardingCycle - cycleInfoTmp.getRewardingCylce());
            } else {
                //第一次进行抵押操作
                cycleInfo.setAvailableDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
            }
            cycleInfo.setRewardingCylce(rewardingCycle);
            cycleInfo.setCurrentPrice(this.currentPrice);
            totalDepositList.add(cycleInfo);
            totalDepositIndex.put(rewardingCycle, totalDepositList.size() - 1);
            height += this.rewardHalvingCycle;
            this.lastCalcCycle = rewardingCycle;
        }
        this.nextRewardHalvingHeight = height;
    }

    /**
     * 退出抵押时从队列中退出抵押金额
     *
     * @param depositValue
     * @param currentHeight
     * @param depositHeight
     */
    private void quitDepositToMap(BigInteger depositValue, long currentHeight, long depositHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        int depositCycle = this.calcRewardCycle(depositHeight);
        if (currentCycle == depositCycle) {
            //加入抵押和退出抵押在同一个奖励周期，更新下一个奖励周期的总抵押数
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(currentCycle + 2));
            cycleInfoTmp.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));
        } else {
            //加入抵押和退出抵押不在同一个奖励周期,则更新当前奖励周期的总抵押数
            int operCycle = currentCycle + 1;
            boolean isContainsKey = totalDepositIndex.containsKey(operCycle);
            //待操作的奖励周期已经计算了总抵押数
            if (isContainsKey) {
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(operCycle));
                cycleInfoTmp.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));
            } else {
                //当前高度已经达到奖励减半高度,将所有的减半周期高度对于的奖励高度加入队列
                long nextHeight = currentHeight + this.awardingCycle;
                if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= nextHeight) {
                    this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, nextHeight);
                }

                RewardCycleInfo cycleInfo = new RewardCycleInfo();

                //取队列中最后一个奖励周期的信息
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositList.size() - 1);
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));
                cycleInfo.setDifferCycleValue(operCycle - cycleInfoTmp.getRewardingCylce());
                cycleInfo.setCurrentPrice(currentPrice);
                cycleInfo.setRewardingCylce(operCycle);
                totalDepositList.add(cycleInfo);

                totalDepositIndex.put(operCycle, totalDepositList.size() - 1);
                this.lastCalcCycle = operCycle;
            }
        }

    }

    /**
     * 从指定的奖励周期开始计算奖励价格之和
     *
     * @param startCycle
     * @return
     */
    private BigDecimal calcPriceBetweenCycle(int startCycle) {
        BigDecimal sumPrice = BigDecimal.ZERO;
        BigDecimal sumPriceForRegin = BigDecimal.ZERO;
        int startIndex = totalDepositIndex.get(startCycle - 1) + 1;
        for (int i = startIndex; i < totalDepositList.size(); i++) {
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(i);
            String amount = toNuls(cycleInfoTmp.getAvailableDepositAmount()).toString();
            if (!"0".equals(amount)) {
                BigDecimal bigAmount = new BigDecimal(amount);
                sumPrice = cycleInfoTmp.getCurrentPrice().divide(bigAmount, decimals(), BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfoTmp.getDifferCycleValue()));
            }
            sumPriceForRegin = sumPriceForRegin.add(sumPrice);
        }
        return sumPriceForRegin;
    }

    /**
     * 计算当前高度所在的奖励周期
     *
     * @param currentHeight
     * @return
     */
    private int calcRewardCycle(long currentHeight) {
        return Integer.parseInt(String.valueOf(currentHeight - this.createHeight)) / this.awardingCycle;
    }

    /**
     * 初始价格
     */
    @View
    public String initialPrice() {
        return initialPrice.toPlainString() + " " + name() + "/ x NULS";
    }

    @View
    public long createHeight() {
        return createHeight;
    }

    @View
    public String getTotalDepositList() {
        String depositinfo = "{";
        String temp = "";
        int size = totalDepositList.size();
        for (int i = 0; i < size; i++) {
            RewardCycleInfo info = totalDepositList.get(i);
            depositinfo = depositinfo + info.toString() + ",";
        }
        if (size > 0) {
            depositinfo = depositinfo.substring(0, depositinfo.length() - 1);
        }
        depositinfo += "}";
        return depositinfo;
    }

    /**
     * 当前奖励周期
     *
     * @return
     */
    @View
    public long currentRewardCycle() {
        return this.calcRewardCycle(Block.number());
    }

    @View
    public int totalDepositAddressCount() {
        return totalDepositAddressCount;
    }

    @View
    public String totalDeposit() {
        return toNuls(totalDepositManager.getTotalDeposit()).toPlainString();
    }

    @View
    public String totalDepositDetail() {
        return totalDepositManager.getTotalDepositDetail();
    }

    @View
    public long awardingCycle() {
        return this.awardingCycle;
    }

    @View
    public long rewardHalvingCycle() {
        return this.rewardHalvingCycle;
    }

    @View
    public BigInteger minimumDeposit() {
        return this.minimumDeposit;
    }

    @View
    public int minimumLocked() {
        return this.minimumLocked;
    }

    @View
    public int maximumDepositAddressCount() {
        return this.maximumDepositAddressCount;
    }

    /**
     * 查询可领取的共识奖励金额
     */
    @View
    public String ownerAvailableConsensusAward() {
        require(openConsensus, "未开启共识功能");
        return toNuls(consensusManager.getAvailableConsensusReward()).toPlainString();
    }

    /**
     * 查询可委托共识的空闲金额
     */
    @View
    public String freeAmountForConsensusDeposit() {
        require(openConsensus, "未开启共识功能");
        return toNuls(consensusManager.getAvailableAmount()).toPlainString();
    }

    /**
     * 查询合约当前所有信息
     */
    @View
    public String wholeConsensusInfo() {
        String totalDepositDetail = totalDepositDetail();
        String totalDepositList = getTotalDepositList();
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDepositDetail\":")
                .append('\"').append(totalDepositDetail).append('\"');
        sb.append(",\"totalDepositList\":")
                .append('\"').append(totalDepositList).append('\"');
        if(openConsensus) {
            sb.append(",\"consensusManager\":")
                    .append(consensusManager==null?" ":consensusManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 检查是否给合约分配的token
     */
    private boolean isAllocationToken(){
        if(!isGetTotal){
            String[][] args = new String[1][];
            args[0]=new String[]{Msg.address().toString()};
            String balance=tokenContractAddress.callWithReturnValue("balanceOf","",args,BigInteger.ZERO);
            totalAllocation =new BigInteger(balance);
            if(totalAllocation.compareTo(BigInteger.ZERO)>0){
                isGetTotal=true;
            }
        }
        return isGetTotal;
    }


    /**
     * 获取给合约分配的token数量
     */
    @View
    public String getTotalAllocation(){
        this.isAllocationToken();
       return totalAllocation.toString();
    }


    private boolean isAcceptDeposit(){
        if(isGetTotal){
            if(!isAcceptDeposit && allocationAmount.compareTo(totalAllocation)<0){
                isAcceptDeposit=true;
            }
        }else{
            isAcceptDeposit=false;
        }
        return isAcceptDeposit;
    }


    @View
    public String name() {
        return name;
    }

    @View
    public String symbol() {
        return symbol;
    }

    @View
    public int decimals() {
        return decimals;
    }
}
