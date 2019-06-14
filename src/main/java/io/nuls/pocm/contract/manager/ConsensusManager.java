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
package io.nuls.pocm.contract.manager;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Block;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.Utils;
import io.nuls.pocm.contract.model.ConsensusAwardInfo;
import io.nuls.pocm.contract.model.ConsensusDepositInfo;
import io.nuls.pocm.contract.model.ConsensusTakeBackUnLockDepositInfo;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.util.PocmUtil.toNuls;

/**
 * @author: PierreLuo
 * @date: 2019-05-14
 */
public class ConsensusManager {

    private final BigInteger minCreateDeposit = BigInteger.valueOf(2000000000000L);
    private final BigInteger maxCreateDeposit = BigInteger.valueOf(20000000000000L);
    private final BigInteger minJoinDeposit = BigInteger.valueOf(200000000000L);
    private final BigInteger maxTotalDeposit = BigInteger.valueOf(50000000000000L);

    private String lastAgentHash;
    private BigInteger agentDeposit = BigInteger.ZERO;
    private LinkedList<ConsensusDepositInfo> depositList = new LinkedList<ConsensusDepositInfo>();
    private String packingAddress;
    private BigInteger availableAmount = BigInteger.ZERO;
    private BigInteger depositLockedAmount = BigInteger.ZERO;
    private BigInteger tempDepositLockedAmount = BigInteger.ZERO;
    private ConsensusAwardInfo awardInfo;
    // lock 3.5 days
    private final long lockConsensusTime = 302400L;
    // lock 3 days
    private final long lockAgentDepositTime = 259200L;
    private long unlockConsensusTime = -1L;
    private long unlockAgentDepositTime = -1L;
    // 初始化共识管理器
    private boolean isReset = false;
    // 等待退还解锁的押金用户集合
    private Map<String, ConsensusTakeBackUnLockDepositInfo> takeBackUnLockDepositMap = new HashMap<String, ConsensusTakeBackUnLockDepositInfo>();
    private boolean hasCreate = false;
    private boolean hasStop = false;
    // 等待解锁退还的押金总额
    private BigInteger totalTakeBackLockDeposit = BigInteger.ZERO;
    // 项目发布者缴纳的创建节点保证金
    private BigInteger ownerCreateAgentDeposit = BigInteger.ZERO;

    private String lastWithdrawHash;
    private String lastStopHash;

    public ConsensusManager(Address packingAddress) {
        awardInfo = new ConsensusAwardInfo(Msg.address().toString());
        this.packingAddress = packingAddress.toString();
        isReset = false;
    }

    /**
     * 共识奖励收益处理
     * 创建的节点的100%佣金比例，收益地址只有当前合约地址
     *
     * @param args 区块奖励地址明细 eg. [[address, amount], [address, amount], ...]
     */
    public void _payable(String[][] args) {
        String[] award = args[0];
        String address = award[0];
        String amount = award[1];
        awardInfo.add(new BigInteger(amount));
    }

    /**
     * @param value 项目发布者向合约转入NULS，提供保证金来创建节点
     */
    public void createAgentByOwner(BigInteger value) {
        require(this.isUnLockedConsensus(), "共识功能锁定中");
        require(!hasCreate, "共识节点已经创建");
        if (ownerCreateAgentDeposit.compareTo(minCreateDeposit) >= 0) {
            // 有足够的保证金，退还转入的NULS
            Msg.sender().transfer(value);
            this.createAgent(packingAddress, ownerCreateAgentDeposit, "100");
            agentDeposit = ownerCreateAgentDeposit;
        } else {
            require(value.compareTo(minCreateDeposit) >= 0, "创建节点保证金不得小于20000NULS");
            ownerCreateAgentDeposit = value;
            this.createAgent(packingAddress, ownerCreateAgentDeposit, "100");
            agentDeposit = ownerCreateAgentDeposit;
        }
    }

    /**
     * 注销节点后，共识功能将锁定3.5天，以此判断是否已解锁
     */
    public boolean isUnLockedConsensus() {
        if (unlockConsensusTime == -1L) {
            return true;
        }
        return Block.timestamp() > unlockConsensusTime;
    }

    /**
     * 注销节点后，创建共识的保证金将锁定3天，以此判断创建共识的保证金是否已解锁
     */
    public boolean isUnLockedAgentDeposit() {
        if (unlockAgentDepositTime == -1L) {
            return true;
        }
        return Block.timestamp() > unlockAgentDepositTime;
    }

    /**
     * 增加了押金后，押金数额达到条件后，若没有节点，则创建节点，若有节点，则委托节点
     * 押金数额未达到条件，则累计总可用押金数额
     *
     * @param value          投资的押金
     * @param currentInitial 是否在当前重置了可用余额，如果重置了，则不需要再计算value
     */
    public void createOrDepositIfPermittedWrapper(BigInteger value, boolean currentReset) {
        tempDepositLockedAmount = depositLockedAmount;
        this.createOrDepositIfPermitted(value, currentReset);
    }

    private void createOrDepositIfPermitted(BigInteger value, boolean currentReset) {
        BigInteger deposit;
        // 初始化时，value已经添加到了availableAmount
        if (!currentReset) {
            availableAmount = availableAmount.add(value);
        }
        // 存在创建节点，检查节点状态
        if (hasCreate) {
            String[] args = new String[]{lastAgentHash};
            String[] info = (String[]) Utils.invokeExternalCmd("cs_getContractAgentInfo", args);
            String delHeight = info[8];
            // 已删除节点，不再自动创建
            if (!"-1".equals(delHeight)) {
                return;
            }
            BigInteger amount = availableAmount;
            // 金额不够委托，退出上一笔委托，累积委托金额加入委托
            if (amount.compareTo(minJoinDeposit) < 0) {
                if (depositList.size() == 0) {
                    return;
                }
                ConsensusDepositInfo last = depositList.removeLast();
                String withdrawHash = this.withdraw(last.getHash());
                BigInteger lastDeposit = last.getDeposit();
                availableAmount = availableAmount.add(lastDeposit);
                depositLockedAmount = depositLockedAmount.subtract(lastDeposit);
                deposit = amount.add(lastDeposit);
            } else {
                deposit = amount;
            }
            // 委托
            this.maintainCanDeposit(deposit);
        } else {
            // 检查可用金额是否足以创建节点
            BigInteger amount = availableAmount;
            if (amount.compareTo(minCreateDeposit) < 0) {
                return;
            }
            // 使用2万保证金，剩余金额下次加入
            deposit = minCreateDeposit;
            this.createAgent(packingAddress, deposit, "100");
            agentDeposit = deposit;
            availableAmount = availableAmount.subtract(deposit);
            depositLockedAmount = depositLockedAmount.add(deposit);
        }
    }

    /**
     * 当可用金额达到最小可委托金额时，合约拥有者可手动委托节点
     */
    public void depositManually() {
        require(this.isUnLockedConsensus(), "共识功能锁定中");
        require(hasCreate, "未创建节点");
        tempDepositLockedAmount = depositLockedAmount;
        BigInteger amount = availableAmount;
        require(amount.compareTo(minJoinDeposit) >= 0, "可用金额不足以委托节点");
        // 委托
        this.maintainCanDeposit(amount);
    }

    /**
     * 如果合约余额不足，则退出委托，直到余额足以退还押金
     *
     * @param value 需要退还的押金
     * @return true - 退出委托后余额足够, false - 退出委托，注销节点，余额被锁定一部分(3天)，可用余额不足以退还押金
     */
    public boolean withdrawIfPermittedWrapper(BigInteger value) {
        tempDepositLockedAmount = depositLockedAmount;
        return this.withdrawIfPermitted(value);
    }

    private boolean withdrawIfPermitted(BigInteger value) {
        if (availableAmount.compareTo(value) >= 0) {
            availableAmount = availableAmount.subtract(value);
            return true;
        }
        // 可用金额在退出所有委托后还不足，注销节点
        if (depositList.size() == 0) {
            if (hasStop) {
                return false;
            }
            this.stopAgent();
            // 清除共识数据，共识功能锁定3.5天重新初始化
            this.lockConsensus();
            return false;
        }
        ConsensusDepositInfo last = depositList.removeLast();
        String withdrawHash = this.withdraw(last.getHash());
        BigInteger deposit = last.getDeposit();
        availableAmount = availableAmount.add(deposit);
        depositLockedAmount = depositLockedAmount.subtract(deposit);
        if (availableAmount.compareTo(value) < 0) {
            return withdrawIfPermitted(value);
        } else {
            availableAmount = availableAmount.subtract(value);
            // 若可用金额足够，则继续委托进去
            BigInteger amount = availableAmount;
            if (amount.compareTo(minJoinDeposit) >= 0) {
                // 委托
                this.maintainCanDeposit(amount);
            }
            return true;
        }
    }

    /**
     * 检查是否重置，若没有，则重置
     */
    public boolean checkCurrentReset() {
        if (isReset) {
            return false;
        }
        // 除去共识收益，除去等待解锁退还的押金总额，项目发布者缴纳的创建节点保证金
        availableAmount = Msg.address().balance()
                .subtract(awardInfo.getAvailableAward())
                .subtract(totalTakeBackLockDeposit);
        // 创建节点后，保证金被锁定，不在可用余额范围中
        if (!hasCreate) {
            availableAmount = availableAmount.subtract(ownerCreateAgentDeposit);
        }
        isReset = true;
        hasStop = false;
        return true;
    }

    /**
     * 记录用户退出时，锁定的押金，用于押金解锁时退还给用户
     */
    public void recordTakeBackLockDeposit(String userString, BigInteger deposit) {
        ConsensusTakeBackUnLockDepositInfo takeBackDepositInfo = takeBackUnLockDepositMap.get(userString);
        if (takeBackDepositInfo == null) {
            takeBackDepositInfo = new ConsensusTakeBackUnLockDepositInfo(deposit);
            takeBackUnLockDepositMap.put(userString, takeBackDepositInfo);
        } else {
            takeBackDepositInfo.setDeposit(takeBackDepositInfo.getDeposit().add(deposit));
        }
        // 累计 等待解锁退还的押金总额
        totalTakeBackLockDeposit = totalTakeBackLockDeposit.add(deposit);
    }

    /**
     * 共识保证金解锁后，退还申请过退出的用户的押金
     */
    public void takeBackUnLockDeposit() {

        Address sender = Msg.sender();
        String senderString = sender.toString();
        ConsensusTakeBackUnLockDepositInfo takeBackDeposit = takeBackUnLockDepositMap.remove(senderString);
        require(takeBackDeposit != null, "没有查询到[" + senderString + "]的解锁押金");
        BigInteger deposit = takeBackDeposit.getDeposit();
        require(deposit.compareTo(BigInteger.ZERO) > 0, "[" + senderString + "]没有足够的押金");
        totalTakeBackLockDeposit = totalTakeBackLockDeposit.subtract(deposit);
        sender.transfer(deposit);
    }


    /**
     * 共识保证金解锁后，退还所有申请过退出的用户的押金
     */
    public void refundAllUnLockDeposit() {
        require(this.isUnLockedAgentDeposit(), "押金锁定中");
        require(takeBackUnLockDepositMap.size() > 0, "无退还信息");
        Set<Map.Entry<String, ConsensusTakeBackUnLockDepositInfo>> entries = takeBackUnLockDepositMap.entrySet();
        BigInteger deposit;
        for (Map.Entry<String, ConsensusTakeBackUnLockDepositInfo> entry : entries) {
            deposit = entry.getValue().getDeposit();
            totalTakeBackLockDeposit = totalTakeBackLockDeposit.subtract(deposit);
            new Address(entry.getKey()).transfer(deposit);
        }
        takeBackUnLockDepositMap.clear();
    }

    /**
     * 转移共识奖励金额
     */
    public void transferConsensusReward(Address beneficiary) {
        BigInteger availableAward = awardInfo.getAvailableAward();
        require(availableAward.compareTo(BigInteger.ZERO) > 0, "无可用的共识奖励金额");
        // 清零
        awardInfo.resetAvailableAward();
        beneficiary.transfer(availableAward);
    }

    /**
     * 可转移的共识奖励
     */
    public BigInteger getAvailableConsensusReward() {
        return awardInfo.getAvailableAward();
    }

    /**
     * 赎回保证金
     */
    public void takeBackCreateAgentDeposit(Address beneficiary) {
        require(ownerCreateAgentDeposit.compareTo(BigInteger.ZERO) > 0, "无可用的创建共识保证金");
        require(!hasCreate, "保证金抵押中，请注销节点三天后领取");
        require(isUnLockedAgentDeposit(), "保证金锁定中");
        beneficiary.transfer(ownerCreateAgentDeposit);
        ownerCreateAgentDeposit = BigInteger.ZERO;
        agentDeposit = BigInteger.ZERO;
    }

    /**
     * 手动注销节点
     */
    public void stopAgentManually() {
        require(this.isUnLockedConsensus(), "共识功能锁定中");
        require(hasCreate, "未创建节点");
        this.stopAgent();
        this.lockConsensus();
    }

    /**
     * 获取可委托共识的空闲金额
     */
    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    /**
     * 获取创建节点的hash
     */
    public String getAgentHash() {
        return lastAgentHash;
    }

    private String createAgent(String packingAddress, BigInteger depositNa, String commissionRate) {
        String[] args = new String[]{packingAddress, depositNa.toString(), commissionRate};
        String txHash = (String) Utils.invokeExternalCmd("cs_createContractAgent", args);
        lastAgentHash = txHash;
        hasCreate = true;
        return txHash;
    }

    private String deposit(String agentHash, BigInteger depositNa) {
        String[] args = new String[]{agentHash, depositNa.toString()};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractDeposit", args);
        this.orderlyAdditionToDepositList(new ConsensusDepositInfo(txHash, depositNa));
        return txHash;
    }

    private String withdraw(String joinAgentHash) {
        String[] args = new String[]{joinAgentHash};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractWithdraw", args);
        lastWithdrawHash = txHash;
        return txHash;
    }

    private String stopAgent() {
        String txHash = (String) Utils.invokeExternalCmd("cs_stopContractAgent", null);
        lastStopHash = txHash;
        hasStop = true;
        hasCreate = false;
        return txHash;
    }

    private void orderlyAdditionToDepositList(ConsensusDepositInfo info) {
        BigInteger deposit = info.getDeposit();
        int size = depositList.size();
        if (size == 0) {
            depositList.add(info);
            return;
        }
        BigInteger compare;
        int result;
        int last = size - 1;
        for (int i = 0; i < size; i++) {
            compare = depositList.get(i).getDeposit();
            result = compare.compareTo(deposit);
            if (result > 0) {
                if (i == last) {
                    depositList.addLast(info);
                    break;
                }
                continue;
            } else if (result == 0) {
                depositList.add(i + 1, info);
                break;
            } else {
                depositList.add(i, info);
                break;
            }
        }
    }

    /**
     * 如果本次委托金额和当前已委托金额累加值大于最大限额，就委托不超过限额的那部分(不能连续交易的补救方式)
     * @param amount 本次委托金额
     */
    private void maintainCanDeposit(BigInteger amount) {
        BigInteger canDepoist = amount;
        if(canDepoist.add(tempDepositLockedAmount).compareTo(maxTotalDeposit) > 0) {
            canDepoist = maxTotalDeposit.subtract(tempDepositLockedAmount);
        }
        if(canDepoist.compareTo(minJoinDeposit) < 0) {
            return;
        }
        this.deposit(lastAgentHash, canDepoist);
        availableAmount = availableAmount.subtract(canDepoist);
        depositLockedAmount = depositLockedAmount.add(canDepoist);
    }

    /**
     * 锁定共识功能
     */
    private void lockConsensus() {
        unlockConsensusTime = Block.timestamp() + lockConsensusTime;
        unlockAgentDepositTime = Block.timestamp() + lockAgentDepositTime;
        availableAmount = BigInteger.ZERO;
        depositLockedAmount = BigInteger.ZERO;
        depositList.clear();
        isReset = false;
    }

    public BigInteger getTotalTakeBackLockDeposit() {
        return totalTakeBackLockDeposit;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"lastAgentHash\":")
                .append('\"').append(lastAgentHash).append('\"');
        sb.append(",\"agentDeposit\":")
                .append('\"').append(toNuls(agentDeposit).toPlainString()).append('\"');
        sb.append(",\"depositList\":");

        sb.append('[');
        for (ConsensusDepositInfo info : depositList) {
            sb.append(info.toString()).append(',');
        }
        if (depositList.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(']');

        sb.append(",\"packingAddress\":")
                .append('\"').append(packingAddress).append('\"');
        sb.append(",\"availableAmount\":")
                .append('\"').append(toNuls(availableAmount).toPlainString()).append('\"');
        sb.append(",\"depositLockedAmount\":")
                .append('\"').append(toNuls(depositLockedAmount).toPlainString()).append('\"');
        sb.append(",\"tempDepositLockedAmount\":")
                .append('\"').append(toNuls(tempDepositLockedAmount).toPlainString()).append('\"');
        sb.append(",\"awardInfo\":")
                .append(awardInfo.toString());
        sb.append(",\"unlockConsensusTime\":")
                .append(unlockConsensusTime);
        sb.append(",\"unlockAgentDepositTime\":")
                .append(unlockAgentDepositTime);
        sb.append(",\"isReset\":")
                .append(isReset);
        sb.append(",\"takeBackUnLockDepositMap\":");

        sb.append('{');
        Set<Map.Entry<String, ConsensusTakeBackUnLockDepositInfo>> entries = takeBackUnLockDepositMap.entrySet();
        for (Map.Entry<String, ConsensusTakeBackUnLockDepositInfo> entry : entries) {
            sb.append('\"').append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toString()).append(',');
        }
        if (takeBackUnLockDepositMap.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append('}');

        sb.append(",\"hasCreate\":")
                .append(hasCreate);
        sb.append(",\"hasStop\":")
                .append(hasStop);
        sb.append(",\"totalTakeBackLockDeposit\":")
                .append('\"').append(toNuls(totalTakeBackLockDeposit).toPlainString()).append('\"');
        sb.append(",\"ownerCreateAgentDeposit\":")
                .append('\"').append(toNuls(ownerCreateAgentDeposit).toPlainString()).append('\"');
        sb.append(",\"lastWithdrawHash\":")
                .append('\"').append(lastWithdrawHash).append('\"');
        sb.append(",\"lastStopHash\":")
                .append('\"').append(lastStopHash).append('\"');
        sb.append('}');
        return sb.toString();
    }

}
