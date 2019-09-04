package io.nuls.pocm.contract.model;

import java.math.BigInteger;

/**
 * @author: tangag
 * @date: 2019-09-04
 */
public class ConsensusAgentInfo {
    private String agentHash;
    private String creator;
    private BigInteger deposit;

    public  ConsensusAgentInfo(String agentHash , String creator, BigInteger deposit){
        this.agentHash = agentHash;
        this.creator = creator;
        this.deposit = deposit;
    }

    public String getAgentHash() {
        return agentHash;
    }

    public void setAgentHash(String agentHash) {
        this.agentHash = agentHash;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public BigInteger getDeposit() {
        return deposit;
    }

    public void setDeposit(BigInteger deposit) {
        this.deposit = deposit;
    }
}
