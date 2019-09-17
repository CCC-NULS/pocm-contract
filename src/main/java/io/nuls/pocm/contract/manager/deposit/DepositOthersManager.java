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
package io.nuls.pocm.contract.manager.deposit;

import io.nuls.contract.sdk.Utils;
import io.nuls.pocm.contract.model.AgentInfo;
import io.nuls.pocm.contract.model.ConsensusDepositInfo;

import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.manager.ConsensusManager.MAX_TOTAL_DEPOSIT;
import static io.nuls.pocm.contract.manager.ConsensusManager.MIN_JOIN_DEPOSIT;
import static io.nuls.pocm.contract.util.PocmUtil.toNuls;

/**
 * @author: PierreLuo
 * @date: 2019-06-25
 */
public class DepositOthersManager {
    /**
     * 在节点不能运行时，委托到其他节点，即抵押的金额不足20W之前，委托到其他节点
     */
    private Map<String, AgentInfo> otherAgents;

    // 委托信息列表
    private LinkedList<ConsensusDepositInfo> depositList = new LinkedList<ConsensusDepositInfo>();
    // 委托其他节点的锁定金额
    private BigInteger depositLockedAmount = BigInteger.ZERO;

    public DepositOthersManager() {
        otherAgents = new HashMap<String, AgentInfo>();
    }

    public int otherAgentsSize() {
        return otherAgents.size();
    }

    public Set<String> getAgents() {
        int size = otherAgents.size();
        if(size == 0) {
            return null;
        }
        return otherAgents.keySet();
    }

    public BigInteger otherDepositLockedAmount() {
        return depositLockedAmount;
    }

    public String[] addOtherAgent(String agentHash) {
        require(!otherAgents.containsKey(agentHash), "重复的共识节点hash");
        otherAgents.put(agentHash, new AgentInfo());
        Object agentInfo = Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
        require(agentInfo != null, "无效的共识节点hash");
        return (String[]) agentInfo;
    }

    public BigInteger removeAgent(String agentHash) {
        BigInteger withdrawAmount = BigInteger.ZERO;
        require(otherAgents.containsKey(agentHash), "节点不存在");
        Object agentInfo = Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
        require(agentInfo != null, "节点不存在");
        if (agentInfo == null ){
            return BigInteger.ZERO;
        }
        String[] agent = (String[])agentInfo;
        boolean isEnabled = "-1".equals(agent[8]);
        //退出节点委托
        Iterator<ConsensusDepositInfo> iterator = depositList.iterator();
        while (iterator.hasNext()){
            ConsensusDepositInfo depositInfo = iterator.next();
            if(agentHash.equals(depositInfo.getAgentHash())){
                if(isEnabled){
                    this.withdraw(depositInfo);
                }else{
                    depositLockedAmount = depositLockedAmount.subtract(depositInfo.getDeposit());
                }
                iterator.remove();
                withdrawAmount = withdrawAmount.add(depositInfo.getDeposit());
            }
        }
        //清除委托节点
        otherAgents.remove(agentHash);
        return withdrawAmount;
    }

    public BigInteger deposit(BigInteger availableAmount) {
        int size = otherAgents.size();
        if(size == 0) {
            // 没有其他节点的共识信息，跳过此流程
            return BigInteger.ZERO;
        }
        BigInteger actualDeposit = BigInteger.ZERO;
        String[] agentInfo;
        Set<Map.Entry<String, AgentInfo>> entries = otherAgents.entrySet();
        for(Map.Entry<String, AgentInfo> entry : entries) {
            String agentHash = entry.getKey();
            AgentInfo agent = entry.getValue();
            agentInfo = (String[]) Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
            // 合约节点已委托金额
            BigInteger totalDeposit = this.moreDeposits(agent, new BigInteger(agentInfo[4]));
            BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
            if(currentAvailable.compareTo(availableAmount) >= 0) {
                this.deposit(agentHash, availableAmount, agent);
                actualDeposit = actualDeposit.add(availableAmount);
                break;
            } else if(currentAvailable.compareTo(MIN_JOIN_DEPOSIT) >= 0){
                this.deposit(agentHash, currentAvailable, agent);
                actualDeposit = actualDeposit.add(currentAvailable);
                availableAmount = availableAmount.subtract(currentAvailable);
            }
        }
        // 当所有节点(超过1个)都未激活时，选第一个节点委托
        return actualDeposit;
    }

    private BigInteger depositAvailable(String agentHash, AgentInfo agent, BigInteger availableAmount, BigInteger totalDepositFromCmd) {
        BigInteger totalDeposit = this.moreDeposits(agent, totalDepositFromCmd);
        BigInteger actualDeposit = BigInteger.ZERO;
        BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
        if(currentAvailable.compareTo(availableAmount) >= 0) {
            this.deposit(agentHash, availableAmount, agent);
            actualDeposit = actualDeposit.add(availableAmount);
        } else if(currentAvailable.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            this.deposit(agentHash, currentAvailable, agent);
            actualDeposit = actualDeposit.add(currentAvailable);
        }
        return actualDeposit;
    }

    private BigInteger moreDeposits(AgentInfo agent, BigInteger totalDepositFromCmd) {
        BigInteger agentDeposits = agent.getAgentDeposits();
        BigInteger total;
        if(agentDeposits.compareTo(totalDepositFromCmd) > 0) {
            total = agentDeposits;
        } else {
            total = totalDepositFromCmd;
        }
        return total;
    }

    /**
     * @param expectWithdrawAmount 期望退出的金额
     * @return actualWithdrawAmount 实际退出的金额(始终大于或等于期望值)
     */
    public BigInteger withdraw(BigInteger expectWithdrawAmount) {
        BigInteger actualWithdraw = BigInteger.ZERO;
        // 退出所有委托
        if(expectWithdrawAmount.compareTo(depositLockedAmount) >= 0) {
            actualWithdraw = depositLockedAmount;
            for(ConsensusDepositInfo info : depositList) {
                this.withdraw(info);
            }
            depositLockedAmount = BigInteger.ZERO;
            depositList.clear();
            return actualWithdraw;
        } else {
            BigInteger withdrawAmount;
            while (!expectWithdrawAmount.equals(BigInteger.ZERO)){
                withdrawAmount = withdrawRecursion(expectWithdrawAmount);
                actualWithdraw = actualWithdraw.add(withdrawAmount);
                if(withdrawAmount.compareTo(expectWithdrawAmount) >= 0){
                    expectWithdrawAmount = BigInteger.ZERO;
                }else{
                    expectWithdrawAmount = expectWithdrawAmount.subtract(withdrawAmount);
                }
            }
        }
        return actualWithdraw;
    }


    private String deposit(String agentHash, BigInteger depositNa, AgentInfo agent) {
        String[] args = new String[]{agentHash, depositNa.toString()};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractDeposit", args);
        this.orderlyAdditionToDepositList(new ConsensusDepositInfo(agentHash, txHash, depositNa));
        depositLockedAmount = depositLockedAmount.add(depositNa);
        agent.add(depositNa);
        return txHash;
    }

    private String withdraw(ConsensusDepositInfo info) {
        String joinAgentHash = info.getHash();
        String[] args = new String[]{joinAgentHash};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractWithdraw", args);
        depositLockedAmount = depositLockedAmount.subtract(info.getDeposit());
        AgentInfo agent = otherAgents.get(info.getAgentHash());
        agent.subtract(info.getDeposit());
        return txHash;
    }

    private BigInteger withdrawRecursion(BigInteger expectWithdrawAmount){
        ConsensusDepositInfo maxDeposit = depositList.getLast();
        BigInteger realWithdrawAmount = BigInteger.ZERO;
        //当退出金额大于最大的委托则退出最大委托；否则从小到大遍历找到大于退出金额的第一条委托记录
        if(expectWithdrawAmount.compareTo(maxDeposit.getDeposit()) >= 0){
            depositList.removeLast();
            this.withdraw(maxDeposit);
            realWithdrawAmount = maxDeposit.getDeposit();
        }else{
            // 找出最小的一笔退出抵押的金额（使闲置金额最小）
            for(Iterator<ConsensusDepositInfo> iterator = depositList.iterator(); iterator.hasNext();){
                ConsensusDepositInfo info = iterator.next();
                if(info.getDeposit().compareTo(expectWithdrawAmount) >= 0) {
                    this.withdraw(info);
                    realWithdrawAmount = info.getDeposit();
                    iterator.remove();
                    break;
                }
            }
        }
        return realWithdrawAmount;
    }

    /**
     * 按金额升序
     */
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
            if (result < 0) {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"otherAgents\":");
        sb.append('[');
        Set<Map.Entry<String, AgentInfo>> entries = otherAgents.entrySet();
        for(Map.Entry<String, AgentInfo> entry : entries) {
            String hash = entry.getKey();
            AgentInfo info = entry.getValue();

            sb.append("{\"hash\":\"").append(hash).append('\"').append(",");
            sb.append("\"agentDeposits\":").append('\"').append(info.getAgentDeposits().toString()).append("\"},");
        }
        if (otherAgents.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(']');

        sb.append(",\"depositList\":");
        sb.append('[');
        for (ConsensusDepositInfo info : depositList) {
            sb.append(info.toString()).append(',');
        }
        if (depositList.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(']');

        sb.append(",\"depositLockedAmount\":")
                .append('\"').append(toNuls(depositLockedAmount).toPlainString()).append('\"');
        sb.append('}');
        return sb.toString();
    }
}
