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
import io.nuls.pocm.contract.model.ConsensusDepositInfo;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.manager.ConsensusManager.ACTIVE_AGENT;
import static io.nuls.pocm.contract.manager.ConsensusManager.MAX_TOTAL_DEPOSIT;
import static io.nuls.pocm.contract.util.PocmUtil.toNuls;

/**
 * @author: PierreLuo
 * @date: 2019-06-25
 */
public class DepositOthersManager {
    /**
     * 在节点不能运行时，委托到其他节点，即抵押的金额不足20W之前，委托到其他节点
     */
    private Set<String> otherAgents;

    // 委托信息列表
    private LinkedList<ConsensusDepositInfo> depositList = new LinkedList<ConsensusDepositInfo>();
    // 委托其他节点的锁定金额
    private BigInteger depositLockedAmount = BigInteger.ZERO;

    public DepositOthersManager() {
        otherAgents = new HashSet<String>();
    }

    public int otherAgentsSize() {
        return otherAgents.size();
    }

    public BigInteger otherDepositLockedAmount() {
        return depositLockedAmount;
    }

    public void addOtherAgent(String agentHash) {
        require(otherAgents.add(agentHash), "重复的共识节点hash");
    }

    public BigInteger deposit(BigInteger availableAmount) {
        BigInteger actualDeposit = BigInteger.ZERO;
        String[] agentInfo;
        // 选择一个可委托金额足够的共识节点
        for(String agentHash : otherAgents) {
            agentInfo = (String[]) Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
            // 0-待共识 1-共识中
            String status = agentInfo[9];
            //emit(new ErrorEvent("status", status));
            if(!ACTIVE_AGENT.equals(status)) {
                continue;
            }
            // 合约节点已委托金额
            BigInteger totalDeposit = new BigInteger(agentInfo[4]);
            BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
            if(currentAvailable.compareTo(availableAmount) >= 0) {
                this.deposit(agentHash, availableAmount);
                actualDeposit = actualDeposit.add(availableAmount);
                break;
            } else {
                this.deposit(agentHash, currentAvailable);
                actualDeposit = actualDeposit.add(currentAvailable);
                availableAmount = availableAmount.subtract(currentAvailable);
            }
        }
        return actualDeposit;
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
                this.withdraw(info.getHash());
            }
            actualWithdraw = depositLockedAmount;
            depositLockedAmount = BigInteger.ZERO;
        } else {
            // 退出部分委托，以达到期望值
            for(ConsensusDepositInfo info : depositList) {
                this.withdraw(info.getHash());
                actualWithdraw = actualWithdraw.add(info.getDeposit());
                depositLockedAmount = depositLockedAmount.subtract(info.getDeposit());
                if(actualWithdraw.compareTo(expectWithdrawAmount) >= 0) {
                    break;
                }
            }
        }
        return actualWithdraw;
    }


    private String deposit(String agentHash, BigInteger depositNa) {
        String[] args = new String[]{agentHash, depositNa.toString()};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractDeposit", args);
        this.orderlyAdditionToDepositList(new ConsensusDepositInfo(txHash, depositNa));
        depositLockedAmount = depositLockedAmount.add(depositNa);
        return txHash;
    }

    private String withdraw(String joinAgentHash) {
        String[] args = new String[]{joinAgentHash};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractWithdraw", args);
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"otherAgents\":");
        sb.append('[');
        for(String hash : otherAgents) {
            sb.append('\"').append(hash).append('\"').append(',');
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
