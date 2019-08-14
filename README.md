# POCM智能合约介绍
POCM是分发指定Token的方式，让NULS持币者通过抵押NULS，根据奖励规则获取指定的Token。
# 合约方法
## 1.部署智能合约
部署智能合约时，参数有：Token合约地址、初始单价、回报周期、最低抵押NULS数额、最小锁定高度、是否开启共识功能、回报减半周期、允许抵押的最多人数。

* Token合约地址：抵押NULS获取的Token来自哪个智能合约；
* 初始单价：每个周期奖励可以奖励的Token数量X，分配方式是每个奖励周期所有参与的NULS抵押数平分这X个Token；
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

 - 自动委托节点
       - 当投资者抵押NULS累积到2000个NULS后，自动委托到登记的已激活的共识节点
       - 当投资者抵押NULS不足2000个，会触发退出委托（上一笔委托），累加NULS再自动委托
       - 当某一个投资者退出抵押，自动退出一笔或多笔委托，可用余额返还给用户后，可用余额超过2000个时，自动委托到登记的已激活的共识节点
 - _**委托到其他节点**_
       - 调用`addOtherAgent`方法添加其他共识节点的信息（交易hash）
 - 手动委托节点
       - 当可用的抵押金额超过2000NULS没有委托到节点上，可调用`depositConsensusManuallyByOwner`方法，手动委托
          - 可以通过调用视图方法`freeAmountForConsensusDeposit`查询空闲的可用的抵押金额

 - 领取共识奖励
       - 共识委托到登记节点后，合约地址会收到共识奖励，可调用`transferConsensusRewardByOwner`方法，转移奖励到合约拥有者地址
 - 自动退出委托
       - 用户退出抵押后，合约可用余额不足以返还给用户，则会触发自动退出委托，直到余额足以退还抵押金额
 - 查询POCM合约共识整体信息
       - 调用视图方法`wholeConsensusInfoFor`方法，可获得合约共识所有信息