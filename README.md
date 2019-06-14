# POCM智能合约介绍
POCM是一种Token产生和分发形式，让NULS持币者把NULS当成矿机，在产生这种新的Token时，通过抵押NULS去挖矿。也可以理解为锁定币天获得空投。
# 合约方法
## 1.部署智能合约
部署智能合约时，参数有：Token名称、Token符号、初始总量、精度、单价、回报周期、最低抵押NULS数额、最小锁定高度、是否开启共识功能、共识节点出块地址、回报减半周期、允许抵押的最多人数、
接收Token空投的地址列表、空投数量列表。

* 单价：每个奖励周期所有的NULS抵押数平分XX个token；
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
