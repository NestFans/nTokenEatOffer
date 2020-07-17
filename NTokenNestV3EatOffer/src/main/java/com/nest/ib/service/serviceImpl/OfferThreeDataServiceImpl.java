package com.nest.ib.service.serviceImpl;

import com.nest.ib.contract.Nest3OfferMain;
import com.nest.ib.dao.mapper.OfferThreeDataMapper;
import com.nest.ib.model.OfferThreeData;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.OfferThreeDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple9;
import org.web3j.tx.Contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class OfferThreeDataServiceImpl implements OfferThreeDataService {
    private static final Logger LOG = LoggerFactory.getLogger(OfferThreeDataServiceImpl.class);

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式

    private static BigInteger BLOCK_NUMBER = new BigInteger("0");

    private static LinkedHashMap<BigInteger,Integer> linkedHashMap = new LinkedHashMap();

    private static final String ETH_ADDRESS_NAME = "0xethereum";

    private static final BigDecimal UNIT_ETH = new BigDecimal("1000000000000000000");

    @Value("${eth.node}")
    private String ETH_NODE;
    @Value("${private.key}")
    private String PRIVATE_KEY;
    @Value("${erc20.token.contract.address}")
    private String ERC20_TOKEN_CONTRACT_ADDRESS;
    @Value("${input.offer}")
    private String INPUT_OFFER;
    @Value("${input.send.eth.buy.erc20}")
    private String INPUT_SEND_ETH_BUY_USDT;
    @Value("${input.send.erc20.buy.eth}")
    private String INPUT_SEND_USDT_BUY_ETH;

    @Autowired
    private OfferThreeDataMapper offerThreeDataMapper;
    @Autowired
    private EatOfferAndTransactionService eatOfferAndTransactionService;

    @Override
    public void save() {
        String offerFactoryContractAddress = eatOfferAndTransactionService.getOfferFactoryContractAddress();
        if(offerFactoryContractAddress == null){
            return;
        }
        if(linkedHashMap.size() > 500){
            linkedHashMap.clear();
        }
        // 获取更新到的最新区块号
        Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        try {
            BigInteger number = web3j.ethBlockNumber().send().getBlockNumber();
            number = number.add(new BigInteger("2"));
            /**
            *   每次获取最近的10个块数据，防止跳块的情况（很短的时间内矿工打包多个块）
            */
            for (int i=10; i>=0; i--){
                BLOCK_NUMBER = number.subtract(new BigInteger(String.valueOf(i)));
                if(linkedHashMap.containsKey(BLOCK_NUMBER) && linkedHashMap.get(BLOCK_NUMBER) != 0)continue;
                EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BLOCK_NUMBER), true).sendAsync().get().getBlock();
                if(block == null){
                    if(!linkedHashMap.containsKey(BLOCK_NUMBER)){
                        linkedHashMap.put(BLOCK_NUMBER,0);
                    }
                    return;
                }
                BigInteger timestamp = block.getTimestamp();
                String time = DF.format(timestamp.multiply(new BigInteger("1000")));
                List<EthBlock.TransactionResult> transactions = block.getTransactions();
                linkedHashMap.put(BLOCK_NUMBER,transactions.size());
                for (EthBlock.TransactionResult tx : transactions) {
                    EthBlock.TransactionObject transaction = (EthBlock.TransactionObject) tx.get();
                    String input = transaction.getInput();
                    /**
                     *   找到指定的input并发送过去
                     */
                    if(input.length() < 10){
                        continue;
                    }
                    String substring = input.substring(0, 10);
                    /**
                    *   根据input确定是否是报价或者吃单的方法名。
                    */
                    if(substring.equalsIgnoreCase(INPUT_OFFER) || substring.equalsIgnoreCase(INPUT_SEND_ETH_BUY_USDT) || substring.equalsIgnoreCase(INPUT_SEND_USDT_BUY_ETH)) {
                        String transactionHash = transaction.getHash();
                        BigInteger blockNumber = transaction.getBlockNumber();
                        String fromAddress = transaction.getFrom();
                        if(offerThreeDataMapper.selectByTransactionHash(transactionHash) != null){
                            LOG.info("该hash已经存在：" + transactionHash);
                            return;
                        }
                        LOG.info("发现的报价hash: " + transactionHash);
                        /**
                         *   将所有报价的数据先存储起来，防止异常情况导致没有存储
                         */
                        try {
                            EthGetTransactionReceipt ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get();
                            TransactionReceipt result = ethGetTransactionReceipt.getResult();
                            List<Log> logs = result.getLogs();
                            if (logs.size() == 0) {
                                return;
                            }
                            // 遍历当前transactionHash下所有的日志记录
                            for (Log log : logs) {
                                String address = log.getAddress();
                                if(address.equalsIgnoreCase(offerFactoryContractAddress)){
                                    String data = log.getData();
                                    if(data.length() == 386){
                                        String contractAddress = "0x" + data.substring(26, 66);    // 合约地址
                                        String erc20TokenAddress = "0x" + data.substring(90, 130);   // 交易token地址
                                        String ethAmount = new BigInteger(data.substring(130,194), 16).toString(10);          // ETH数量
                                        String erc20Amount = new BigInteger(data.substring(194,258), 16).toString(10);        // ERC20数量
                                        String intervalBlock = new BigInteger(data.substring(258,322), 16).toString(10);      // 确认需要的区块数量
                                        String serviceCharge = new BigInteger(data.substring(322,386), 16).toString(10);       // 手续费
                                        /**
                                         *   查询可成交的数量
                                         */
                                        System.out.println("报价工厂合约地址：" + offerFactoryContractAddress);
                                        Nest3OfferMain nest3OfferMain = Nest3OfferMain.load(offerFactoryContractAddress,web3j,credentials, Contract.GAS_PRICE,Contract.GAS_LIMIT);
                                        // 将报价合约地址转化为在报价单数组中的索引
                                        BigInteger toIndex = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
                                        // 根据索引获取报价单信息
                                        String s = nest3OfferMain.getPrice(toIndex).sendAsync().get();
                                        LOG.info(s);
                                        String[] split = s.split(",");
                                        Tuple9<String, String, String, String, String, String, String, String, String> tuple9 = new Tuple9<>(
                                                "0x" + split[0],       // 报价单唯一标识key
                                                "0x" + split[1],       // 报价人地址
                                                "0x" + split[2],       // 报价 token 地址
                                                split[3],       // 报价单 ETH 数量（余额）
                                                split[4],       // 报价单 token 数量（余额）
                                                split[5],       // 可成交 ETH 数量
                                                split[6],       // 可成交 token 数量
                                                split[7],       // 报价所在区块
                                                split[8]       // 手续费
//                                                split[9].split("\\|")[0]        // 延时区块
                                        );
                                        BigDecimal erc20Decimal = eatOfferAndTransactionService.getErc20Decimal();
                                        String erc20Name = eatOfferAndTransactionService.getErc20Name();
                                        if(erc20Decimal == null || erc20Name == null){
                                            continue;
                                        }
                                        OfferThreeData offerThreeData = new OfferThreeData();
                                        offerThreeData.setContractAddress(contractAddress);
                                        offerThreeData.setLeftTokenName("ETH");
                                        offerThreeData.setLeftTokenAddress(ETH_ADDRESS_NAME);
                                        offerThreeData.setLeftTokenAmount(new BigDecimal(ethAmount));
                                        offerThreeData.setLeftTokenBalance(new BigDecimal(tuple9.getValue6()));
                                        offerThreeData.setRightTokenAddress(erc20TokenAddress);
                                        offerThreeData.setRightTokenName(erc20Name);
                                        offerThreeData.setRightTokenAmount(new BigDecimal(erc20Amount));
                                        offerThreeData.setRightTokenBalance(new BigDecimal(tuple9.getValue7()));
                                        offerThreeData.setPriceRatio(new BigDecimal(tuple9.getValue7()).divide(erc20Decimal).divide(new BigDecimal(tuple9.getValue6()).divide(UNIT_ETH),18,BigDecimal.ROUND_DOWN));
                                        offerThreeData.setTransactionHash(transactionHash);
                                        offerThreeData.setOriginatorWalletAddress(fromAddress);
                                        offerThreeData.setBlockNumber(blockNumber.intValue());
                                        offerThreeData.setCreateTime(time);
                                        offerThreeData.setVersion(0);
                                        offerThreeData.setIntervalBlock(Integer.valueOf(intervalBlock));
                                        offerThreeData.setServiceCharge(new BigDecimal(serviceCharge));
                                        LOG.info("保存报价：" + offerThreeData.toString());
                                        offerThreeDataMapper.insert(offerThreeData);
                                    }
                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("出现异常: " + e);
            return;
        }
    }

}
