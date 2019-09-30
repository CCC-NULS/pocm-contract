package io.nuls.pocm.contract.model;

/**
 * 共识节点创建者参与抵押的信息
 */
public class ConsensusAgentDepositInfo {

    private String agentHash;

    //抵押编号
    private long depositNumber;

    //抵押者地址
    private String  depositorAddress;

    public  ConsensusAgentDepositInfo(String agentHash , String depositorAddress, long depositNumber){
        this.agentHash = agentHash;
        this.depositorAddress = depositorAddress;
        this.depositNumber = depositNumber;
    }

    public String getAgentHash() {
        return agentHash;
    }

    public void setAgentHash(String agentHash) {
        this.agentHash = agentHash;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }
}
