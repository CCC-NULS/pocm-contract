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
import io.nuls.pocm.contract.util.PocmUtil;

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
    public static BigInteger _2000_NULS = BigInteger.valueOf(200000000000L);

    // POCM合约修订版本
    private static final String VERSION = "V10";

    private final BigInteger HLAVING = new BigInteger("2");
    //1天=24*60*60秒
    private final long TIMEPERDAY = 86400;

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
    private String name;
    //token的symbol
    private String symbol;
    //token的精度
    private int decimals;

    //token的总分配量
    private BigInteger totalAllocation = BigInteger.ZERO;

    //已经分配的Token数量
    private BigInteger allocationAmount = BigInteger.ZERO;

    private boolean isGetTotal = false;
    //是否接受抵押
    private boolean isAcceptDeposit = false;
    //是否开启合约共识功能
    private boolean openConsensus = false;

    //dapp的唯一识别码
    private String authorizationCode;

    private int lockedTokenDay;

    private Map<String, ConsensusAgentDepositInfo> agentDeposits = new HashMap<String, ConsensusAgentDepositInfo>();

    //第一笔抵押的高度，在计算未领取奖励时使用，若计算时的高度与第一笔抵押的高度相差小于2则不计算奖励
    private long firstDepositHeight = 0;

    /**
     * @param tokenAddress               Token合约地址
     * @param cycleRewardTokenAmount     单周期奖励的Token数量
     * @param awardingCycle              奖励发放周期
     * @param minimumDepositNULS         最低抵押NULS数量
     * @param minimumLocked              锁定区块个数
     * @param openConsensus              是否开启合约共识
     * @param lockedTokenDay             获取Token奖励的锁定天数
     * @param authorizationCode          dapp的唯一识别码
     * @param rewardHalvingCycle         奖励减半周期（默认空，不减半）
     * @param maximumDepositAddressCount 最大参与抵押人数（默认空，不限制）
     */
    public Pocm(@Required String tokenAddress, @Required BigDecimal cycleRewardTokenAmount, @Required int awardingCycle,
                @Required BigInteger minimumDepositNULS, @Required int minimumLocked, @Required boolean openConsensus,
                @Required int lockedTokenDay, String authorizationCode, String rewardHalvingCycle, String maximumDepositAddressCount) {
        tokenContractAddress = new Address(tokenAddress);
        require(tokenContractAddress.isContract(), "tokenAddress应该是合约地址");
        require(cycleRewardTokenAmount.compareTo(BigDecimal.ZERO) > 0, "每个奖励周期的Token数量应该大于0");

        require(minimumDepositNULS.compareTo(BigInteger.ZERO) > 0, "最小抵押NULS数量应该大于0");
        require(lockedTokenDay >= 0, "Token的锁定天数应该大于等于0");

        this.decimals = Integer.parseInt(tokenContractAddress.callWithReturnValue("decimals", "", null, BigInteger.ZERO));

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
        this.currentPrice = toMinUit(cycleRewardTokenAmount, this.decimals);
        this.authorizationCode = authorizationCode;
        this.lockedTokenDay = lockedTokenDay;

        name = tokenContractAddress.callWithReturnValue("name", "", null, BigInteger.ZERO);
        symbol = tokenContractAddress.callWithReturnValue("symbol", "", null, BigInteger.ZERO);

        totalDepositManager = new TotalDepositManager();
        if (openConsensus) {
            openConsensus();
        }
        emit(new CreateContractEvent(tokenAddress, cycleRewardTokenAmount, awardingCycle, minimumDepositNULS, minimumLocked, openConsensus, lockedTokenDay, authorizationCode, rewardHalvingCycle, maximumDepositAddressCount));
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
        if (consensusManager == null) {
            consensusManager = new ConsensusManager();
        }
        totalDepositManager.setOpenConsensus(true);
        totalDepositManager.setConsensusManager(consensusManager);
    }

    public void closeConsensus() {
        onlyOwnerOrOffcial();
        require(openConsensus, "已关闭共识功能");
        this.openConsensus = false;
        totalDepositManager.closeConsensus();
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        onlyOffcial();
        require(openConsensus, "未开启共识功能");
        require(value.compareTo(_2000_NULS) >= 0, "金额太小");
        consensusManager.modifyMinJoinDeposit(value);
    }

    public void withdrawSpecifiedAmount(BigInteger value) {
        onlyOwnerOrOffcial();
        require(openConsensus, "未开启共识功能");
        consensusManager.withdrawSpecifiedAmount(value);
    }

    public void repairConsensus(BigInteger value) {
        onlyOffcial();
        require(openConsensus, "未开启共识功能");
        consensusManager.repairAmount(value);
    }

    public void repairTotalDepositManager(BigInteger value) {
        onlyOffcial();
        totalDepositManager.repairAmount(value);
    }

    /**
     * 添加其他节点的共识信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void addOtherAgent(String agentHash) {
        onlyOwnerOrOffcial();
        require(openConsensus, "未开启共识功能");
        require(isAllocationToken(), "此POCM合约未预分配Token,暂不接受添加节点");
        require(isAcceptDeposit(), "预分配的Token数量已经奖励完毕，不再接受添加节点");
        String[] agentInfo = consensusManager.addOtherAgent(agentHash);
        String agentAddress = agentInfo[0];
        Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
        for (ConsensusAgentDepositInfo agentDepositInfo : agentDepositInfos) {
            require(!agentDepositInfo.getDepositorAddress().equals(agentAddress), "当前添加的节点的创建者地址和已添加的节点的创建者地址冲突");
        }
        BigInteger value = new BigInteger(agentInfo[3]);
        emit(new AgentEvent(agentHash, value));

        long currentHeight = Block.number();

        DepositInfo info = depositUsers.get(agentAddress);
        long depositNumber;
        if (info == null) {
            depositNumber = NUMBER++;
            info = new DepositInfo();
            depositUsers.put(agentAddress, info);

            DepositDetailInfo detailInfo = new DepositDetailInfo();
            detailInfo.setDepositAmount(value, FULL_PERCENT);
            detailInfo.setDepositHeight(currentHeight);
            detailInfo.setMiningAddress(agentAddress);
            detailInfo.setDepositNumber(depositNumber);

            info.setDepositDetailInfo(detailInfo);
            info.setDepositorAddress(agentAddress);
            info.setDepositTotalAmount(value, FULL_PERCENT);
            info.setDepositCount(1);
        } else {
            //存在抵押记录，领取奖励
            List<CurrentMingInfo> mingInfosList = this.receive(info, true);
            if (mingInfosList != null && mingInfosList.size() > 0) {
                emit(new CurrentMiningInfoEvent(mingInfosList));
            }
            //更新抵押信息
            DepositDetailInfo detailInfo = info.getDepositDetailInfo();
            detailInfo.setDepositAmount(value, FULL_PERCENT);
            detailInfo.setDepositHeight(currentHeight);
            detailInfo.setMiningAddress(agentAddress);
            depositNumber = detailInfo.getDepositNumber();

            info.setDepositTotalAmount(value, FULL_PERCENT);
            info.setDepositCount(info.getDepositCount() + 1);
        }
        ConsensusAgentDepositInfo agentDepositInfo = new ConsensusAgentDepositInfo(agentHash, agentAddress, depositNumber);

        //将抵押数加入队列中
        this.putDepositToMap(value, currentHeight);
        agentDeposits.put(agentHash, agentDepositInfo);
        //记录第一笔抵押的高度
        if (firstDepositHeight == 0) {
            firstDepositHeight = currentHeight;
        }
        //初始化挖矿信息
        initMingInfo(currentHeight, agentAddress, agentAddress, depositNumber);

    }

    /**
     * 删除节点信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void removeAgent(String agentHash) {
        onlyOwnerOrOffcial();
        require(openConsensus, "未开启共识功能");
        consensusManager.removeAgent(agentHash);
        emit(new RemoveAgentEvent(agentHash));

        //1.共识节点的创建者先领取奖励
        ConsensusAgentDepositInfo agentDepositInfo = agentDeposits.get(agentHash);
        require(agentDepositInfo != null, "该共识节点的创建者没有添加节点信息");

        String userAddress = agentDepositInfo.getDepositorAddress();
        MiningInfo miningInfo = mingUsers.get(userAddress);
        require(miningInfo != null, "没有该共识节点的创建者抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(userAddress);

        List<CurrentMingInfo> mingInfosList = this.receive(depositInfo, true);

        //2.共识节点的创建者退出
        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfo();
        long currentHeight = Block.number();
        List<Long> depositNumbers = new ArrayList<Long>();

        // 参与POCM的抵押金额 ，参与POCM的抵押金额=锁定金额*9，因为availableAmount可能包含共识节点的抵押金额，所以通过锁定金额反向计算参与抵押的金额
        BigInteger quitAvailableAmount = detailInfo.getLockedAmount().multiply(PocmUtil.AVAILABLE_PERCENT.multiply(new BigDecimal("10")).toBigInteger());
        BigInteger lockedAmount = detailInfo.getLockedAmount();
        //退出抵押返回的押金=参与POCM的抵押金额+锁定金额
        BigInteger selfDepositAmount = lockedAmount.add(quitAvailableAmount);

        BigInteger agentDepositAmount = detailInfo.getDepositAmount().subtract(selfDepositAmount);
        //说明共识节点的创建者没有主动参与抵押
        if (selfDepositAmount.compareTo(BigInteger.ZERO) == 0) {
            depositUsers.remove(userAddress);
            //删除挖矿信息
            mingUsers.remove(detailInfo.getMiningAddress());
        } else {
            // 更新退押金详情
            detailInfo.updateDepositTotalAmount(agentDepositAmount, agentDepositAmount, BigInteger.ZERO);
        }

        // 退押金
        depositInfo.updateDepositTotalAmount(agentDepositAmount, agentDepositAmount, BigInteger.ZERO);
        //从队列中退出抵押金额
        this.quitDepositToMap(agentDepositAmount, currentHeight, detailInfo.getDepositHeight());

        depositNumbers.add(detailInfo.getDepositNumber());
        agentDeposits.remove(agentHash);

        emit(new CurrentMiningInfoEvent(mingInfosList));
        emit(new QuitDepositEvent(depositNumbers, depositInfo.getDepositorAddress()));

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
        require(isAllocationToken(), "此POCM合约未预分配Token,暂不接受抵押");
        require(isAcceptDeposit(), "预分配的Token数量已经奖励完毕，不再接受抵押");
        String userStr = Msg.sender().toString();
        DepositInfo info = depositUsers.get(userStr);
        BigInteger value = Msg.value();
        BigInteger decimalValue = PocmUtil.extractDecimal(value);
        boolean hasDecimal = decimalValue.compareTo(BigInteger.ZERO) > 0;
        if (hasDecimal) {
            // 防止退回的小数金额太小
            if (decimalValue.compareTo(MININUM_TRANSFER_AMOUNT) < 0) {
                decimalValue = decimalValue.add(ONE_NULS);
            }
            value = value.subtract(decimalValue);
            require(decimalValue.add(value).compareTo(Msg.value()) == 0, "小数提取错误，抵押金: " + Msg.value());
        }
        long currentHeight = Block.number();
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + toNuls(minimumDeposit).toBigInteger() + "NULS，若抵押值有小数，请去掉小数");
        long depositNumber = 0;
        if (info == null) {
            depositNumber = NUMBER++;
            if (maximumDepositAddressCount > 0) {
                require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
            }
            info = new DepositInfo();
            depositUsers.put(userStr, info);
            totalDepositAddressCount += 1;

            DepositDetailInfo detailInfo = new DepositDetailInfo();
            detailInfo.setDepositAmount(value, AVAILABLE_PERCENT);
            detailInfo.setDepositHeight(currentHeight);
            detailInfo.setMiningAddress(userStr);
            detailInfo.setDepositNumber(depositNumber);

            info.setDepositDetailInfo(detailInfo);
            info.setDepositorAddress(userStr);
            info.setDepositTotalAmount(value, AVAILABLE_PERCENT);
            info.setDepositCount(1);
        } else {
            //存在抵押记录，领取奖励
            List<CurrentMingInfo> mingInfosList = this.receive(info, true);
            emit(new CurrentMiningInfoEvent(mingInfosList));
            //更新抵押信息
            DepositDetailInfo detailInfo = info.getDepositDetailInfo();
            detailInfo.setDepositAmount(value, AVAILABLE_PERCENT);
            detailInfo.setDepositHeight(currentHeight);
            detailInfo.setMiningAddress(userStr);
            depositNumber = detailInfo.getDepositNumber();
            info.setDepositTotalAmount(value, AVAILABLE_PERCENT);
            info.setDepositCount(info.getDepositCount() + 1);
        }

        BigDecimal bigDecimalValue = new BigDecimal(value);
        BigInteger availableDepositValue = AVAILABLE_PERCENT.multiply(bigDecimalValue).toBigInteger();

        //将抵押数加入队列中
        this.putDepositToMap(availableDepositValue, currentHeight);

        //记录第一笔抵押的高度
        if (firstDepositHeight == 0) {
            firstDepositHeight = currentHeight;
        }

        //初始化挖矿信息
        initMingInfo(currentHeight, userStr, userStr, depositNumber);
        totalDepositManager.add(availableDepositValue);
        // 退还抵押金的小数位
        if (hasDecimal) {
            Msg.sender().transfer(decimalValue);
        }
        emit(new DepositDetailInfoEvent(info.getDepositDetailInfo(), value));
    }

    /**
     * 退出抵押挖矿，当抵押编号为0时退出全部抵押
     *
     * @param number 抵押编号
     * @return
     */
    public void quit(String number) {
        this.quitByUser(Msg.sender());
    }

    private void quitByUser(Address user) {
        long currentHeight = Block.number();
        String userString = user.toString();
        DepositInfo depositInfo = getDepositInfo(userString);

        List<Long> depositNumbers = new ArrayList<Long>();

        // 发放指定抵押编号的奖励
        List<CurrentMingInfo> mingInfosList = this.receive(depositInfo, true);

        //退出某一次抵押
        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfo();
        long unLockedHeight = checkDepositLocked(detailInfo);
        require(unLockedHeight == -1, "挖矿锁定中, 解锁高度是 " + unLockedHeight);


        //退出的总抵押金额
        BigInteger totalDepositAmount = detailInfo.getDepositAmount();

        // 参与POCM的抵押金额 ，参与POCM的抵押金额=锁定金额*9，因为availableAmount可能包含共识节点的抵押金额，所以通过锁定金额反向计算参与抵押的金额
        BigInteger quitAvailableAmount = detailInfo.getLockedAmount().multiply(PocmUtil.AVAILABLE_PERCENT.multiply(new BigDecimal("10")).toBigInteger());

        //防止共识节点的创建在无抵押的情况下调用此方法：当共识节点的创建者调用此方法时，可能存在抵押记录，但是没有主动参与抵押的金额
        require(quitAvailableAmount.compareTo(BigInteger.ZERO) > 0, "此用户参与抵押的金额为零");

        BigInteger lockedAmount = detailInfo.getLockedAmount();
        //退出抵押返回的押金=参与POCM的抵押金额+锁定金额
        BigInteger transferTotalAmount = lockedAmount.add(quitAvailableAmount);

        boolean isEnoughBalance = totalDepositManager.subtract(quitAvailableAmount);
        require(isEnoughBalance, "余额不足以退还押金，请联系项目方，退出抵押金额：" + quitAvailableAmount);

        //退出抵押时返回的押金等于总抵押金额，说明该地址是是共识节点的创建者,该quit方法只退出该创建者参与抵押的金额
        if (totalDepositAmount.compareTo(transferTotalAmount) > 0) {
            // 更新退押金详情
            detailInfo.updateDepositTotalAmount(transferTotalAmount, quitAvailableAmount, lockedAmount);
        } else {
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);
            this.totalDepositAddressCount -= 1;
            depositUsers.remove(userString);
            //删除挖矿信息
            mingUsers.remove(detailInfo.getMiningAddress());
        }

        // 更新退押金
        depositInfo.updateDepositTotalAmount(transferTotalAmount, quitAvailableAmount, lockedAmount);

        //从队列中退出抵押金额
        this.quitDepositToMap(quitAvailableAmount, currentHeight, detailInfo.getDepositHeight());
        depositNumbers.add(detailInfo.getDepositNumber());

        if (mingInfosList != null && mingInfosList.size() > 0) {
            emit(new CurrentMiningInfoEvent(mingInfosList));
        }
        emit(new QuitDepositEvent(depositNumbers, depositInfo.getDepositorAddress()));
        user.transfer(transferTotalAmount);

    }

    public void quitAll() {
        onlyOwnerOrOffcial();
        require(consensusManager.getAgents() == null, "请先移除共识节点hash");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = depositUsers.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.quitByUser(new Address(user));
        }
    }

    /**
     * 放弃抵押，不要奖励
     */
    public void giveUp() {
        this.giveUpByUser(Msg.sender());
    }

    private void giveUpByUser(Address user) {
        long currentHeight = Block.number();

        String userString = user.toString();
        DepositInfo depositInfo = getDepositInfo(userString);

        List<Long> depositNumbers = new ArrayList<Long>();

        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfo();
        //退出的总抵押金额
        BigInteger totalDepositAmount = detailInfo.getDepositAmount();

        // 参与POCM的抵押金额 ，参与POCM的抵押金额=锁定金额*9，因为availableAmount可能包含共识节点的抵押金额，所以通过锁定金额反向计算参与抵押的金额
        BigInteger quitAvailableAmount = detailInfo.getLockedAmount().multiply(PocmUtil.AVAILABLE_PERCENT.multiply(new BigDecimal("10")).toBigInteger());
        //防止共识节点的创建在无抵押的情况下调用此方法：当共识节点的创建者调用此方法时，可能存在抵押记录，但是没有主动参与抵押的金额
        require(quitAvailableAmount.compareTo(BigInteger.ZERO) > 0, "此用户参与抵押的金额为零");

        BigInteger lockedAmount = detailInfo.getLockedAmount();
        //退出抵押返回的押金=参与POCM的抵押金额+锁定金额
        BigInteger transferTotalAmount = lockedAmount.add(quitAvailableAmount);

        boolean isEnoughBalance = totalDepositManager.subtract(quitAvailableAmount);
        require(isEnoughBalance, "余额不足以退还押金，请联系项目方，退出抵押金额：" + quitAvailableAmount);
        this.quitDepositToMap(quitAvailableAmount, currentHeight, detailInfo.getDepositHeight());

        //退出抵押时返回的押金等于总抵押金额，说明该地址是是共识节点的创建者,该quit方法只退出该创建者参与抵押的金额
        if (totalDepositAmount.compareTo(transferTotalAmount) > 0) {
            // 更新退押金详情
            detailInfo.updateDepositTotalAmount(transferTotalAmount, quitAvailableAmount, lockedAmount);
        } else {
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);
            this.totalDepositAddressCount -= 1;
            depositUsers.remove(userString);
            //删除挖矿信息
            mingUsers.remove(detailInfo.getMiningAddress());
        }

        depositNumbers.add(detailInfo.getDepositNumber());
        emit(new QuitDepositEvent(depositNumbers, depositInfo.getDepositorAddress()));
        user.transfer(transferTotalAmount);
    }

    public void giveUpAll() {
        onlyOffcial();
        require(consensusManager.getAgents() == null, "请先移除共识节点hash");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = depositUsers.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.giveUpByUser(new Address(user));
        }
    }

    /**
     * 领取奖励,领取为自己抵押挖矿的Token
     *
     * @param depositNumber 抵押编号，若为0表示领取所有抵押交易的奖励
     */
    public void receiveAwards(String depositNumber) {
        String userAddress = Msg.sender().toString();
        MiningInfo miningInfo = mingUsers.get(userAddress);
        require(miningInfo != null, "没有为自己抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(userAddress);
        List<CurrentMingInfo> mingInfosList = this.receive(depositInfo, true);
        if (mingInfosList != null && mingInfosList.size() > 0) {
            emit(new CurrentMiningInfoEvent(mingInfosList));
        }
    }

    /**
     * 领取所有用户的奖励，然后清理奖励的过程数据
     */
    public void receiveAwardsForAllUser() {
        List<CurrentMingInfo> mingInfosList = new ArrayList<CurrentMingInfo>();
        if (depositUsers != null && depositUsers.size() > 0) {
            Iterator<DepositInfo> iter = depositUsers.values().iterator();
            while (iter.hasNext()) {
                DepositInfo depositInfo = iter.next();
                if (depositInfo != null) {
                    this.receive(depositInfo, false);
                }
            }
            //删除totalDepositList队列中所有数据，只保留最后两条记录，因为若有投资者刚加入，需要这两条数据
            RewardCycleInfo lastCycleInfo1 = totalDepositList.get(totalDepositList.size() - 2);
            RewardCycleInfo lastCycleInfo2 = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
            totalDepositList.clear();
            totalDepositIndex.clear();
            totalDepositList.add(lastCycleInfo1);
            totalDepositIndex.put(lastCycleInfo1.getRewardingCylce(), 01);
            totalDepositList.add(lastCycleInfo2);
            totalDepositIndex.put(this.lastCalcCycle, 1);
        }
    }

    /**
     * 由挖矿接收地址发起领取奖励;当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     *
     * @return
     */
    public void receiveAwardsForMiningAddress() {
        Address user = Msg.sender();
        MiningInfo info = mingUsers.get(user.toString());
        require(info != null, "没有替" + user.toString() + "用户抵押挖矿的挖矿信息");
        MiningDetailInfo detailInfo = info.getMiningDetailInfo();
        DepositInfo depositInfo = getDepositInfo(detailInfo.getDepositorAddress());
        List<CurrentMingInfo> mingInfosList = this.receive(depositInfo, true);
        if (mingInfosList != null && mingInfosList.size() > 0) {
            emit(new CurrentMiningInfoEvent(mingInfosList));
        }
    }

    public void receiveAwardsByAddress(Address user) {
        require(user != null, "请指定领取地址");
        MiningInfo info = mingUsers.get(user.toString());
        require(info != null, "没有" + user.toString() + "用户抵押挖矿的挖矿信息");
        DepositInfo depositInfo = getDepositInfo(user);
        List<CurrentMingInfo> mingInfosList = this.receive(depositInfo, true);
        if (mingInfosList != null && mingInfosList.size() > 0) {
            emit(new CurrentMiningInfoEvent(mingInfosList));
        }
    }

    /**
     * 统计未分配的Token数量 = 总Token数量-已分配Token-未领取Token数量
     *
     * @return
     */
    @View
    public String calcUnAllocationTokenAmount() {
        BigInteger result = BigInteger.ZERO;
        if (!isAllocationToken()) {
            return result.toString();
        }
        if (this.allocationAmount.compareTo(this.totalAllocation) == 0) {
            return result.toString();
        }

        if (isAcceptDeposit) {
            if (depositUsers != null && depositUsers.size() > 0) {
                BigInteger amountTmp = BigInteger.ZERO;
                Iterator<DepositInfo> iter = depositUsers.values().iterator();
                while (iter.hasNext()) {
                    DepositInfo depositInfo = iter.next();
                    if (depositInfo != null) {
                        amountTmp = amountTmp.add(this.calcUnReceiceMining(depositInfo, null));
                    }
                }
                result = this.totalAllocation.subtract(this.allocationAmount).subtract(amountTmp);
                if (result.compareTo(BigInteger.ZERO) < 0) {
                    result = BigInteger.ZERO;
                }
            } else {
                result = this.totalAllocation.subtract(this.allocationAmount);
            }
        } else {
            //Token可分配的数量已经不足
            result = BigInteger.ZERO;
        }

        return result.toString();
    }

    /**
     * 领取抵押者参与抵押的交易未领取的收益
     *
     * @param depositorAddress 抵押者账户地址
     * @param depositNumber    抵押编号，若为0表示计算所有抵押交易的收益
     * @return
     */
    @View
    public String calcUnReceiveAwards(@Required Address depositorAddress, String depositNumber) {
        String address = depositorAddress.toString();
        DepositInfo depositInfo = getDepositInfo(address);
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
        // this.whetherAcceptDeposit();

        BigInteger unReceiveAwards = this.calcUnReceiceMining(depositInfo, null);
        return unReceiveAwards.toString();
    }

    /**
     * @return 抵押者为自己抵押后未领取的收益
     */
    @View
    public String calcUnReceiveAwardsForOwner(@Required Address depositorAddress) {
        String address = depositorAddress.toString();
        DepositInfo depositInfo = getDepositInfo(address);
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
        // this.whetherAcceptDeposit();
        BigInteger unReceiveAwards = this.calcUnReceiceMining(depositInfo, address);
        return unReceiveAwards.toString();
    }


    /**
     * @return 由挖矿接收地址发起计算未领取的收益；当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     */
    @View
    public String calcUnReceiveAwardsForMiningAddress(@Required Address receiverMiningAddress) {
        BigInteger unReceiveAwards = BigInteger.ZERO;
        String address = receiverMiningAddress.toString();
        MiningInfo miningInfo = mingUsers.get(address);
        require(miningInfo != null, "没有替" + address + "用户抵押挖矿的挖矿信息");
        MiningDetailInfo detailInfo = miningInfo.getMiningDetailInfo();
        //在领取之前，如果已经不接收抵押了，则不再计算未领取的Token数量，已最后一次计算的未领取奖励token数
        //this.whetherAcceptDeposit();
        DepositInfo depositInfo = getDepositInfo(detailInfo.getDepositorAddress());
        unReceiveAwards = unReceiveAwards.add(this.calcUnReceiceMining(depositInfo, address));
        return unReceiveAwards.toString();
    }

    /**
     * 合约创建者清空剩余余额
     */
    public void clearContract() {
        onlyOwner();
        BigInteger balance = Msg.address().balance();
        require(balance.compareTo(ONE_NULS) <= 0, "余额不得大于1NULS");
        require(balance.compareTo(BigInteger.ZERO) > 0, "余额为零，无需清空");
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
    private List<CurrentMingInfo> receive(DepositInfo depositInfo, Boolean isTranfer) {
        Map<String, BigInteger> mingResult = new HashMap<String, BigInteger>();
        //预分配的Token已经奖励完，不再进行奖励计算
        if (allocationAmount.compareTo(totalAllocation) == 0) {
            return null;
        }
        // 奖励计算, 计算每次挖矿的高度是否已达到奖励减半周期的范围，若达到，则当次奖励减半，以此类推
        List<CurrentMingInfo> mingInfosList = this.calcMining(depositInfo, mingResult, isTranfer);
        if (isTranfer) {
            Set<Map.Entry<String, BigInteger>> entrySet = mingResult.entrySet();
            Iterator<Map.Entry<String, BigInteger>> iterator = entrySet.iterator();
            long currentTime = Block.timestamp();
            String lockedTime = String.valueOf(currentTime + this.lockedTokenDay * this.TIMEPERDAY);
            while (iterator.hasNext()) {
                Map.Entry<String, BigInteger> ming = iterator.next();
                BigInteger mingValue = ming.getValue();
                if (mingValue.compareTo(BigInteger.ZERO) > 0) {
                    String[][] args = new String[3][];
                    args[0] = new String[]{ming.getKey()};
                    args[1] = new String[]{mingValue.toString()};
                    args[2] = new String[]{lockedTime};
                    //tokenContractAddress.call("transfer","",args,BigInteger.ZERO);
                    tokenContractAddress.call("transferLocked", "", args, BigInteger.ZERO);
                    this.allocationAmount = this.allocationAmount.add(mingValue);
                }
            }
        }
        return mingInfosList;
    }

    /**
     * 计算奖励数额
     *
     * @param depositInfo
     * @param mingResult
     * @return
     */
    private List<CurrentMingInfo> calcMining(DepositInfo depositInfo, Map<String, BigInteger> mingResult, Boolean isTranfer) {
        List<CurrentMingInfo> mingInfosList = new ArrayList<CurrentMingInfo>();
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight, currentRewardCycle);
        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfo();
        MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
        MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfo();
        int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
        BigInteger miningTmp = BigInteger.ZERO;
        //达到领取奖励的高度
        if (nextStartMiningCycle <= currentRewardCycle) {
            //已经分配完毕
            if (this.isAcceptDeposit) {
                BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
                BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
                miningTmp = availableDepositAmountNULS.multiply(sumPrice).toBigInteger();
            }

            if (isTranfer) {
                //将后台领取但未发放的加上
                BigInteger miningValue = miningTmp.add(mingDetailInfo.getUnTranferReceivedMining());
                //Token数量不够分配
                if (miningValue.add(this.allocationAmount).compareTo(this.totalAllocation) >= 0) {
                    this.isAcceptDeposit = false;
                    miningValue = this.totalAllocation.subtract(this.allocationAmount);
                }
                int currentMiningCount = currentRewardCycle - nextStartMiningCycle + 1;
                CurrentMingInfo currentMingInfo = this.updateMingDetailInfo(mingDetailInfo, miningInfo, mingResult,
                        detailInfo.getDepositNumber(), miningValue, currentMiningCount, currentRewardCycle + 1);
                mingInfosList.add(currentMingInfo);
            } else {
                //后台领取但是不发放，暂时记录下来
                mingDetailInfo.setUnTranferReceivedMining(mingDetailInfo.getUnTranferReceivedMining().add(miningTmp));
                mingDetailInfo.setUnTranferMiningCount(mingDetailInfo.getUnTranferMiningCount() + currentRewardCycle - nextStartMiningCycle + 1);
                mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
                //封装当次的挖矿信息
                CurrentMingInfo currentMingInfo = new CurrentMingInfo();
                currentMingInfo.setDepositNumber(detailInfo.getDepositNumber());
                currentMingInfo.setMiningAmount(miningTmp);
                currentMingInfo.setMiningCount(currentRewardCycle - nextStartMiningCycle + 1);
                currentMingInfo.setReceiverMiningAddress(mingDetailInfo.getReceiverMiningAddress());
                mingInfosList.add(currentMingInfo);
            }
        } else if (isTranfer) {
            BigInteger miningValue = mingDetailInfo.getUnTranferReceivedMining();
            //如果未达到下一次领取周期，但是有未发放的奖励，说明后台刚刚触发了统一领取
            if (miningValue.compareTo(BigInteger.ZERO) > 0) {
                int currentMiningCount = 0;
                //下一次奖励的开始周期不变
                CurrentMingInfo currentMingInfo = this.updateMingDetailInfo(mingDetailInfo, miningInfo, mingResult,
                        detailInfo.getDepositNumber(), miningValue, currentMiningCount, mingDetailInfo.getNextStartMiningCycle());
                mingInfosList.add(currentMingInfo);
            }
        }
        return mingInfosList;
    }

    private CurrentMingInfo updateMingDetailInfo(MiningDetailInfo mingDetailInfo, MiningInfo miningInfo, Map<String, BigInteger> mingResult,
                                                 long depositNumber, BigInteger miningValue, int currentMiningCount, int nextStartMiningCycle) {
        CurrentMingInfo currentMingInfo = new CurrentMingInfo();
        mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(miningValue));
        mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount() + currentMiningCount + mingDetailInfo.getUnTranferMiningCount());
        mingDetailInfo.setNextStartMiningCycle(nextStartMiningCycle);

        miningInfo.setTotalMining(miningInfo.getTotalMining().add(miningValue));
        miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(miningValue));
        if (mingResult.containsKey(mingDetailInfo.getReceiverMiningAddress())) {
            miningValue = mingResult.get(mingDetailInfo.getReceiverMiningAddress()).add(miningValue);
        }
        mingResult.put(mingDetailInfo.getReceiverMiningAddress(), miningValue);

        //封装当次的挖矿信息
        currentMingInfo.setDepositNumber(depositNumber);
        currentMingInfo.setMiningAmount(miningValue);
        currentMingInfo.setMiningCount(currentMiningCount + mingDetailInfo.getUnTranferMiningCount());
        currentMingInfo.setReceiverMiningAddress(mingDetailInfo.getReceiverMiningAddress());

        //清理后台领取时的数据
        mingDetailInfo.setUnTranferMiningCount(0);
        mingDetailInfo.setUnTranferReceivedMining(BigInteger.ZERO);

        return currentMingInfo;
    }

    /**
     * 计算未获取的收益
     *
     * @param depositInfo
     * @param receiceAddress 接收奖励的地址,若为null表示计算所有接收奖励的地址
     * @return
     */
    private BigInteger calcUnReceiceMining(DepositInfo depositInfo, String receiceAddress) {
        BigInteger mining = BigInteger.ZERO;
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        int firstRewardCycle = this.calcRewardCycle(firstDepositHeight);
        if (currentRewardCycle <= firstRewardCycle) {
            //若距离第一笔抵押还未过1个奖励周期，则没有奖励可以领取，直接返回0
            return mining;
        }

        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight, currentRewardCycle);
        DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfo();
        //只计算指定address的收益
        if (receiceAddress != null && !detailInfo.getMiningAddress().equals(receiceAddress)) {
            return mining;
        }
        MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());

        MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfo();
        int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
        //说明未到领取奖励的高度
        if (nextStartMiningCycle > currentRewardCycle) {
            mining = mining.add(mingDetailInfo.getUnTranferReceivedMining());
            return mining;
        }
        BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
        BigDecimal availableDepositAmountNULS = toNuls(detailInfo.getAvailableAmount());
        BigInteger miningTmp = availableDepositAmountNULS.multiply(sumPrice).toBigInteger();
        mining = mining.add(miningTmp).add(mingDetailInfo.getUnTranferReceivedMining());
        return mining;
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
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        MiningInfo mingInfo = mingUsers.get(miningAddress);
        //该Token地址为第一次挖矿
        if (mingInfo == null) {
            mingInfo = new MiningInfo();
            MiningDetailInfo mingDetailInfo = new MiningDetailInfo(miningAddress, depositorAddress, depositNumber);
            mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
            mingInfo.setMiningDetailInfo(mingDetailInfo);
            mingUsers.put(miningAddress, mingInfo);
        } else {
            MiningDetailInfo mingDetailInfo = mingInfo.getMiningDetailInfo();
            mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
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
        //检查当前奖励周期的总抵押数是否在队列中
        if (!totalDepositIndex.containsKey(currentCycle)) {
            moveLastDepositToCurrentCycle(currentHeight, currentCycle);
        }
        int nextCycle = currentCycle + 1;
        boolean isContainsKey = totalDepositIndex.containsKey(nextCycle);
        if (!isContainsKey) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            //计算奖励减半
            long rewardingHeight = nextCycle * this.awardingCycle + this.createHeight;
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= rewardingHeight) {
                this.currentPrice = this.currentPrice.divide(this.HLAVING);
                this.nextRewardHalvingHeight += this.rewardHalvingCycle;
            }

            if (this.lastCalcCycle == 0) {
                cycleInfo.setAvailableDepositAmount(depositValue);
                cycleInfo.setRewardingCylce(nextCycle);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);

                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfo.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfo.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(cycleInfo.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN);
                }
                cycleInfo.setRewardBase(sumPrice);

                totalDepositList.add(cycleInfo);
            } else {
                RewardCycleInfo lastCycleInfo = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
                int differCycleValue = nextCycle - lastCycleInfo.getRewardingCylce();
                cycleInfo.setAvailableDepositAmount(depositValue.add(lastCycleInfo.getAvailableDepositAmount()));
                cycleInfo.setRewardingCylce(nextCycle);
                cycleInfo.setDifferCycleValue(differCycleValue);
                cycleInfo.setCurrentPrice(this.currentPrice);

                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfo.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfo.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(cycleInfo.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(differCycleValue));
                }
                cycleInfo.setRewardBase(lastCycleInfo.getRewardBase().add(sumPrice));

                totalDepositList.add(cycleInfo);
            }
            totalDepositIndex.put(nextCycle, totalDepositList.size() - 1);
            this.lastCalcCycle = nextCycle;
        } else {
            int alreadyTotalDepositIndex = totalDepositIndex.get(nextCycle);
            RewardCycleInfo cycleInfo = totalDepositList.get(alreadyTotalDepositIndex);
            cycleInfo.setAvailableDepositAmount(depositValue.add(cycleInfo.getAvailableDepositAmount()));

            BigDecimal sumPrice = BigDecimal.ZERO;
            BigDecimal privSumPrice = BigDecimal.ZERO;
            if (cycleInfo.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                BigDecimal amount = toNuls(cycleInfo.getAvailableDepositAmount());
                //为了提高计算的精确度，保留小数点后8位
                sumPrice = new BigDecimal(cycleInfo.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfo.getDifferCycleValue()));
            }
            if (totalDepositList.size() > 1) {
                RewardCycleInfo lastcycleInfo2 = totalDepositList.get(totalDepositList.size() - 2);
                privSumPrice = lastcycleInfo2.getRewardBase();
            }
            cycleInfo.setRewardBase(privSumPrice.add(sumPrice));
        }
    }

    /**
     * 将当前高度的奖励周期加入队列
     *
     * @param currentHeight
     */
    private void moveLastDepositToCurrentCycle(long currentHeight, int currentCycle) {
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
                int differCycleValue = currentCycle - cycleInfoTmp.getRewardingCylce();
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount());
                cycleInfo.setDifferCycleValue(differCycleValue);
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);

                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfoTmp.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfoTmp.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(cycleInfoTmp.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(differCycleValue));
                }
                cycleInfo.setRewardBase(cycleInfoTmp.getRewardBase().add(sumPrice));
            } else {
                cycleInfo.setAvailableDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);
                cycleInfo.setRewardBase(BigDecimal.ZERO);
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
            this.currentPrice = this.currentPrice.divide(this.HLAVING);
            rewardingCycle = this.calcRewardCycle(height);
            boolean isContainsKey = totalDepositIndex.containsKey(rewardingCycle);
            if (isContainsKey) {
                continue;
            }
            if (this.lastCalcCycle != 0) {
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(this.lastCalcCycle));
                int differCycleValue = rewardingCycle - cycleInfoTmp.getRewardingCylce();
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount());
                cycleInfo.setDifferCycleValue(differCycleValue);
                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfo.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfo.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(this.currentPrice).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(differCycleValue));
                }
                cycleInfo.setRewardBase(cycleInfoTmp.getRewardBase().add(sumPrice));
            } else {
                //第一次进行抵押操作
                cycleInfo.setAvailableDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setRewardBase(BigDecimal.ZERO);
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
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(currentCycle + 1));
            cycleInfoTmp.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));

            BigDecimal sumPrice = BigDecimal.ZERO;
            if (cycleInfoTmp.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                BigDecimal amount = toNuls(cycleInfoTmp.getAvailableDepositAmount());
                //为了提高计算的精确度，保留小数点后8位
                sumPrice = new BigDecimal(cycleInfoTmp.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfoTmp.getDifferCycleValue()));

                BigDecimal privSumPrice = BigDecimal.ZERO;
                if (totalDepositList.size() > 1) {
                    RewardCycleInfo lastcycleInfo2 = totalDepositList.get(totalDepositList.size() - 2);
                    privSumPrice = lastcycleInfo2.getRewardBase();
                }
                cycleInfoTmp.setRewardBase(privSumPrice.add(sumPrice));
            } else {
                cycleInfoTmp.setRewardBase(sumPrice);
            }
        } else {
            //加入抵押和退出抵押不在同一个奖励周期,则更新当前奖励周期的总抵押数
            boolean isContainsKey = totalDepositIndex.containsKey(currentCycle);
            //待操作的奖励周期已经计算了总抵押数
            if (isContainsKey) {
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositIndex.get(currentCycle));
                cycleInfoTmp.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));

                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfoTmp.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfoTmp.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(cycleInfoTmp.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfoTmp.getDifferCycleValue()));

                    BigDecimal privSumPrice = BigDecimal.ZERO;
                    if (totalDepositList.size() > 1) {
                        RewardCycleInfo lastcycleInfo2 = totalDepositList.get(totalDepositList.size() - 2);
                        privSumPrice = lastcycleInfo2.getRewardBase();
                    }
                    cycleInfoTmp.setRewardBase(privSumPrice.add(sumPrice));
                } else {
                    cycleInfoTmp.setRewardBase(sumPrice);
                }
            } else {
                //当前高度已经达到奖励减半高度,将所有的减半周期高度对于的奖励高度加入队列
                long nextHeight = currentHeight + this.awardingCycle;
                if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= nextHeight) {
                    this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, nextHeight);
                }
                RewardCycleInfo cycleInfo = new RewardCycleInfo();

                //取队列中最后一个奖励周期的信息
                RewardCycleInfo cycleInfoTmp = totalDepositList.get(totalDepositList.size() - 1);
                int differCycleValue = currentCycle - cycleInfoTmp.getRewardingCylce();
                cycleInfo.setAvailableDepositAmount(cycleInfoTmp.getAvailableDepositAmount().subtract(depositValue));
                cycleInfo.setDifferCycleValue(differCycleValue);
                cycleInfo.setCurrentPrice(currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);

                BigDecimal sumPrice = BigDecimal.ZERO;
                if (cycleInfo.getAvailableDepositAmount().compareTo(BigInteger.ZERO) > 0) {
                    BigDecimal amount = toNuls(cycleInfo.getAvailableDepositAmount());
                    //为了提高计算的精确度，保留小数点后8位
                    sumPrice = new BigDecimal(cycleInfo.getCurrentPrice()).divide(amount, 8, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(differCycleValue));
                    cycleInfo.setRewardBase(cycleInfoTmp.getRewardBase().add(sumPrice));
                } else {
                    cycleInfo.setRewardBase(sumPrice);
                }

                totalDepositList.add(cycleInfo);
                totalDepositIndex.put(currentCycle, totalDepositList.size() - 1);
                this.lastCalcCycle = currentCycle;
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
        int startIndex = totalDepositIndex.get(startCycle - 1);
        BigDecimal sumPriceEnd = totalDepositList.get(totalDepositList.size() - 1).getRewardBase();
        BigDecimal sumPriceStart = totalDepositList.get(startIndex).getRewardBase();
        return sumPriceEnd.subtract(sumPriceStart);
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
            return toMaxUit(cycleInfoTmp.getCurrentPrice().multiply(BigInteger.TEN.pow(8)).divide(intAmount), this.decimals).toPlainString() + " " + name() + "/NULS .";
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
    public int getLockedDay() {
        return this.lockedTokenDay;
    }

    @View
    public String version() {
        return VERSION;
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

    @View
    public String getMinJoinDeposit() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getMinJoinDeposit().toString();
    }

    /**
     * 查询合约当前所有信息
     */
    @View
    public String wholeConsensusInfo() {
        String totalDepositDetail = totalDepositDetail();
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDepositDetail\":")
                .append('\"').append(totalDepositDetail).append('\"');
        if (openConsensus) {
            sb.append(",\"consensusManager\":")
                    .append(consensusManager == null ? "0" : consensusManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }


    /**
     * 在抵押期间，更新合约可分配的Token数量
     */
    public void updateTotalAllocation() {
        String[][] args = new String[1][];
        args[0] = new String[]{Msg.address().toString()};
        String balance = tokenContractAddress.callWithReturnValue("balanceOf", "", args, BigInteger.ZERO);
        //合约持有的Token加上已经分配的Token=该合约总共可分配的Token
        totalAllocation = this.allocationAmount.add(new BigInteger(balance));
        if (this.totalAllocation.compareTo(this.allocationAmount) > 0) {
            this.isAcceptDeposit = true;
        } else {
            this.isAcceptDeposit = false;
        }
    }


    /**
     * 检查是否给合约分配的token
     */
    private boolean isAllocationToken() {
        if (!isGetTotal) {
            String[][] args = new String[1][];
            args[0] = new String[]{Msg.address().toString()};
            String balance = tokenContractAddress.callWithReturnValue("balanceOf", "", args, BigInteger.ZERO);
            totalAllocation = new BigInteger(balance);
            if (totalAllocation.compareTo(BigInteger.ZERO) > 0) {
                isGetTotal = true;
                isAcceptDeposit = true;
            }
        }
        return isGetTotal;
    }

    /**
     * 大致检查是否已经分配完毕
     *
     * @return
     */
    private boolean isAcceptDeposit() {
        return isAcceptDeposit;
    }

    /**
     * 获取给合约分配的token数量
     */
    @View
    public String getTotalAllocation() {
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
