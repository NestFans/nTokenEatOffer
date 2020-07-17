[toc]

***

### NEST 3.0 nToken-ETH自动套利(以HT为例)

***

#### 1. 创建私钥，节点，安装mysql

>由于调用合约获取相关数据以及发送交易，需要跟链交互，需要准备一个以太坊节点URL和私钥，节点可以通过https://infura.io/ 注册后免费申请。
>
>由于etherscan偶尔会出现数据延迟，故连接节点遍历所有的区块交易，将报价交易存储到Mysql。检测数据库中所有离当前25区块内的报价，达到了指定了价格偏差，即可进行套利。

```java
// 以太坊节点 
String ETH_NODE = "";
// 私钥
String USER_PRIVATE_KEY = "";
Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
Credentials credentials = Credentials.create(USER_PRIVATE_KEY);
```

#### 2. 获取Nest Protocol相关合约地址

>Nest  Protocol中映射合约的作用：管理其它所有合约地址。
>
>报价涉及到的合约为：HT代币合约，映射合约，报价合约，矿池合约，价格合约。

```java
// 映射合约地址
String mappingContractAddress = "";
// 创建映射合约对象
MappingContract mappingContract = MappingContract.load(mappingContractAddress,web3j,credentials,GAS_PRICE,GAS_LIMIT);
// 报价合约地址
offerFactoryContractAddress = mappingContract.checkAddress("nest.nToken.offerMain").sendAsync().get();
// 价格合约地址
offerPriceContractAddress = mappingContract.checkAddress("nest.v3.offerPrice").sendAsync().get();
ERC20 erc20 = ERC20.load(ERC20_TOKEN_ADDRESS, web3j, credentials, Contract.GAS_PRICE, Contract.GAS_LIMIT);
// ERC20代币精度
BigInteger erc20Decimal = erc20.decimals().sendAsync().get();
// ERC20代币名称
String erc20Name = erc20.name().sendAsync().get();
```

#### 3. 授权报价合约

>套利需要将HT转入到报价合约，转HT操作是由报价合约调用HT代币合约来执行，故需要对报价合约进行HT授权。

```java
// 报价合约地址
String offerContractAddress = "";
// 设置gasPrice为默认的2倍，可自行调整
BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(new BigInteger("2"));
// 创建HT代币合约对象
ERC20 erc20 = ERC20.load(USDT_TOKEN_ADDRESS, web3j, credentials, gasPrice, new BigInteger("200000"));
// 获取对报价合约的HT授权金额
BigInteger approveValue = erc20.allowance(credentials.getAddress(), offerContractAddress).sendAsync().get();
// 采用一次性授权方式
if(approveValue.compareTo(new BigInteger("999999999999999999999999999999999999")) < 0){
      String transactionHash = erc20.approve(offerContractAddress, new BigInteger("99999999999999999999999999999999999999999999999999999999")).sendAsync().get().getTransactionHash();
      System.out.println("一次性授权hash：" + transactionHash);
}
```

#### 4. 建立数据库，存储HT-ETH报价交易数据

>不断轮询最新生成的区块，将报价交易数据存储（包括套利后的报价）。

1. 轮询最新区块，遍历区块里面所有交易。

2. 判断每条交易携带的input前10位字符串是否跟报价合约方法名一样。

3. 如果input前10位符合，通过该交易hash查询log包含的address，如果该address为报价合约地址，那么即可认为该交易为报价交易。
4. 通过log包含的data字段，进行拆分，获取用户报价标号，报价区块高度，报价ETH,HT数量等数据，存储到数据库。

```java
// 报价方法名标识
String INPUT_OFFER = "0xf6a4932f";
// 发送ETH获取HT套利方法名标识
String INPUT_SEND_ETH_BUY_ERC20 = "0x91f5f9f3";
// 发送USDT获取ETH套利方法名标识
String INPUT_SEND_ERC20_BUY_ETH = "0xb3e2767c";
// 报价合约地址
String offerContractAddress = "";
// 最新区块高度
BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
// 获取区块信息
EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), true).sendAsync().get().getBlock();
// 获取区块包含的所有交易
List<EthBlock.TransactionResult> transactions = block.getTransactions();
for (EthBlock.TransactionResult tx : transactions) {
	EthBlock.TransactionObject transaction = (EthBlock.TransactionObject) tx.get();
    String input = transaction.getInput();
    String substring = input.substring(0, 10);
    if(substring.equalsIgnoreCase(INPUT_OFFER) || substring.equalsIgnoreCase(INPUT_SEND_ETH_BUY_ERC20) || substring.equalsIgnoreCase(INPUT_SEND_ERC20_BUY_ETH)) {
 		// 交易hash
        String transactionHash = transaction.getHash();
        // 交易区块高度
        BigInteger blockNumber = transaction.getBlockNumber();
        // 交易logs
        List<Log> logs = web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get().getResult().getLogs();
        for (Log log : logs) {
         	// 如果该log包含地址为报价合约，可认为该交易为报价 
            if(log.getAddress().equalsIgnoreCase(offerContractAddress)){
            	String data = log.getData();
                 // 在报价单的索引
                String contractAddress = "0x" + data.substring(26, 66);   
                // 报价ERC20 token地址
                String erc20TokenAddress = "0x" + data.substring(90, 130);  
                // 报价ETH数量
                String ethAmount = new BigInteger(data.substring(130,194), 16).toString(10);
                // 报价HT数量
                String erc20Amount = new BigInteger(data.substring(194,258), 16).toString(10);
                // 确认需要的区块数量（初始为25）
                String intervalBlock = new BigInteger(data.substring(258,322), 16).toString(10);  
                // 报价支付的手续费
                String serviceCharge = new BigInteger(data.substring(322,386), 16).toString(10);      
             	
            }
        }
    }
}
```

#### 5. 获取交易所当前HT-ETH价格

>通过交易频率很高的交易所API获取HT-ETH价格。
>
>部分交易所API需要海外节点才能访问，以下采用的是火币交易所API：

```java
// 火币HT-ETH的API
String htEthPriceUrl = "https://api.huobi.pro/market/history/trade?symbol=hteth&size=1";
// 访问火币API获取的数据
String s;
try {
	s = HttpClientUtil.sendHttpGet(htEthPriceUrl);
}catch (Exception e){
	return null;
}
if(s == null){
	return null;
}
// 将获取的字符串转换为json
JSONObject jsonObject = JSONObject.parseObject(s);
// 筛选出ETH/USDT价格
BigDecimal price = JSONObject.parseObject(
	String.valueOf(
		JSONObject.parseObject(
			String.valueOf(
				jsonObject.getJSONArray("data").get(0)
			)
		).getJSONArray("data").get(0)
	)
).getBigDecimal("price");
```

#### 6. 获取待验证的报价

>由于之前数据库存储了所有报价数据，那么只需要获取当前区块高度N，将所有区块高度大于(N-25)的报价单数据查询出即可。

#### 7. 发起套利交易

>待验证报价价格：待验证报价ETH数量和HT数量的比例。
>
>将当前交易所的价格和待验证报价单的价格进行对比，如果偏离满足期望，即可进行套利。
>
>注意：如果当前交易所获取的价格和价格合约最新生效价格对比，偏离超过了10%，那么套利同时发起的报价双向资产(ETH和HT)必须是10倍。

1. 查看报价合约是否存在套利机会,如存在发起套利交易时，必须报价套利ETH数量的双倍。

```java
// 报价合约地址
String offerContractAddress = "";
// 待验证报价交易标识
String contractAddress = "";
// 离正常价格向上偏移2%
BigDecimal UP_PRICE_DEVIATION = new BigDecimal("1.02"); 
// 离正常价格向下偏移2%
BigDecimal DOWN_PRICE_DEVIATION = new BigDecimal("0.98");
// 交易所价格
BigDecimal exchangePrice = "";
// 待验证报价价格
BigDecimal needDemonstrationOfferPrice = "";
// 向上最大允许偏移量
BigDecimal maxPriceDeviation = exchangePrice.multiply(UP_PRICE_DEVIATION);
// 向下最大允许偏移量
BigDecimal minPriceDeviation = exchangePrice.multiply(DOWN_PRICE_DEVIATION);
// 如果价格达到指定期望
if(needDemonstrationOfferPrice.compareTo(maxPriceDeviation) > 0 || needDemonstrationOfferPrice.compareTo(minPriceDeviation) < 0){
    Nest3OfferMain nest3OfferMain = Nest3OfferMain.load(offerContractAddress, web3j, credentials, Contract.GAS_PRICE, Contract.GAS_LIMIT);
	// 将报价合约地址转化为在报价单数组中的索引
	BigInteger toIndex = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
	// 根据索引获取报价单信息,并进行分割
	String[] split = nest3OfferMain.getPrice(toIndex).sendAsync().get().split(",");
	// 剩余可套利ETH数量
	BigInteger value1 = new BigInteger(split[5]);
	// 剩余可套利ERC20数量
	BigInteger value2 = new BigInteger(split[6]);
    // ERC20 TOKEN地址，此处可以验证是否为HT代币合约地址，确保数据正确性
	String erc20TokenAddress = "0x" + split[2];  
    // 如果剩余可套利数量都为0，说明该报价单已无套利机会
	if(value1.compareTo(new BigInteger("0"))==0 && value2.compareTo(new BigInteger("0"))==0){
		LOG.info("该合约已经被全部套利： " + contractAddress);
		return;
	}
	// 套利后的报价ETH数量(ETH必须是套利数量的2倍)
	BigInteger offerEthAmount = value1.multiply(new BigInteger("2"));
    // 套利后的报价HT数量(通过当前交易所价格，将套利后的报价ETH数量转换为HT)
    BigInteger offerUsdtAmount = getOfferUsdtAmount(offerEthAmount,exchangePrice);
}
```

2. 获取价格合约最新已完成验证的价格。

```java
// ETH精度
BigDecimal UNIT_ETH = new BigDecimal("1000000000000000000");
// HT精度
BigDecimal UNIT_HT = new BigDecimal("1000000000000000000");
// 价格合约地址
String OFFER_PRICE_CONTRACT_ADDRESS = "";
NestOfferPriceContract nestOfferPriceContract = NestOfferPriceContract.load(OFFER_PRICE_CONTRACT_ADDRESS,web3j,credentials,gasPrice,gasLimit);
Tuple2<BigInteger, BigInteger> bigIntegerBigIntegerTuple2 = nestOfferPriceContract.checkPriceNow(ERC20_TOKEN_CONTRACT_ADDRESS).sendAsync().get();
// 最新已完成验证的报价HT数量
BigInteger htAmount = bigIntegerBigIntegerTuple2.getValue2()
// 最新已完成验证的报价ETH数量
BigInteger ethAmount = bigIntegerBigIntegerTuple2.getValue1();
// 生效价格
BigDecimal authenticatedPrice = new BigDecimal(htAmount).divide(UNIT_HT,18,BigDecimal.ROUND_DOWN)
                                .divide(new BigDecimal(ethAmount).divide(UNIT_ETH,18,BigDecimal.ROUND_DOWN),18,BigDecimal.ROUND_DOWN);
```

3. 将自己套利后的报价价格和最新已完成验证的价格对比。如果向上偏离达到期望，那么进行套利A(打入ETH获取ERC20)；如果向下偏离达到期望，那么进行套利B(打入ERC20获取ETH)。

   * 打入ETH获取ERC20

   ```java
   // 报价合约地址
   String offerContractAddress = "";
   // 套利后的报价ETH数量
   BigInteger ETH_AMOUNT = "";
   // 套利后的报价HT数量
   BigInteger TOKEN_AMOUNT = "";
   // 被套利的报价单标识
   String CONTRACT_ADDRESS = "";
   // 被套利的ETH数量
   BigInteger TRAN_ETH_AMOUNT = "";
   // 被套利的HT数量
   BigInteger TRAN_TOKEN_AMOUNT = "";
   // 套利的HT代币合约地址
   String TRAN_TOKEN_ADDRESS = "";
   // 套利时往合约里面转入的ETH数量
   BigInteger PAYABLE_ETH = "";
   Function function = new Function(
   	"sendEthBuyErc",
   	Arrays.<Type>asList(
   		new Uint256(ETH_AMOUNT),
   		new Uint256(TOKEN_AMOUNT),
   		new Address(CONTRACT_ADDRESS),
   		new Uint256(TRAN_ETH_AMOUNT),
   		new Uint256(TRAN_TOKEN_AMOUNT),
   		new Address(TRAN_TOKEN_ADDRESS)
   	),
   	Collections.<TypeReference<?>>emptyList());
   String encode = FunctionEncoder.encode(function);
   RawTransaction rawTransaction = RawTransaction.createTransaction(
   	nonce,
   	gasPrice,
   	gasLimit,
   	OFFER_FACTORY_CONTRACT_ADDRESS,
   	PAYABLE_ETH,
   	encode);
   byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction,credentials);
   String hexValue = Numeric.toHexString(signedMessage);
   String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
   ```

   * 打入ERC20获取ETH

   ```java
   // 报价合约地址
   String offerContractAddress = "";
   // 套利后的报价ETH数量
   BigInteger ETH_AMOUNT = "";
   // 套利后的报价HT数量
   BigInteger TOKEN_AMOUNT = "";
   // 被套利的报价单标识
   String CONTRACT_ADDRESS = "";
   // 被套利的ETH数量
   BigInteger TRAN_ETH_AMOUNT = "";
   // 被套利的HT数量
   BigInteger TRAN_TOKEN_AMOUNT = "";
   // 套利的HT代币合约地址
   String TRAN_TOKEN_ADDRESS = "";
   // 套利时往合约里面转入的ETH数量
   BigInteger PAYABLE_ETH = "";
   Function function = new Function(
   	"sendErcBuyEth",
   	Arrays.<Type>asList(
   		new Uint256(ETH_AMOUNT),
   		new Uint256(TOKEN_AMOUNT),
   		new Address(CONTRACT_ADDRESS),
   		new Uint256(TRAN_ETH_AMOUNT),
   		new Uint256(TRAN_TOKEN_AMOUNT),
   		new Address(TRAN_TOKEN_ADDRESS)
   	),
   	Collections.<TypeReference<?>>emptyList());
   String encode = FunctionEncoder.encode(function);
   RawTransaction rawTransaction = RawTransaction.createTransaction(
   	nonce,
   	gasPrice,
   	gasLimit,
   	OFFER_FACTORY_CONTRACT_ADDRESS,
   	PAYABLE_ETH,
   	encode);
   byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction,credentials);
   String hexValue = Numeric.toHexString(signedMessage);
   String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
   ```

####  8. 取回资产

>查询还未被取回的ETH和HT是否都为0，如果还有剩余，并且该报价已经度过25个区块，那么发起取回交易。

```java
// 报价合约地址
String OFFER_FACTORY_CONTRACT_ADDRESS = "";
// 报价单标识
String contractAddress = "";
Nest3OfferMain nest3OfferMain = Nest3OfferMain.load(OFFER_FACTORY_CONTRACT_ADDRESS,web3j,credentials, Contract.GAS_PRICE,Contract.GAS_LIMIT);
// 将报价合约地址转化为在报价单数组中的索引
BigInteger toIndex = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
// 根据索引获取报价单信息,并进行分割
String[] split = nest3OfferMain.getPrice(toIndex).sendAsync().get().split(",")
// 剩余ETH数量
BigInteger value1 = new BigInteger(split[3]);
// 剩余ERC20数量
BigInteger value2 = new BigInteger(split[4]);
// 如果两边都为0，说明取回了
if(value1.compareTo(new BigInteger("0"))>0 || value2.compareTo(new BigInteger("0"))>0){
	// 发起取回交易
	Function function = new Function(
		"turnOut",
		Arrays.<Type>asList(new Address(contractAddress)),
		Collections.<TypeReference<?>>emptyList());
	String encode = FunctionEncoder.encode(function);
	RawTransaction rawTransaction = RawTransaction.createTransaction(
		nonce,
		gasPrice,
		gasLimit,
		OFFER_FACTORY_CONTRACT_ADDRESS,
		encode);
	byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction,credentials);
	String hexValue = Numeric.toHexString(signedMessage);
	String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
}
```

