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
import io.nuls.contract.sdk.Msg;
import io.nuls.pocm.contract.manager.deposit.DepositOthersManager;
import io.nuls.pocm.contract.model.ConsensusAwardInfo;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.util.PocmUtil.toNuls;

/**
 * @author: PierreLuo
 * @date: 2019-05-14
 */
public class ConsensusManager {
    // 2K
    public static final BigInteger MIN_JOIN_DEPOSIT = BigInteger.valueOf(200000000000L);
    // 50W
    public static final BigInteger MAX_TOTAL_DEPOSIT = BigInteger.valueOf(50000000000000L);

    public static final String ACTIVE_AGENT = "1";
    // 可用金额
    private BigInteger availableAmount = BigInteger.ZERO;
    // 共识奖励金额信息
    private ConsensusAwardInfo awardInfo;
    /**
     * 开启委托到其他节点的功能
     */
    private boolean enableDepositOthers = false;
    private DepositOthersManager depositOthersManager;

    public ConsensusManager() {
        awardInfo = new ConsensusAwardInfo(Msg.address().toString());
        enableDepositOthers();
    }

    /**
     * 共识奖励收益处理
     * 委托到其他节点，收益地址只有当前合约地址
     *
     * @param args 区块奖励地址明细 eg. [[address, amount]]
     */
    public void _payable(String[][] args) {
        String[] award = args[0];
        String address = award[0];
        String amount = award[1];
        awardInfo.add(new BigInteger(amount));
    }


    private void enableDepositOthers() {
        require(!enableDepositOthers, "重复操作，已开启此功能");
        enableDepositOthers = true;
        depositOthersManager = new DepositOthersManager();
    }

    public void addOtherAgent(String agentHash) {
        require(enableDepositOthers, "未开启此功能");
        depositOthersManager.addOtherAgent(agentHash);
    }

    public BigInteger otherDepositLockedAmount() {
        return depositOthersManager.otherDepositLockedAmount();
    }

    /**
     * 增加了押金后，押金数额达到条件后，则委托节点
     * 押金数额未达到条件，则累计总可用押金数额
     *
     * @param value          投资的押金
     */
    public void createOrDepositIfPermitted(BigInteger value) {
        availableAmount = availableAmount.add(value);
        if(depositOthersManager.otherAgentsSize() == 0) {
            // 没有其他节点的共识信息，跳过此流程
            //emit(new ErrorEvent("log", "in 209L"));
            return;
        }
        /**
         * 委托其他节点
         */
        BigInteger actualDeposit = depositOthersManager.deposit(availableAmount);
        availableAmount = availableAmount.subtract(actualDeposit);
    }

    /**
     * 当可用金额达到最小可委托金额时，合约拥有者可手动委托合约节点
     */
    public void depositManually() {
        BigInteger amount = availableAmount;
        require(amount.compareTo(MIN_JOIN_DEPOSIT) >= 0, "可用金额不足以委托节点");
        /**
         * 委托其他节点
         */
        BigInteger actualDeposit = depositOthersManager.deposit(availableAmount);
        availableAmount = availableAmount.subtract(actualDeposit);
    }

    /**
     * 如果合约余额不足，则退出其他节点的委托和合约节点的委托，直到余额足以退还押金
     *
     * @param value 需要退还的押金
     * @return true - 退出委托后余额足够, false - 退出委托，可用余额不足以退还押金
     */
    public boolean withdrawIfPermittedWrapper(BigInteger value) {
        if (availableAmount.compareTo(value) >= 0) {
            availableAmount = availableAmount.subtract(value);
            return true;
        }
        // 退出委托其他节点的金额
        BigInteger actualWithdraw = depositOthersManager.withdraw(value);
        availableAmount = availableAmount.add(actualWithdraw);
        if (availableAmount.compareTo(value) < 0) {
            return false;
        }
        availableAmount = availableAmount.subtract(value);
        /**
         * 若可用金额足够，则委托其他节点
         */
        if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            BigInteger actualDeposit = depositOthersManager.deposit(availableAmount);
            availableAmount = availableAmount.subtract(actualDeposit);
        }
        return true;
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
     * 获取可委托共识的空闲金额
     */
    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append(",\"availableAmount\":")
                .append('\"').append(toNuls(availableAmount).toPlainString()).append('\"');
        sb.append(",\"awardInfo\":")
                .append(awardInfo.toString());
        if(enableDepositOthers) {
            sb.append(",\"depositOthersManager\":")
                    .append(depositOthersManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

}
