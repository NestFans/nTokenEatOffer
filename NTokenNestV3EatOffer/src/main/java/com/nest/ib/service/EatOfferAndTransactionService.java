package com.nest.ib.service;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

/**
 * ClassName:EatOfferAndTransactionService
 * Description:
 */
public interface EatOfferAndTransactionService {
    // 吃 ERC20 (打入ERC20获得ETH) (ETH数量: 报价ETH + 吃的ETH*0.002)
    String eatErc20(Web3j web3j, Credentials credentials, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, BigInteger ETH_AMOUNT, BigInteger TOKEN_AMOUNT, String CONTRACT_ADDRESS, BigInteger TRAN_ETH_AMOUNT, BigInteger TRAN_TOKEN_AMOUNT, String TRAN_TOKEN_ADDRESS,BigInteger M) throws ExecutionException, InterruptedException;
    // 吃 ETH (打入ETH获得ERC20) (ETH数量: 报价ETH + 打入ETH*0.002 + 打入的ETH)
    String eatEth(Web3j web3j, Credentials credentials, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, BigInteger ETH_AMOUNT, BigInteger TOKEN_AMOUNT, String CONTRACT_ADDRESS, BigInteger TRAN_ETH_AMOUNT, BigInteger TRAN_TOKEN_AMOUNT, String TRAN_TOKEN_ADDRESS,BigInteger M) throws ExecutionException, InterruptedException;
    // 吃报价合约
    void eatOffer();
    // 取回
    void retrieveAssets() throws Exception;

    void updateOfferFactoryContractAddress(String offerFactoryContractAddress);

    String getOfferFactoryContractAddress();

    void updateOfferPriceContractAddress(String offerPriceContractAddress);

    String getOfferPriceContractAddress();

    void updateErc20Decimal(BigInteger decimal);

    BigDecimal getErc20Decimal();

    void updateErc20Name(String name);

    String getErc20Name();
}
