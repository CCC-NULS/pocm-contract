# POCM智能合约介绍
POCM是分发指定Token的方式，让NULS持币者通过抵押NULS，根据奖励规则获取指定的Token。
# 合约方法
## 1.部署智能合约
部署智能合约时，参数有：Token合约地址、初始单价、回报周期、最低抵押NULS数额、最小锁定高度、是否开启共识功能、共识节点出块地址、回报减半周期、允许抵押的最多人数。

* Token合约地址：抵押NULS获取的Token来自哪个智能合约；
* 初始单价：每个奖励周期所有的NULS抵押数平分XX个token；
* 回报周期：每过X块发放一次Token；
* 回报减半周期：非必填，每过X块奖励减半；
* 允许抵押的最多人数：非必填；
* 接收Token空投的地址列表、空投数量列表：非必填，如果不填写，则将Token的初始总量全部空投给发起者，若填写，则先给空投地址空投指定数量的Token，然后将剩下的余额空投给发起者。

## 2.depositForOwn
抵押NULS为自己获取Token回报

## 3.depositForOther
为其他账户抵押NULS，获取的Token回报直接转入该指定账户

## 4.quit
退出抵押挖矿：根据抵押编号逐笔退出，也可以一次全部退出，当抵押编号为0时退出全部抵押

## 5.receiveAwards
领取奖励,领取为自己抵押的Token回报

## 6.receiveAwardsForMiningAddress
由挖矿接收地址发起领取奖励;当为其他账户做抵押挖矿时，接收Token回报的账户可以调用此方法领取回报

## 7.getMingInfo
查找指定账户的挖矿信息

## 8.getDepositInfo
查找指定账户的抵押信息

## 9.getAirdropperInfo
获取空投信息

## 10.currentPrice
获取当前单价

---

# 合约共识机制说明

 - 手动创建合约节点
       - 开启共识功能后，项目发布者可以转入20000个NULS，手动创建节点（调用`createAgentByOwner`方法）
 - 自动创建合约节点
       - 等待投资者投资金额累积到20000后，自动创建节点
 - 自动委托合约节点
       - 节点创建后，当投资者抵押NULS累积到2000个NULS后，自动委托到本节点
       - 当投资者抵押NULS不足2000个，会触发退出委托（上一笔委托），累加NULS再自动委托
       - 当某一个投资者退出抵押，自动退出一笔或多笔委托，可用余额返还给用户后，可用余额超过2000个时，自动委托到本节点
 - _**委托到其他节点**_
       - 投资者抵押NULS数量累积不足22W，或者，委托到合约节点的NULS数量已经达到50W，可开启此功能委托到其他节点    
         - 调用`enableDepositOthers`方法开启此功能
         - 调用`addOtherAgent`方法添加其他共识节点的信息（交易hash）
 - 手动委托合约节点
       - 当抵押金额超过2000NULS没有委托到节点上，可调用`depositConsensusManuallyByOwner`方法，手动委托
          - 可以通过调用视图方法`freeAmountForConsensusDeposit`查询空闲的抵押金额

 - 领取共识奖励
       - 共识节点激活后，合约地址会受到共识奖励，可调用`transferConsensusRewardByOwner`方法，转移奖励到合约创建者地址
 - 自动退出委托，自动注销合约节点
       - 用户退出抵押后，合约可用余额不足以返还给用户，则会触发自动退出委托，直到余额足够，若退出所有委托的NULS仍然不足以返还给用户，则会触发自动注销节点，并按照POCM共识规则，锁定3天，再返还用户抵押的NULS（项目拥有者3天后调用`refundAllUnLockDepositByOwner`方法返还NULS给所有申请过退出的用户，或者用户自行调用`takeBackUnLockDeposit`方法赎回自己的押金），同时，POCM共识功能将锁定3.5天
 - 手动注销合约节点
       - 项目发布者手动注销节点, 可调用`stopAgentManuallyByOwner`方法，手动注销节点，按照POCM共识规则，创建共识的保证金锁定3天，POCM共识功能锁定3.5天
 - 项目发布者赎回共识创建的保证金
       - 手动创建的节点，注销节点后，锁定3天，项目发布者可调用`takeBackConsensusCreateAgentDepositByOwner`方法赎回共识保证金
 - 查询POCM合约共识整体信息
       - 调用视图方法`wholeConsensusInfoForTest`方法，可获得合约共识所有信息