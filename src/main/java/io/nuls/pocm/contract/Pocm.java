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
import io.nuls.pocm.contract.event.*;
import io.nuls.pocm.contract.manager.ConsensusManager;
import io.nuls.pocm.contract.manager.TotalDepositManager;
import io.nuls.pocm.contract.model.*;
import io.nuls.pocm.contract.ownership.Ownable;

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
    private final BigInteger HLAVING = new BigInteger("2");
    //1天=24*60*60秒
    private final long TIMEPERDAY=86400;

    // 合约创建高度
    private final long createHeight;
    // 初始价格，每个周期奖励可以奖励的Token数量X，分配方式是：每个奖励周期所有参与的NULS抵押数平分这X个Token（最大单位）
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
    private List<RewardCycleInfo> totalDepositList = new ArrayList<RewardCycleInfo>();
    //上一次抵押数量有变动的奖励周期
    private int lastCalcCycle = 0;

    //下一次奖励减半的高度
    private long nextRewardHalvingHeight = 0L;

    // 当前价格，每个周期奖励可以奖励的Token数量X，分配方式是：每个奖励周期所有参与的NULS抵押数平分这X个Token（最小单位）
    private BigInteger currentPrice;

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
    private BigInteger totalAllocation=BigInteger.ZERO;

    //已经分配的Token数量
    private BigInteger allocationAmount=BigInteger.ZERO;

    //未领取的Token数量
    private BigInteger unRewardsAmount=BigInteger.ZERO;

    private  boolean isGetTotal=false;
    //是否接受抵押
    private boolean isAcceptDeposit=false;
    //是否开启合约共识功能
    private boolean openConsensus=false;

    //dapp的唯一识别码
    private String authorizationCode;

    private int  lockedTokenDay;

    private Map<String, ConsensusAgentDepositInfo> agentDeposits =new HashMap<String, ConsensusAgentDepositInfo>();

    //第一笔抵押的高度，在计算未领取奖励时使用，若计算时的高度与第一笔抵押的高度相差小于2则不计算奖励
    private long firstDepositHeight=0;

    /**
     * @param tokenAddress Token合约地址
     * @param cycleRewardTokenAmount 单周期奖励的Token数量
     * @param awardingCycle 奖励发放周期
     * @param minimumDepositNULS 最低抵押NULS数量
     * @param minimumLocked 锁定区块个数
     * @param openConsensus 是否开启合约共识
     * @param lockedTokenDay 获取Token奖励的锁定天数
     * @param authorizationCode dapp的唯一识别码
     * @param rewardHalvingCycle 奖励减半周期（默认空，不减半）
     * @param maximumDepositAddressCount 最大参与抵押人数（默认空，不限制）
     */
    public Pocm(@Required String tokenAddress, @Required BigDecimal  cycleRewardTokenAmount, @Required int awardingCycle,
                @Required BigInteger minimumDepositNULS, @Required int minimumLocked, @Required boolean openConsensus,
                @Required int lockedTokenDay,String authorizationCode,String rewardHalvingCycle, String maximumDepositAddressCount) {
        tokenContractAddress = new Address(tokenAddress);
        require(tokenContractAddress.isContract(),"tokenAddress应该是合约地址");
        require(cycleRewardTokenAmount.compareTo(BigDecimal.ZERO) > 0, "每个奖励周期的Token数量应该大于0");

        require(minimumDepositNULS.compareTo(BigInteger.ZERO) > 0, "最小抵押NULS数量应该大于0");
        require(lockedTokenDay>=0,"Token的锁定天数应该大于等于0");

        this.decimals= Integer.parseInt(tokenContractAddress.callWithReturnValue("decimals","",null,BigInteger.ZERO));

        require(checkMaximumDecimals(cycleRewardTokenAmount, this.decimals), "每个奖励周期的Token数量最多支持" + decimals + "位小数");

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
        this.initialPrice = cycleRewardTokenAmount;
        this.awardingCycle = awardingCycle;
        this.rewardHalvingCycle = rewardHalvingCycleForInt;
        this.minimumDeposit = toNa(new BigDecimal(minimumDepositNULS));
        this.minimumLocked = minimumLocked;
        this.maximumDepositAddressCount = maximumDepositAddressCountForInt;
        this.nextRewardHalvingHeight = this.createHeight + this.rewardHalvingCycle;
        this.currentPrice = toMinUit(cycleRewardTokenAmount,this.decimals);
        this.authorizationCode=authorizationCode;
        this.lockedTokenDay=lockedTokenDay;

        name= tokenContractAddress.callWithReturnValue("name","",null,BigInteger.ZERO);
        symbol= tokenContractAddress.callWithReturnValue("symbol","",null,BigInteger.ZERO);

        totalDepositManager = new TotalDepositManager();
        if(openConsensus) {
            openConsensus();
        }
        emit(new CreateContractEvent(tokenAddress,cycleRewardTokenAmount,awardingCycle, minimumDepositNULS,minimumLocked, openConsensus, lockedTokenDay, authorizationCode,rewardHalvingCycle, maximumDepositAddressCount));
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
        String[] agentInfo = consensusManager.addOtherAgent(agentHash);
        emit(new AgentEvent(agentHash));

        //将共识节点创建者的委托金额加入委托中，参与Token奖励的分配
        //总Token额度大于零，表示已经为此POCM分配了Token，则要检查可领取的Token数量是否足够，若为0表示还没有分配
       // if(totalAllocation.compareTo(BigInteger.ZERO)>0){
       //     require(whetherAcceptDeposit(),"预分配的Token数量已经奖励完毕，不再添加新的共识节点");
       // }

        //BigInteger rewardsAmount= this.allocationAmount.add(this.unRewardsAmount);

        String agentAddress = agentInfo[0];
        BigInteger value = new BigInteger(agentInfo[3]);
        long currentHeight = Block.number();
        long depositNumber = NUMBER++;
        ConsensusAgentDepositInfo agentDepositInfo=new ConsensusAgentDepositInfo(agentHash,agentAddress,depositNumber);

        DepositInfo info = depositUsers.get(agentAddress);
        if (info == null) {
            info = new DepositInfo();
            depositUsers.put(agentAddress, info);
        }

        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value, FULL_PERCENT);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(agentAddress);
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(agentAddress);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value),FULL_PERCENT);
        info.setDepositCount(info.getDepositCount() + 1);

        //将抵押数加入队列中
        this.putDepositToMap(detailInfo.getAvailableAmount(), currentHeight);
        agentDeposits.put(agentHash,agentDepositInfo);
        //记录第一笔抵押的高度
        if(firstDepositHeight == 0){
            firstDepositHeight = currentHeight;
        }
        //初始化挖矿信息
        initMingInfo(currentHeight, agentAddress, agentAddress, depositNumber);

    }

    /**
     * 删除节点信息
     * @param agentHash 其他共识节点的hash
     */
    public void removeAgent(String agentHash){
        onlyOwner();
        require(openConsensus, "未开启共识功能");
        consensusManager.removeAgent(agentHash);
        emit(new RemoveAgentEvent(agentHash));

        //1.共识节点的创建者先领取奖励
        ConsensusAgentDepositInfo agentDepositInfo=agentDeposits.get(agentHash);
        if(agentDepositInfo==null){
            return;
        }
        String userAddress = agentDepositInfo.getDepositorAddress();
        MiningInfo miningInfo = mingUsers.get(userAddress);
        require(miningInfo != null, "没有该共识节点的创建者抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(userAddress);
        long number=agentDepositInfo.getDepositNumber();
        if(number!=0){
            //检查该抵押编号的记录是否存在
            depositInfo.getDepositDetailInfoByNumber(number);
        }
        List<CurrentMingInfo> mingInfosList=this.receive(depositInfo,number);


        //2.共识节点的创建者退出
        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfoByNumber(number);
        long currentHeight = Block.number();
        List<Long> depositNumbers =new ArrayList<Long>();
        //删除挖矿信息
        miningInfo = mingUsers.get(detailInfo.getMiningAddress());
        miningInfo.removeMiningDetailInfoByNumber(number);
        if (miningInfo.getMiningDetailInfos().size() == 0) {
            mingUsers.remove(detailInfo.getMiningAddress());
        }
        depositInfo.removeDepositDetailInfoByNumber(number);
        // 退押金
        depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(detailInfo.getDepositAmount()),FULL_PERCENT);
        //从队列中退出抵押金额
        this.quitDepositToMap(detailInfo.getAvailableAmount(), currentHeight, detailInfo.getDepositHeight());
        depositNumbers.add(number);
        if (depositInfo.getDepositDetailInfos().size() == 0) {
            depositUsers.remove(userAddress);
        }
        agentDeposits.remove(agentHash);

        emit(new CurrentMiningInfoEvent(mingInfosList));
        emit(new QuitDepositEvent(depositNumbers,depositInfo.getDepositorAddress()));

    }

    /**
     * 手动把闲置的抵押金委托到共识节点
     */
    public void depositConsensusManually() {
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
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + toNuls(minimumDeposit).toBigInteger()+"NULS");
        long depositNumber = NUMBER++;

        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value,AVAILABLE_PERCENT);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(userStr);
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value),AVAILABLE_PERCENT);
        info.setDepositCount(info.getDepositCount() + 1);

        //将抵押数加入队列中
        this.putDepositToMap(detailInfo.getAvailableAmount(), currentHeight);

        //记录第一笔抵押的高度
        if(firstDepositHeight==0){
            firstDepositHeight=currentHeight;
        }

        //初始化挖矿信息
        initMingInfo(currentHeight, userStr, userStr, depositNumber);
        totalDepositManager.add(detailInfo.getAvailableAmount());
        emit(new DepositDetailInfoEvent(detailInfo));
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
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + toNuls(minimumDeposit).toBigInteger()+"NULS");
        long depositNumber = NUMBER++;
        long currentHeight = Block.number();
        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value,AVAILABLE_PERCENT);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(miningAddress.toString());
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value),AVAILABLE_PERCENT);
        info.setDepositCount(info.getDepositCount() + 1);

        //将抵押数加入队列中
        this.putDepositToMap(detailInfo.getAvailableAmount(), currentHeight);

        //记录第一笔抵押的高度
        if(firstDepositHeight==0){
            firstDepositHeight=currentHeight;
        }

        //初始化挖矿信息
        initMingInfo(currentHeight, miningAddress.toString(), userStr, depositNumber);
        totalDepositManager.add(detailInfo.getAvailableAmount());
        emit(new DepositDetailInfoEvent(detailInfo));
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

        BigInteger depositAvailableTotalAmount;
        BigInteger depositTotalAmount;
        MiningInfo miningInfo;
        List<Long> depositNumbers =new ArrayList<Long>();
        List<CurrentMingInfo> mingInfosList;

        //表示退出全部的抵押
        if (depositNumber == 0) {
            // 发放所有抵押的奖励
            mingInfosList=this.receive(depositInfo, 0);

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
                depositNumbers.add(key);
            }
            depositInfo.clearDepositDetailInfos();
        } else {
            // 发放指定抵押编号的奖励
            mingInfosList=this.receive(depositInfo, depositNumber);

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
            depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(detailInfo.getDepositAmount()),AVAILABLE_PERCENT);
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);
            //从队列中退出抵押金额
            this.quitDepositToMap(depositAvailableTotalAmount, currentHeight, detailInfo.getDepositHeight());
            depositNumbers.add(depositNumber);
        }
        boolean isEnoughBalance = totalDepositManager.subtract(depositAvailableTotalAmount);
        require(isEnoughBalance, "余额不足以退还押金，请联系项目方，退出抵押金额：" + depositAvailableTotalAmount);
        if (depositInfo.getDepositDetailInfos().size() == 0) {
            totalDepositAddressCount -= 1;
            //TODO 退出后是否保留该账户的挖矿记录
            depositUsers.remove(userString);
        }
        emit(new CurrentMiningInfoEvent(mingInfosList));
        emit(new QuitDepositEvent(depositNumbers,depositInfo.getDepositorAddress()));
        user.transfer(depositTotalAmount);
    }

    /**
     * 放弃抵押，不要奖励
     */
    public void giveUp() {
        long currentHeight = Block.number();

        Address user = Msg.sender();
        String userString = user.toString();
        DepositInfo depositInfo = getDepositInfo(userString);

        List<Long> depositNumbers =new ArrayList<Long>();

        BigInteger depositAvailableTotalAmount = depositInfo.getDepositAvailableTotalAmount();
        BigInteger depositTotalAmount=depositInfo.getDepositTotalAmount();
        Map<Long, DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();
        delMingInfo(depositDetailInfos);
        //从队列中退出抵押金额
        for (Long key : depositDetailInfos.keySet()) {
            DepositDetailInfo detailInfo = depositDetailInfos.get(key);
            this.quitDepositToMap(detailInfo.getAvailableAmount(), currentHeight, detailInfo.getDepositHeight());
            depositNumbers.add(key);
        }
        depositInfo.clearDepositDetailInfos();
        totalDepositAddressCount -= 1;
        depositUsers.remove(userString);

        boolean isEnoughBalance = totalDepositManager.subtract(depositAvailableTotalAmount);
        require(isEnoughBalance, "余额不足以退还押金，请联系项目方，退出抵押金额：" + depositAvailableTotalAmount);

        emit(new QuitDepositEvent(depositNumbers,depositInfo.getDepositorAddress()));
        user.transfer(depositTotalAmount);
    }

    /**
     * 领取奖励,领取为自己抵押挖矿的Token
     * @param depositNumber 抵押编号，若为0表示领取所有抵押交易的奖励
     */
    public void receiveAwards(String depositNumber) {
        long number = 0;
        if (depositNumber != null && depositNumber.trim().length() > 0) {
            require(canConvertNumeric(depositNumber.trim(), String.valueOf(Long.MAX_VALUE)), "抵押编号输入不合法，应该输入数字字符");
            number = Long.valueOf(depositNumber.trim());
        }
        String userAddress = Msg.sender().toString();
        MiningInfo miningInfo = mingUsers.get(userAddress);

        require(miningInfo != null, "没有为自己抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(userAddress);
        if(number!=0){
            //检查该抵押编号的记录是否存在
            depositInfo.getDepositDetailInfoByNumber(number);
        }

        List<CurrentMingInfo> mingInfosList=this.receive(depositInfo,number);
        emit(new CurrentMiningInfoEvent(mingInfosList));
    }

    /**
     * 领取所有用户的奖励，然后清理奖励的过程数据
     */
    public void receiveAwardsForAllUser(){
        List<CurrentMingInfo> mingInfosList=new ArrayList<CurrentMingInfo>();
        if(depositUsers!=null && depositUsers.size()>0){
            Iterator<DepositInfo> iter=depositUsers.values().iterator();
            while (iter.hasNext()){
                DepositInfo depositInfo=iter.next();
                if(depositInfo!=null){
                    List<CurrentMingInfo> mingInfosListTmp= this.receive(depositInfo,0);
                    if(mingInfosListTmp!=null){
                        mingInfosList.addAll(mingInfosListTmp);
                    }
                }
            }
            RewardCycleInfo lastCycleInfo = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
            totalDepositList.clear();
            totalDepositIndex.clear();
            totalDepositList.add(lastCycleInfo);
            totalDepositIndex.put(this.lastCalcCycle, totalDepositList.size() - 1);
        }
        emit(new CurrentMiningInfoEvent(mingInfosList));
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
        List<CurrentMingInfo> mergemingInfosList =new ArrayList<CurrentMingInfo>();
        for (Long key : detailInfos.keySet()) {
            MiningDetailInfo detailInfo = detailInfos.get(key);
            if (!alreadyReceive.contains(detailInfo.getDepositorAddress())) {
                DepositInfo depositInfo = getDepositInfo(detailInfo.getDepositorAddress());
                List<CurrentMingInfo> mingInfosList=this.receive(depositInfo, 0);
                alreadyReceive.add(detailInfo.getDepositorAddress());
                if(mingInfosList!=null && mingInfosList.size()>0){
                    mergemingInfosList.addAll(mingInfosList);
                }
            }
        }
        emit(new CurrentMiningInfoEvent(mergemingInfosList));
    }

    /**
     * 统计未分配的Token数量 = 总Token数量-已分配Token-未领取Token数量
     * @return
     */
    @View
    public String calcUnAllocationTokenAmount(){
        BigInteger result=BigInteger.ZERO;
        if(!isAllocationToken()){
            return result.toString();
        }
        if(this.allocationAmount.compareTo(this.totalAllocation)==0){
            return result.toString();
        }

        if(isAcceptDeposit){
            if(depositUsers!=null && depositUsers.size()>0){
                BigInteger amountTmp=BigInteger.ZERO;
                Iterator<DepositInfo> iter=depositUsers.values().iterator();
                while (iter.hasNext()){
                    DepositInfo depositInfo=iter.next();
                    if(depositInfo!=null){
                        amountTmp=amountTmp.add(this.calcUnReceiceMining(depositInfo,null,0));
                    }
                }
                result=this.totalAllocation.subtract(this.allocationAmount).subtract(amountTmp);
                if(result.compareTo(BigInteger.ZERO)<0){
                    result=BigInteger.ZERO;
                }
            }else{
                result=this.totalAllocation.subtract(this.allocationAmount);
            }
        }else{
            //Token可分配的数量已经不足
            result=BigInteger.ZERO;
        }

        return result.toString();
    }

    /**
     * 领取抵押者参与抵押的交易未领取的收益
     * @param depositorAddress 抵押者账户地址
     * @param depositNumber 抵押编号，若为0表示计算所有抵押交易的收益
     * @return
     */
    @View
    public String calcUnReceiveAwards(@Required Address depositorAddress,String depositNumber){
        long number = 0;
        if (depositNumber != null && depositNumber.trim().length() > 0) {
            require(canConvertNumeric(depositNumber.trim(), String.valueOf(Long.MAX_VALUE)), "抵押编号输入不合法，应该输入数字字符");
            number = Long.valueOf(depositNumber.trim());
        }
        String address =depositorAddress.toString();
        DepositInfo depositInfo = getDepositInfo(address);
        if(number!=0){
            //检查该抵押编号的记录是否存在
            depositInfo.getDepositDetailInfoByNumber(number);
        }
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
       // this.whetherAcceptDeposit();

        BigInteger unReceiveAwards =this.calcUnReceiceMining(depositInfo,null,number);
        return unReceiveAwards.toString();
    }

    /**
     *
     * @return 抵押者为自己抵押后未领取的收益
     */
    @View
    public String calcUnReceiveAwardsForOwner(@Required Address depositorAddress){
        String address =depositorAddress.toString();
        DepositInfo depositInfo = getDepositInfo(address);
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
       // this.whetherAcceptDeposit();
        BigInteger unReceiveAwards =this.calcUnReceiceMining(depositInfo,address,0);
        return unReceiveAwards.toString();
    }


    /**
     *
     * @return 由挖矿接收地址发起计算未领取的收益；当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     */
    @View
    public String calcUnReceiveAwardsForMiningAddress(@Required Address receiverMiningAddress){
        BigInteger unReceiveAwards=BigInteger.ZERO;
        String address =receiverMiningAddress.toString();
        List<String> alreadyReceive = new ArrayList<String>();
        MiningInfo miningInfo = mingUsers.get(address);
        require(miningInfo != null, "没有替" + address + "用户抵押挖矿的挖矿信息");
        Map<Long, MiningDetailInfo> detailInfos = miningInfo.getMiningDetailInfos();
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
        //this.whetherAcceptDeposit();

        for (Long key : detailInfos.keySet()) {
            MiningDetailInfo detailInfo = detailInfos.get(key);
            if (!alreadyReceive.contains(detailInfo.getDepositorAddress())) {
                DepositInfo depositInfo = getDepositInfo(detailInfo.getDepositorAddress());
                unReceiveAwards =unReceiveAwards.add(this.calcUnReceiceMining(depositInfo,address,0));
                alreadyReceive.add(detailInfo.getDepositorAddress());
            }
        }
        return  unReceiveAwards.toString();
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

    /**
     * 领取奖励
     *
     * @param depositInfo
     * @return 返回请求地址的挖矿信息
     */
    private List<CurrentMingInfo> receive(DepositInfo depositInfo,long depositNumber) {
        Map<String, BigInteger> mingResult = new HashMap<String, BigInteger>();
        //预分配的Token已经奖励完，不再进行奖励计算
        if(allocationAmount.compareTo(totalAllocation)==0){
            return null;
        }

        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
        //this.whetherAcceptDeposit();

        // 奖励计算, 计算每次挖矿的高度是否已达到奖励减半周期的范围，若达到，则当次奖励减半，以此类推
        List<CurrentMingInfo> mingInfosList = this.calcMining(depositInfo, mingResult,depositNumber);

        Set<Map.Entry<String, BigInteger>> entrySet=mingResult.entrySet();
        Iterator<Map.Entry<String, BigInteger>> iterator =entrySet.iterator();
        long currentTime=Block.timestamp();
        String lockedTime=String.valueOf(currentTime+this.lockedTokenDay*this.TIMEPERDAY);
        while(iterator.hasNext()){
            Map.Entry<String, BigInteger> ming = iterator.next();
            BigInteger mingValue = ming.getValue();
            if(mingValue.compareTo(BigInteger.ZERO)>0){
                //  user.transfer(mingValue);
                String[][] args = new String[3][];
                args[0]=new String[]{ming.getKey()};
                args[1]=new String[]{mingValue.toString()};
                args[2]=new String[]{lockedTime};
                //tokenContractAddress.call("transfer","",args,BigInteger.ZERO);
                tokenContractAddress.call("transferLocked","",args,BigInteger.ZERO);
                this.allocationAmount=this.allocationAmount.add(mingValue);
            }
        }

      return mingInfosList;
    }

    /**
     * 计算奖励数额
     *
     * @param depositInfo
     * @param mingResult
     * @param depositNumber
     * @return
     */
    private List<CurrentMingInfo> calcMining(DepositInfo depositInfo, Map<String, BigInteger> mingResult,long depositNumber) {
        List<CurrentMingInfo> mingInfosList=new ArrayList<CurrentMingInfo>();
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight);
        Map<Long, DepositDetailInfo> detailInfos = depositInfo.getDepositDetailInfos();

        //若指定了抵押编号，则只计算此抵押编号的奖励收益
        if(depositNumber!=0){
            DepositDetailInfo detailInfo = detailInfos.get(depositNumber);
            MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
/*            if(mingDetailInfo.isRewardsEnd()){
                //表示该抵押已经领取过最后一笔奖励,不能再领取
                return mingInfosList;
            }*/

            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            //说明未到领取奖励的高度
            if (nextStartMiningCycle <= currentRewardCycle) {
                BigInteger miningTmp =BigInteger.ZERO;
                //已经分配完毕
                if(this.isAcceptDeposit){
                    BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
                    BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
                    miningTmp = availableDepositAmountNULS.multiply(sumPrice).toBigInteger();

                    //Token数量不够分配
                    if(miningTmp.add(this.allocationAmount).compareTo(this.totalAllocation)>=0){
                        this.isAcceptDeposit=false;
                        miningTmp=this.totalAllocation.subtract(this.allocationAmount);
                    }
                }

/*
                if(!isAcceptDeposit){
                    //在余额不足的情况下，已经领取过一部分
                    if(mingDetailInfo.getCanRewarsAmountWhenFinal().compareTo(BigInteger.ZERO)>0){
                        if(mingDetailInfo.getCanRewarsAmountWhenFinal().compareTo(miningTmp)<=0){
                            miningTmp=mingDetailInfo.getCanRewarsAmountWhenFinal();
                            mingDetailInfo.setRewardsEnd(true);
                            mingDetailInfo.setCanRewarsAmountWhenFinal(BigInteger.ZERO);
                        }else{
                            mingDetailInfo.setCanRewarsAmountWhenFinal(mingDetailInfo.getCanRewarsAmountWhenFinal().subtract(miningTmp));
                        }
                    }else{
                        //余额不足的情况下，第一次领取
                        BigInteger canRewardsamount=this.allocationRatio.multiply(toNuls(detailInfo.getAvailableAmount())).toBigInteger();
                        if(canRewardsamount.compareTo(miningTmp)<=0){
                            miningTmp=canRewardsamount;
                            mingDetailInfo.setRewardsEnd(true);
                        }else{
                            mingDetailInfo.setCanRewarsAmountWhenFinal(canRewardsamount.subtract(miningTmp));
                        }
                    }
                }*/

                mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(miningTmp));
                mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount() + currentRewardCycle - nextStartMiningCycle + 1);
                mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
                miningInfo.setTotalMining(miningInfo.getTotalMining().add(miningTmp));
                miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(miningTmp));

                mingResult.put(mingDetailInfo.getReceiverMiningAddress(), miningTmp);

                //封装当次的挖矿信息
                CurrentMingInfo currentMingInfo= new CurrentMingInfo();
                currentMingInfo.setDepositNumber(detailInfo.getDepositNumber());
                currentMingInfo.setMiningAmount(miningTmp);
                currentMingInfo.setMiningCount(currentRewardCycle - nextStartMiningCycle + 1);
                currentMingInfo.setReceiverMiningAddress(mingDetailInfo.getReceiverMiningAddress());
                mingInfosList.add(currentMingInfo);
            }
        }else{
            BigInteger miningSum=this.allocationAmount;
            //若未指定抵押编号，则只计算所有抵押的奖励收益
            for (Long key : detailInfos.keySet()) {
                DepositDetailInfo detailInfo = detailInfos.get(key);
                MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
                MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
                int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
                //说明未到领取奖励的高度
                if (nextStartMiningCycle > currentRewardCycle) {
                    continue;
                }
/*                if(mingDetailInfo.isRewardsEnd()){
                    //表示该抵押已经领取过最后一笔奖励,不能再领取
                    continue;
                }*/
                BigInteger miningTmp =BigInteger.ZERO;
                //已经分配完毕
                if(this.isAcceptDeposit){
                    BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
                    BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
                    miningTmp = availableDepositAmountNULS.multiply(sumPrice).toBigInteger();
                    //Token数量不够分配
                    if(miningSum.add(miningTmp).compareTo(this.totalAllocation)>=0){
                        this.isAcceptDeposit=false;
                        miningTmp=this.totalAllocation.subtract(miningSum);
                    }
                    miningSum=miningSum.add(miningTmp);
                }


/*                //Token数量不够分配
                if(!isAcceptDeposit){
                    //在余额不足的情况下，已经领取过一部分
                    if(mingDetailInfo.getCanRewarsAmountWhenFinal().compareTo(BigInteger.ZERO)>0){
                        if(mingDetailInfo.getCanRewarsAmountWhenFinal().compareTo(miningTmp)<=0){
                            miningTmp=mingDetailInfo.getCanRewarsAmountWhenFinal();
                            mingDetailInfo.setRewardsEnd(true);
                            mingDetailInfo.setCanRewarsAmountWhenFinal(BigInteger.ZERO);
                        }else{
                            mingDetailInfo.setCanRewarsAmountWhenFinal(mingDetailInfo.getCanRewarsAmountWhenFinal().subtract(miningTmp));
                        }
                    }else{
                        //余额不足的情况下，第一次领取
                        BigInteger canRewardsamount=this.allocationRatio.multiply(toNuls(detailInfo.getAvailableAmount())).toBigInteger();
                        if(canRewardsamount.compareTo(miningTmp)<=0){
                            miningTmp=canRewardsamount;
                            mingDetailInfo.setRewardsEnd(true);
                        }else{
                            mingDetailInfo.setCanRewarsAmountWhenFinal(canRewardsamount.subtract(miningTmp));
                        }
                    }
                }*/

                mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(miningTmp));
                mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount() + currentRewardCycle - nextStartMiningCycle + 1);
                mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
                miningInfo.setTotalMining(miningInfo.getTotalMining().add(miningTmp));
                miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(miningTmp));

                //封装当次的挖矿信息
                CurrentMingInfo currentMingInfo= new CurrentMingInfo();
                currentMingInfo.setDepositNumber(detailInfo.getDepositNumber());
                currentMingInfo.setMiningAmount(miningTmp);
                currentMingInfo.setMiningCount(currentRewardCycle - nextStartMiningCycle + 1);
                currentMingInfo.setReceiverMiningAddress(mingDetailInfo.getReceiverMiningAddress());
                mingInfosList.add(currentMingInfo);

                if (mingResult.containsKey(mingDetailInfo.getReceiverMiningAddress())) {
                    miningTmp = mingResult.get(mingDetailInfo.getReceiverMiningAddress()).add(miningTmp);
                }
                mingResult.put(mingDetailInfo.getReceiverMiningAddress(), miningTmp);
            }
        }
        return mingInfosList;
    }

    /**
     * 计算未获取的收益
     * @param depositInfo
     * @param receiceAddress 接收奖励的地址,若为null表示计算所有接收奖励的地址
     * @param depositNumber  抵押编号，若为0表示不限制抵押编号
     * @return
     */
    private BigInteger calcUnReceiceMining(DepositInfo depositInfo,String receiceAddress,long depositNumber){
        BigInteger mining = BigInteger.ZERO;
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        int firstRewardCycle = this.calcRewardCycle(firstDepositHeight);
        if(currentRewardCycle-firstRewardCycle<2){
            //若距离第一笔抵押还未过2个奖励周期，则没有奖励可以领取，直接返回0
            return mining;
        }

        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight);
        Map<Long, DepositDetailInfo> detailInfos = depositInfo.getDepositDetailInfos();
        for (Long key : detailInfos.keySet()) {
            //若指定了抵押编号，则只计算此抵押编号的奖励收益
            if(depositNumber!=0 && key!=depositNumber){
                continue;
            }
            DepositDetailInfo detailInfo = detailInfos.get(key);
            //只计算指定address的收益
            if(receiceAddress!=null && !detailInfo.getMiningAddress().equals(receiceAddress)){
                continue;
            }
            MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            //说明未到领取奖励的高度
            if (nextStartMiningCycle > currentRewardCycle) {
                continue;
            }
            BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
            BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
            BigInteger miningTmp =availableDepositAmountNULS.multiply(sumPrice).toBigInteger();

            //Token数量不够分配,按照抵押比例进行分配
 /*           if(!isAcceptDeposit){
                BigInteger canRewardsamount=this.allocationRatio.multiply(toNuls(detailInfo.getAvailableAmount())).toBigInteger();
                if(canRewardsamount.compareTo(miningTmp)<=0){
                    miningTmp=canRewardsamount;
                }
            }*/

            mining = mining.add(miningTmp);
        }
        return mining;
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
                this.currentPrice=this.currentPrice.divide(this.HLAVING);
                //this.currentPrice = this.currentPrice.divide(this.HLAVING, decimals(), BigDecimal.ROUND_DOWN);
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
            if (totalDepositList.size() > 0) {
                //取队列中最后一个奖励周期的信息
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositList.size() - 1);
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
            this.currentPrice=this.currentPrice.divide(this.HLAVING);
            //this.currentPrice = this.currentPrice.divide(this.HLAVING, decimals(), BigDecimal.ROUND_DOWN);
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
            if(cycleInfoTmp.getAvailableDepositAmount().compareTo(BigInteger.ZERO)>0){
                BigDecimal amount = toNuls(cycleInfoTmp.getAvailableDepositAmount());
                //为了提高计算的精确度，保留小数点后8位
                sumPrice= new BigDecimal(cycleInfoTmp.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfoTmp.getDifferCycleValue()));
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
     * 当前的每个奖励周期奖励Token的数量
     */
    @View
    public String currentCycleRewardTokenAmount() {
        int size = totalDepositList.size();
        if (size > 0) {
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(size - 1);
            BigInteger intAmount = cycleInfoTmp.getAvailableDepositAmount();
            if (intAmount.compareTo(BigInteger.ZERO) == 0) {
                return "Unknown";
            }
            return toMaxUit(cycleInfoTmp.getCurrentPrice().multiply(BigInteger.TEN.pow(8)).divide(intAmount),this.decimals).toPlainString()+ " " + name() + "/NULS .";
        } else {
            return initialPrice.toPlainString() + " " + name() + "/ x NULS .";
        }
    }

    /**
     * 初始的每个奖励周期奖励Token的数量
     */
    @View
    public String initialCycleRewardTokenAmount() {
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

    @View
    public int getLockedDay(){
        return this.lockedTokenDay;
    }

    /**
     * 查询可领取的共识奖励金额
     */
    @View
    public String ownerAvailableConsensusAward() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().toString();
    }

    /**
     * 查询共识总奖励金额
     */
    @View
    public String ownerTotalConsensusAward() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().add(consensusManager.getTransferedConsensusReward()).toString();
    }

    /**
     * 查询可委托共识的空闲金额
     */
    @View
    public String freeAmountForConsensusDeposit() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableAmount().toString();
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
                    .append(consensusManager==null?"0":consensusManager.toString());
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
                isAcceptDeposit=true;
            }
        }
        return isGetTotal;
    }

    /**
     * 大致检查是否已经分配完毕
     * @return
     */
    private boolean isAcceptDeposit(){
        return isAcceptDeposit;
    }

    /**
     * 获取给合约分配的token数量
     */
    @View
    public String getTotalAllocation(){
        this.isAllocationToken();
       return totalAllocation.toString();
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
