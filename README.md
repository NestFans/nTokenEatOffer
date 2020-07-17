### 

[toc]

***

### nToken-ETH自动套利操作说明(以HT-ETH为例)

***

#### 启动配置

1. 准备好：钱包私钥、以太坊节点URL、etherscan-api-key、HT代币合约地址、获取价格API。
   * 钱包私钥：通过助记词生成，可通过nestDapp注册。
   * 以太坊节点URL：可通过https://infura.io 免费申请。
   * etherscan-api-key：可通过https://cn.etherscan.com免费申请。
   * HT-ETH火币价格API：  https://api.huobi.pro/market/history/trade?symbol=hteth&size=1
2. 在 application.properties 里面根据注释将数据填写在对应的地方。

#### 数据库连接

1. 使用mysql进行连接，resources/db/目录下有对应的sql。

#### 设置（GasPrice、GasLimit、偏离百分比）

1. GasPrice：
   * 套利：默认为2倍默认gasPrice，参数名：OFFER_GAS_PRICE_MULTIPLE。
   * 取回：默认为1.2倍默认gasPrice，参数名：TURNOUT_GAS_PRICE_MULTIPLE。
   * 取消报价交易：报价gasPrice的2倍。
2. GasLimit：
   * 所有gasLimit没有进行预估，均给予的默认值。
3. 偏离百分比：
   * 默认为超过2%波动即进行套利，百分比参数名：UP_PRICE_DEVIATION 和 DOWN_PRICE_DEVIATION。

#### 测试报价

```java
1. 将发起套利交易代码注释：
String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
2. 查看打印的log里面的报价ETH数量和报价USDT数量，核对数据。
```

#### 合约交互

[合约交互说明](./NestV3EatOffer/README.md)

