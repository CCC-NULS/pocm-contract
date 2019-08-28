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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.manager.ConsensusManager.*;
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

    public BigInteger otherDepositLockedAmount() {
        return depositLockedAmount;
    }

    public void addOtherAgent(String agentHash) {
        require(!otherAgents.containsKey(agentHash), "重复的共识节点hash");
        otherAgents.put(agentHash, new AgentInfo());
        Object agentInfo = Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
        require(agentInfo != null, "无效的共识节点hash");
    }

    public BigInteger deposit(BigInteger availableAmount) {
        int size = otherAgents.size();
        if(size == 0) {
            // 没有其他节点的共识信息，跳过此流程
            return BigInteger.ZERO;
        }
        BigInteger actualDeposit = BigInteger.ZERO;
        String[] agentInfo;
        String[] firstAgentInfo = null;
        String firstAgentHash = null;
        AgentInfo firstAgent = null;
        // 选择一个可委托金额足够的共识节点
        boolean allInActive = true;
        int i = 0;
        Set<Map.Entry<String, AgentInfo>> entries = otherAgents.entrySet();
        for(Map.Entry<String, AgentInfo> entry : entries) {
            String agentHash = entry.getKey();
            AgentInfo agent = entry.getValue();
            i++;
            agentInfo = (String[]) Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
            if(i == 1) {
                firstAgentHash = agentHash;
                firstAgentInfo = agentInfo;
                firstAgent = agent;
            }
            // 0-待共识 1-共识中
            String status = agentInfo[9];
            //emit(new ErrorEvent("status", status));
            if(!ACTIVE_AGENT.equals(status) && size != 1) {
                continue;
            }
            allInActive = false;
            // 合约节点已委托金额
            BigInteger totalDeposit = this.moreDeposits(agent, new BigInteger(agentInfo[4]));
            BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
            if(currentAvailable.compareTo(availableAmount) >= 0) {
                this.deposit(agentHash, availableAmount, agent);
                actualDeposit = actualDeposit.add(availableAmount);
                break;
            } else {
                this.deposit(agentHash, currentAvailable, agent);
                actualDeposit = actualDeposit.add(currentAvailable);
                availableAmount = availableAmount.subtract(currentAvailable);
            }
        }
        // 当所有节点(超过1个)都未激活时，选第一个节点委托
        if(allInActive) {
            actualDeposit = this.depositAvailable(firstAgentHash, firstAgent, availableAmount, new BigInteger(firstAgentInfo[4]));
        }
        return actualDeposit;
    }

    private BigInteger depositAvailable(String agentHash, AgentInfo agent, BigInteger availableAmount, BigInteger totalDepositFromCmd) {
        BigInteger totalDeposit = this.moreDeposits(agent, totalDepositFromCmd);
        BigInteger actualDeposit = BigInteger.ZERO;
        BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
        if(currentAvailable.compareTo(availableAmount) >= 0) {
            this.deposit(agentHash, availableAmount, agent);
            actualDeposit = actualDeposit.add(availableAmount);
        } else {
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
            for(ConsensusDepositInfo info : depositList) {
                this.withdraw(info);
            }
            actualWithdraw = depositLockedAmount;
            depositLockedAmount = BigInteger.ZERO;
            depositList.clear();
            return actualWithdraw;
        } else {
            // 退出部分委托，以达到期望值
            ConsensusDepositInfo last = depositList.getLast();
            BigInteger remain = last.getDeposit().subtract(expectWithdrawAmount);
            // 先找最大的委托，若在退出抵押后剩余大于2000个，退出这笔委托，再把剩余的委托进去
            if(remain.compareTo(MIN_JOIN_DEPOSIT) >= 0 ) {
                depositList.removeFirst();
                this.withdraw(last);
                // 剩下的再次委托
                String lastAgentHash = last.getAgentHash();
                AgentInfo agent = otherAgents.get(lastAgentHash);
                this.deposit(lastAgentHash, remain, agent);
                actualWithdraw = expectWithdrawAmount;
                return actualWithdraw;
            } else if (remain.compareTo(BigInteger.ZERO) >= 0) {
                // 找出最小的一笔退出抵押的金额（使闲置金额最小）
                for(ConsensusDepositInfo info : depositList) {
                    if(info.getDeposit().compareTo(expectWithdrawAmount) >= 0) {
                        this.withdraw(info);
                        actualWithdraw = expectWithdrawAmount;
                        return actualWithdraw;
                    }
                }
            } else {
                // 从小到大退出委托，直到满足抵押金额
                while(actualWithdraw.compareTo(expectWithdrawAmount) < 0) {
                    ConsensusDepositInfo info = depositList.removeFirst();
                    this.withdraw(info);
                    actualWithdraw = actualWithdraw.add(info.getDeposit());
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
        return txHash;
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
