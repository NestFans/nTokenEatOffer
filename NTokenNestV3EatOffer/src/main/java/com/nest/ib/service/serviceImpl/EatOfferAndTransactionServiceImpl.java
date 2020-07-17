package com.nest.ib.service.serviceImpl;

import com.nest.ib.contract.ERC20;
import com.nest.ib.contract.Nest3OfferMain;
import com.nest.ib.contract.NestOfferPriceContract;
import com.nest.ib.dao.mapper.OfferThreeDataMapper;
import com.nest.ib.model.OfferThreeData;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.OfferCoinPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ClassName:EatOfferAndTransactionServiceImpl
 * Description:
 */
@Service
public class EatOfferAndTransactionServiceImpl implements EatOfferAndTransactionService{
    private static final Logger LOG = LoggerFactory.getLogger(EatOfferAndTransactionServiceImpl.class);
    // 离正常价格向上偏移2%
    private static final BigDecimal UP_PRICE_DEVIATION = new BigDecimal("1.02");
    // 离正常价格向下偏移2%
    private static final BigDecimal DOWN_PRICE_DEVIATION = new BigDecimal("0.98");
    private static final BigDecimal UNIT_ETH = new BigDecimal("1000000000000000000");
    private static BigDecimal UNIT_ERC20 = null;
    private static String ERC20_NAME = null;
    private static String OFFER_PRICE_CONTRACT_ADDRESS = null;
    private static String OFFER_FACTORY_CONTRACT_ADDRESS = null;

    @Value("${eth.node}")
    private String ETH_NODE;
    @Value("${private.key}")
    private String PRIVATE_KEY;
    @Value("${erc20.token.contract.address}")
    private String ERC20_TOKEN_CONTRACT_ADDRESS;


    @Autowired
    private OfferCoinPriceService offerCoinPriceService;
    @Autowired
    private OfferThreeDataMapper offerThreeDataMapper;

    /**
     * 报价吃单(吃ETH或者ERC20)
     */
    @Override
    public void eatOffer() {
        if(OFFER_FACTORY_CONTRACT_ADDRESS == null || ERC20_NAME == null || OFFER_PRICE_CONTRACT_ADDRESS == null){
            return;
        }
        // 查看当前报价的单子,是否有价格不合理的
        Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        BigInteger nonce = null;
        try {
            nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
        } catch (IOException e) {
            return;
        }
        try {
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(new BigInteger("3"));
            BigInteger gasLimit = new BigInteger("2000000");
            /**
            *   找出近25个区块内的报价合约(不包括吃单)
            */
            List<OfferThreeData> offerThreeDatas = offerThreeDataMapper.selectOverBlockNumberAddIntervalBlock(blockNumber.intValue(),ERC20_NAME);
            if(offerThreeDatas.size() == 0){
                LOG.info("当前没有等待验证中的报价");
                return;
            }
            // 遍历这些合约,找出价格 超出指定偏移百分比的合约
            for (OfferThreeData offerThreeData : offerThreeDatas) {
                String leftTokenName = offerThreeData.getLeftTokenName();
                String rightTokenName = offerThreeData.getRightTokenName();
                BigDecimal priceRatio = offerThreeData.getPriceRatio();
                String contractAddress = offerThreeData.getContractAddress();

                if(leftTokenName.equalsIgnoreCase("ETH")){  // 左边必须是ETH
                    BigDecimal coinEth = offerCoinPriceService.getCoinPrice();
                    if(coinEth != null){
                        BigDecimal nowExchangePrice = new BigDecimal("1").divide(coinEth, 4, BigDecimal.ROUND_DOWN);
                        BigDecimal maxPriceDeviation = nowExchangePrice.multiply(UP_PRICE_DEVIATION);   // 最大偏移量
                        BigDecimal minPriceDeviation = nowExchangePrice.multiply(DOWN_PRICE_DEVIATION); // 最小偏移量
                        LOG.info("报价合约价格: " + priceRatio + "   允许的最大偏移价格: " + maxPriceDeviation + "  允许的最小偏移价格: " + minPriceDeviation);
                        if(priceRatio.compareTo(maxPriceDeviation) < 0 && priceRatio.compareTo(minPriceDeviation) > 0){
                            continue;
                        }
                        Nest3OfferMain nest3OfferMain = Nest3OfferMain.load(OFFER_FACTORY_CONTRACT_ADDRESS, web3j, credentials, gasPrice, gasLimit);
                        // 将报价合约地址转化为在报价单数组中的索引
                        BigInteger toIndex = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
                        // 根据索引获取报价单信息
                        String s = nest3OfferMain.getPrice(toIndex).sendAsync().get();
                        String[] split = s.split(",");
                        // 剩余可成交ETH数量
                        BigInteger value1 = new BigInteger(split[5]);
                        // 剩余可成交ERC20数量
                        BigInteger value2 = new BigInteger(split[6]);
                        LOG.info("套利 -> 剩余可以成交的ETH：" + value1 + "    剩余可以成交的ERC20：" + value2);
                        String erc20TokenAddress = "0x" + split[2];       // ERC20 TOKEN地址
                        if(!erc20TokenAddress.equalsIgnoreCase(ERC20_TOKEN_CONTRACT_ADDRESS)){
                            LOG.error("ERC20代币合约地址对不上，设置ERC20代币合约地址为：" + ERC20_TOKEN_CONTRACT_ADDRESS + "  想要吃单的报价ERC20合约地址为：" + erc20TokenAddress);
                            return;
                        }
                        // 吃单后的报价ETH数量
                        BigInteger offerEthAmount = value1.multiply(new BigInteger("2"));
                        // 吃单后的报价ERC20数量
                        BigInteger offerErc20Amount = new BigInteger(String.valueOf(
                                new BigDecimal(offerEthAmount).divide(UNIT_ETH,18,BigDecimal.ROUND_DOWN).multiply(nowExchangePrice).multiply(UNIT_ERC20).setScale(0,BigDecimal.ROUND_DOWN)
                        ));
                        if(value1.compareTo(new BigInteger("0"))==0 && value2.compareTo(new BigInteger("0"))==0){
                            LOG.info("该合约已经被吃完了： " + contractAddress);
                            continue;
                        }
                        NestOfferPriceContract nestOfferPriceContract = NestOfferPriceContract.load(OFFER_PRICE_CONTRACT_ADDRESS,web3j,credentials,gasPrice,gasLimit);
                        Tuple2<BigInteger, BigInteger> bigIntegerBigIntegerTuple2 = nestOfferPriceContract.checkPriceNow(ERC20_TOKEN_CONTRACT_ADDRESS).sendAsync().get();
                        // 价格合约里面最新的有效价格
                        BigDecimal otherMinerOfferPrice = new BigDecimal(bigIntegerBigIntegerTuple2.getValue2()).divide(UNIT_ERC20,18,BigDecimal.ROUND_DOWN)
                                .divide(new BigDecimal(bigIntegerBigIntegerTuple2.getValue1()).divide(UNIT_ETH,18,BigDecimal.ROUND_DOWN),18,BigDecimal.ROUND_DOWN);
                        // 如果   当前价格 > 合约及时价格*1.1  或者 当前价格 < 合约及时价格*0.9， 那么吃单后的报价*5
                        BigInteger M = new BigInteger("1");
                        if(nowExchangePrice.compareTo(otherMinerOfferPrice.multiply(new BigDecimal("1.1")))>0 || nowExchangePrice.compareTo(otherMinerOfferPrice.multiply(new BigDecimal("0.9")))<0){
                            if(priceRatio.compareTo(otherMinerOfferPrice.multiply(new BigDecimal("1.1"))) <= 0 &&  priceRatio.compareTo(otherMinerOfferPrice.multiply(new BigDecimal("0.9"))) >= 0){
                                M = new BigInteger("5");
                            }
                        }
                        /**
                        *   如果超过最大偏移量, 那么 吃掉ETH,获取ERC20
                        */
                        if(priceRatio.compareTo(maxPriceDeviation) >= 0){
                            String eatOfferTransactionHash = eatErc20(web3j, credentials, nonce, gasPrice, gasLimit,
                                    offerEthAmount,
                                    offerErc20Amount,
                                    contractAddress,
                                    value1,
                                    value2,
                                    erc20TokenAddress,
                                    M);
                            nonce = nonce.add(new BigInteger("1"));
                            LOG.info("eatErc吃单(打入ETH获取ERC20) Hash ：" + eatOfferTransactionHash);
                            Thread.sleep(1000*5);
                        }
                        /**
                        * 如果低于最小偏移量,那么 吃掉ERC20,获取ETH
                        */
                        if(priceRatio.compareTo(minPriceDeviation) <= 0){
                            String eatOfferTransactionHash = eatEth(web3j, credentials, nonce, gasPrice, gasLimit,
                                    offerEthAmount,
                                    offerErc20Amount,
                                    contractAddress,
                                    value1,
                                    value2,
                                    erc20TokenAddress,
                                    M);
                            nonce = nonce.add(new BigInteger("1"));
                            LOG.info("吃ERC20(打入ERC20获取ETH) Hash: " + eatOfferTransactionHash);
                            Thread.sleep(1000*5);
                        }
                    }
                }
            }
            Thread.sleep(1000*40);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("吃单出现异常");
            return;
        }
    }
    /**
     *  ETH吃单(打入ETH,获取ERC20)
     */
    @Override
    public String eatErc20(Web3j web3j,
                         Credentials credentials,
                         BigInteger nonce,
                         BigInteger gasPrice,
                         BigInteger gasLimit,
                         BigInteger ETH_AMOUNT,
                         BigInteger TOKEN_AMOUNT,
                         String CONTRACT_ADDRESS,
                         BigInteger TRAN_ETH_AMOUNT,
                         BigInteger TRAN_TOKEN_AMOUNT,
                         String TRAN_TOKEN_ADDRESS,
                           BigInteger  M){
        // 验证ETH和ERC20的余额是否足够
        BigInteger ethBalance;
        BigInteger ercBalance;
        try {
            // ETH余额
            ethBalance = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            // 需要计算减去矿工费后的余额
            ethBalance = ethBalance.subtract(new BigInteger("200000000000000000"));
            // ERC20余额
            ERC20 erc20 = ERC20.load(TRAN_TOKEN_ADDRESS,web3j,credentials,gasPrice,gasLimit);
            ercBalance = erc20.balanceOf(credentials.getAddress()).send();
            // 由于吃单优化后，打入ETH吃掉USDT变少
            ercBalance = ercBalance.add(TRAN_TOKEN_AMOUNT);
            /**
             *   查看钱包资产吃单是否足够，如果不够能吃多少吃多少
             */
            if(ethBalance.compareTo(ETH_AMOUNT.multiply(M).add(TRAN_ETH_AMOUNT)) <= 0 || ercBalance.compareTo(TOKEN_AMOUNT.multiply(M).subtract(TRAN_TOKEN_AMOUNT)) < 0){
                LOG.info("吃单时账户余额不够");
                // HT和USDT精度不一样，需要区分token并设置精度
                BigDecimal UNIT_TOKEN;
                if(TRAN_TOKEN_ADDRESS.equalsIgnoreCase(ERC20_TOKEN_CONTRACT_ADDRESS)){
                    UNIT_TOKEN = UNIT_ERC20;
                }else {
                    return null;
                }
                // 获取USDT/ETH价格
                BigDecimal price = new BigDecimal(TOKEN_AMOUNT).divide(UNIT_TOKEN,18,BigDecimal.ROUND_DOWN).divide(new BigDecimal(ETH_AMOUNT).divide(UNIT_ETH,0,BigDecimal.ROUND_DOWN), 18, BigDecimal.ROUND_DOWN);
                // 获取账户USDT换算成ETH的数量
                BigDecimal erc20ExchangeEth = new BigDecimal(ercBalance).divide(UNIT_TOKEN,18,BigDecimal.ROUND_DOWN).divide(price, 18, BigDecimal.ROUND_DOWN).multiply(UNIT_ETH).setScale(0,BigDecimal.ROUND_DOWN);

                // 如果钱包余额ERC20连最小10ETH吃单都不支持，不需要执行任何操作了
                if(erc20ExchangeEth.compareTo(new BigDecimal("20").multiply(UNIT_ETH).multiply(new BigDecimal(M))) < 0){
                    System.out.println("账户ERC20不够，需要补充资产才能吃单");
                    return null;
                }
                /**
                 * 设置吃ETH数量为X，那么完成吃单报价，需要ETH：3.001*X, 需要USDT: 价值 2*X 的ETH， 那么 ETH/USDT = 3.001/2
                 */
                BigInteger offerCopies;
                // 如果账户USDT换算成ETH后乘以3/2，比账户ETH余额多，那么吃单就按照账户ETH的余额数量来算，保持吃单的最大数量
                BigDecimal divide = erc20ExchangeEth.multiply(new BigDecimal("3.001")).divide(new BigDecimal("2"), 18, BigDecimal.ROUND_DOWN);
                if( divide.compareTo(new BigDecimal(ethBalance)) >= 0 ){
                    // 吃10ETH为一份，求出最多能吃多少份(ETH余额需要减去打包的矿工费)
                    offerCopies = ethBalance.divide(new BigInteger("30" + "010000000000000000").multiply(M));
                }else {
                    // 吃单不需要ERC20，报价需要双倍，ERC20余额转化为ETH数量，除以20即可得到最多能够报价多少份（10ETH为1份）
                    offerCopies = new BigInteger(String.valueOf(erc20ExchangeEth.divide(new BigDecimal("20000000000000000000").multiply(new BigDecimal(M)), 0, BigDecimal.ROUND_DOWN)));
                }
                // 如果最多能吃单份数为0，说明资产不够
                if(offerCopies.compareTo(new BigInteger("0")) <= 0){
                    System.out.println("账户ETH资产不够，需要补充资产");
                    return null;
                }else {
                    // 报价ETH数量
                    ETH_AMOUNT = offerCopies.multiply(new BigInteger("20")).multiply(new BigInteger(String.valueOf(UNIT_ETH)));
                    // 报价ERC20数量
                    TOKEN_AMOUNT = new BigInteger(String.valueOf(new BigDecimal(offerCopies).multiply(price).multiply(UNIT_TOKEN).multiply(new BigDecimal("20")).setScale(0,BigDecimal.ROUND_DOWN)));
                    // 报价合约产生的价格
                    BigDecimal offerPrice = new BigDecimal(TRAN_TOKEN_AMOUNT).divide(UNIT_TOKEN,18,BigDecimal.ROUND_DOWN).divide((new BigDecimal(TRAN_ETH_AMOUNT).divide(UNIT_ETH,18,BigDecimal.ROUND_DOWN)),18,BigDecimal.ROUND_DOWN);
                    // 吃掉ERC20数量
                    TRAN_TOKEN_AMOUNT = offerCopies.multiply(
                            new BigInteger(String.valueOf(offerPrice.multiply(new BigDecimal("10")).multiply(UNIT_TOKEN).setScale(0,BigDecimal.ROUND_DOWN)))
                    );
                    // 吃掉ETH数量
                    TRAN_ETH_AMOUNT = ETH_AMOUNT.divide(new BigInteger("2"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        // 合约优化了需要的资产
        ETH_AMOUNT = ETH_AMOUNT.multiply(M);
        TOKEN_AMOUNT = TOKEN_AMOUNT.multiply(M);
        // ETH数量: 报价ETH + 吃的ETH*0.001 + 打入的ETH
        BigDecimal multiply = new BigDecimal(TRAN_ETH_AMOUNT).multiply(new BigDecimal("1.001"));
        BigInteger PAYABLE_ETH = new BigInteger(String.valueOf(new BigDecimal(ETH_AMOUNT).add(multiply).setScale(0, BigDecimal.ROUND_DOWN)));
        if(ethBalance.compareTo(PAYABLE_ETH) <= 0 || ercBalance.compareTo(TOKEN_AMOUNT) < 0) {
            LOG.info("账户余额不够，10ETH也无法吃单");
            return null;
        }
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
        String transactionHash = null;
        try {
            transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
        } catch (Exception e) {
            LOG.info("ethTran吃单出现异常");
        }
        LOG.info("ethTran的Hash: " + transactionHash);
        return transactionHash;
    }
    /**
     *  ERC20吃单(打入ERC20,获取ETH)
     *
     */
    @Override
    public String eatEth(Web3j web3j,
                         Credentials credentials,
                         BigInteger nonce,
                         BigInteger gasPrice,
                         BigInteger gasLimit,
                         BigInteger ETH_AMOUNT,
                         BigInteger TOKEN_AMOUNT,
                         String CONTRACT_ADDRESS,
                         BigInteger TRAN_ETH_AMOUNT,
                         BigInteger TRAN_TOKEN_AMOUNT,
                         String TRAN_TOKEN_ADDRESS,
                         BigInteger M) {
        // 验证ETH和ERC20的余额是否足够
        BigInteger ethBalance;
        BigInteger ercBalance;
        try {
            // ETH余额
            ethBalance = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            // 需要计算除去矿工费的余额
            ethBalance = ethBalance.subtract(new BigInteger("200000000000000000"));
            // ERC20余额
            ERC20 erc20 = ERC20.load(TRAN_TOKEN_ADDRESS,web3j,credentials,gasPrice,gasLimit);
            ercBalance = erc20.balanceOf(credentials.getAddress()).send();
            if(ethBalance.compareTo(ETH_AMOUNT.multiply(M).subtract(TRAN_ETH_AMOUNT)) <= 0 || ercBalance.compareTo(TOKEN_AMOUNT.multiply(M).add(TRAN_TOKEN_AMOUNT)) < 0){
                LOG.info("吃单时账户余额不够全吃掉");
                // 设置token精度
                BigDecimal UNIT_TOKEN;
                if(TRAN_TOKEN_ADDRESS.equalsIgnoreCase(ERC20_TOKEN_CONTRACT_ADDRESS)){
                    UNIT_TOKEN = UNIT_ERC20;
                }else {
                    System.out.println("获取到的代币合约地址暂不支持：" + TRAN_TOKEN_ADDRESS);
                    return null;
                }
                /**
                 *   由于打入ERC20,获取ETH，涉及到两个价格：
                 *                      1. 要吃的报价合约ETH/USDT产生的价格
                 *                      2. 当前交易所ETH/USDT产生的价格
                 *   计算方式为：
                 *      1. 计算出吃掉最小单位需要的ETH数量m和ERC20数量n
                 *      2. 用账户余额的ETH和ERC20分别除以m和n，可以得到ETH和ERC20最多能支持吃下的份数
                 *      3. 选择份数最小的进行吃单报价
                 */
                //  吃掉10ETH，总共需要的ETH数量
                BigInteger eatOfferEthAmount =  new BigInteger("10010000000000000000");
                if(M.compareTo(new BigInteger("5")) == 0){
                    eatOfferEthAmount = new BigInteger("90010000000000000000");
                }
                //  钱包ETH余额最大支持份数(1份为吃掉10ETH)
                BigInteger ethCopies = ethBalance.divide(eatOfferEthAmount);
                //  吃掉10ETH，报价需要的ERC20数量
                BigInteger offerErc20Amount = TOKEN_AMOUNT.multiply(new BigInteger("20")).divide(ETH_AMOUNT.divide(new BigInteger(String.valueOf(UNIT_ETH))));
                //  吃掉10ETH，吃需要打入的ERC20数量
                BigDecimal divide = new BigDecimal(TRAN_TOKEN_AMOUNT).divide(UNIT_TOKEN, 18, BigDecimal.ROUND_DOWN).divide(new BigDecimal(TRAN_ETH_AMOUNT).divide(UNIT_ETH, 0, BigDecimal.ROUND_DOWN), 18, BigDecimal.ROUND_DOWN);
                BigInteger eatErc20Amount = new BigInteger(String.valueOf(divide.multiply(UNIT_TOKEN).multiply(new BigDecimal("10")).setScale(0,BigDecimal.ROUND_DOWN)));
                //  吃掉10ETH,总共需要的ERC20数量
                BigInteger eatOfferErc20Amount = offerErc20Amount.multiply(M).add(eatErc20Amount);
                //  钱包ERC20余额最大能够吃的份数
                BigInteger erc20Copies = ercBalance.divide(eatOfferErc20Amount);
                if( ethCopies.compareTo(new BigInteger("0"))==0){
                    LOG.info("钱包ETH不够，请增加资产");
                    return null;
                }
                if( erc20Copies.compareTo(new BigInteger("0"))==0 ){
                    if(UNIT_TOKEN.compareTo(UNIT_ERC20) == 0){
                        LOG.info("钱包USDT不够，请增加资产");
                    }
                    return null;
                }
                // 吃掉10ETH为一份，总共需要吃掉的份数
                BigInteger copies;
                // 对比ETH和ERC20支持份数，取最小支持份数
                if(ethCopies.compareTo(erc20Copies) < 0){
                    copies = ethCopies;
                }else {
                    copies = erc20Copies;
                }
                // 报价ETH数量
                ETH_AMOUNT = copies.multiply(new BigInteger("20000000000000000000"));
                // 报价ERC20数量
                TOKEN_AMOUNT = copies.multiply(offerErc20Amount);
                // 吃掉ETH数量
                TRAN_ETH_AMOUNT = copies.multiply(new BigInteger("10000000000000000000"));
                // 吃掉ERC20数量
                TRAN_TOKEN_AMOUNT = copies.multiply(eatErc20Amount);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        ETH_AMOUNT = ETH_AMOUNT.multiply(M);
        TOKEN_AMOUNT = TOKEN_AMOUNT.multiply(M);
        // ETH数量: 报价ETH + 吃的ETH*0.002
        BigDecimal multiply = new BigDecimal(TRAN_ETH_AMOUNT).multiply(new BigDecimal("0.001"));
        BigInteger PAYABLE_ETH = new BigInteger(String.valueOf(new BigDecimal(ETH_AMOUNT).add(multiply).setScale(0, BigDecimal.ROUND_DOWN)));
        PAYABLE_ETH = PAYABLE_ETH.subtract(TRAN_ETH_AMOUNT);
        if(ethBalance.compareTo(PAYABLE_ETH) <= 0 || ercBalance.compareTo(TOKEN_AMOUNT.add(TRAN_TOKEN_AMOUNT)) < 0) {
            LOG.info("吃单账户余额不够，10 ETH最小金额也无法吃掉");
            return null;
        }
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
        String transactionHash = null;
        try {
            transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
        } catch (Exception e) {
            LOG.info("ercTran吃单出现异常");
            return null;
        }
        LOG.info("ercTran的Hash: " + transactionHash);
        return transactionHash;
    }
    /**
     *  取回报价合约资产(发布报价过了25个区块)
     *
     */
    @Override
    public void retrieveAssets(){
        if(OFFER_FACTORY_CONTRACT_ADDRESS == null){
            return;
        }
        try {
            Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            gasPrice = gasPrice.multiply(new BigInteger("2"));
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            Credentials credentials = Credentials.create(PRIVATE_KEY);
            List<OfferThreeData> offerThreeDatas = offerThreeDataMapper.selectBelowBlockNumberAddIntervalBlockByWalletAddress(blockNumber.intValue(),credentials.getAddress(),ERC20_NAME);
            if(offerThreeDatas.size() == 0){
                LOG.info("没有需要取回的合约");
                return;
            }
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
            for(int i=0; i<offerThreeDatas.size(); i++){
                OfferThreeData offerThreeData = offerThreeDatas.get(i);
                LOG.info(offerThreeData.toString());
                if(!offerThreeData.getOriginatorWalletAddress().equalsIgnoreCase(credentials.getAddress())){
                    continue;
                }
                String contractAddress = offerThreeData.getContractAddress();
                LOG.info(OFFER_FACTORY_CONTRACT_ADDRESS);
                // 检查是否领取过
                Nest3OfferMain nest3OfferMain = Nest3OfferMain.load(OFFER_FACTORY_CONTRACT_ADDRESS,web3j,credentials, Contract.GAS_PRICE,Contract.GAS_LIMIT);
                // 将报价合约地址转化为在报价单数组中的索引
                BigInteger toIndex = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
                // 根据索引获取报价单信息
                String s = nest3OfferMain.getPrice(toIndex).sendAsync().get();
                String[] split = s.split(",");
                // 剩余ETH数量
                BigInteger value1 = new BigInteger(split[3]);
                // 剩余ERC20数量
                BigInteger value2 = new BigInteger(split[4]);
                LOG.info("取回 --> 剩余的ETH：" + value1 + "    剩余的" + ERC20_NAME + ":" + value2);
                // 如果2边都为0，说明取回了
                if(value1.compareTo(new BigInteger("0")) == 0 && value2.compareTo(new BigInteger("0")) == 0){
                    continue;
                }
                BigInteger gasLimit = new BigInteger("500000");
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
                LOG.info("取回hash： " + transactionHash);
                nonce = nonce.add(new BigInteger("1"));
            }
        }catch (Exception e){
            e.printStackTrace();
            LOG.info("取回出现异常");
            return;
        }
    }

    @Override
    public void updateOfferFactoryContractAddress(String offerFactoryContractAddress) {
        OFFER_FACTORY_CONTRACT_ADDRESS = offerFactoryContractAddress;
        LOG.info("报价合约地址更新：" + OFFER_FACTORY_CONTRACT_ADDRESS);
    }

    @Override
    public String getOfferFactoryContractAddress() {
        return OFFER_FACTORY_CONTRACT_ADDRESS;
    }

    @Override
    public void updateOfferPriceContractAddress(String offerPriceContractAddress) {
        OFFER_PRICE_CONTRACT_ADDRESS = offerPriceContractAddress;
        LOG.info("价格合约地址更新：" + OFFER_PRICE_CONTRACT_ADDRESS);
    }

    @Override
    public String getOfferPriceContractAddress() {
        return OFFER_PRICE_CONTRACT_ADDRESS;
    }
    /**
     *   更新ERC20代币的精度
     */
    @Override
    public void updateErc20Decimal(BigInteger decimal){
        if(decimal != null){
            UNIT_ERC20 = new BigDecimal(Math.pow(10,decimal.intValue())).setScale(0,BigDecimal.ROUND_DOWN);
        }
    }
    @Override
    public BigDecimal getErc20Decimal(){
        return UNIT_ERC20;
    }
    /**
    *   更新ERC20名字
    */
    @Override
    public void updateErc20Name(String erc20Name){
        if(erc20Name != null){
            ERC20_NAME = erc20Name;
        }
    }
    @Override
    public String getErc20Name(){
        return ERC20_NAME;
    }


}
