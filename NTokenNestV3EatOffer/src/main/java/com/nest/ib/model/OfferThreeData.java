package com.nest.ib.model;


import java.io.Serializable;
import java.math.BigDecimal;

public class OfferThreeData implements Serializable {
    private Integer id;

    private String contractAddress;

    private String leftTokenName;

    private String leftTokenAddress;

    private BigDecimal leftTokenAmount;

    private String rightTokenName;

    private String rightTokenAddress;

    private BigDecimal rightTokenAmount;

    private BigDecimal priceRatio;

    private String transactionHash;

    private String originatorWalletAddress;

    private Integer blockNumber;

    private String createTime;

    private BigDecimal leftTokenBalance;

    private BigDecimal rightTokenBalance;

    private Integer version;

    private Integer isEatOffer;

    private Integer eatOfferId;

    private Integer intervalBlock;

    private BigDecimal serviceCharge;

    private static final long serialVersionUID = 1L;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress == null ? null : contractAddress.trim();
    }

    public String getLeftTokenName() {
        return leftTokenName;
    }

    public void setLeftTokenName(String leftTokenName) {
        this.leftTokenName = leftTokenName == null ? null : leftTokenName.trim();
    }

    public String getLeftTokenAddress() {
        return leftTokenAddress;
    }

    public void setLeftTokenAddress(String leftTokenAddress) {
        this.leftTokenAddress = leftTokenAddress == null ? null : leftTokenAddress.trim();
    }

    public BigDecimal getLeftTokenAmount() {
        return leftTokenAmount;
    }

    public void setLeftTokenAmount(BigDecimal leftTokenAmount) {
        this.leftTokenAmount = leftTokenAmount;
    }

    public String getRightTokenName() {
        return rightTokenName;
    }

    public void setRightTokenName(String rightTokenName) {
        this.rightTokenName = rightTokenName == null ? null : rightTokenName.trim();
    }

    public String getRightTokenAddress() {
        return rightTokenAddress;
    }

    public void setRightTokenAddress(String rightTokenAddress) {
        this.rightTokenAddress = rightTokenAddress == null ? null : rightTokenAddress.trim();
    }

    public BigDecimal getRightTokenAmount() {
        return rightTokenAmount;
    }

    public void setRightTokenAmount(BigDecimal rightTokenAmount) {
        this.rightTokenAmount = rightTokenAmount;
    }

    public BigDecimal getPriceRatio() {
        return priceRatio;
    }

    public void setPriceRatio(BigDecimal priceRatio) {
        this.priceRatio = priceRatio;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash == null ? null : transactionHash.trim();
    }

    public String getOriginatorWalletAddress() {
        return originatorWalletAddress;
    }

    public void setOriginatorWalletAddress(String originatorWalletAddress) {
        this.originatorWalletAddress = originatorWalletAddress == null ? null : originatorWalletAddress.trim();
    }

    public Integer getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(Integer blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime == null ? null : createTime.trim();
    }

    public BigDecimal getLeftTokenBalance() {
        return leftTokenBalance;
    }

    public void setLeftTokenBalance(BigDecimal leftTokenBalance) {
        this.leftTokenBalance = leftTokenBalance;
    }

    public BigDecimal getRightTokenBalance() {
        return rightTokenBalance;
    }

    public void setRightTokenBalance(BigDecimal rightTokenBalance) {
        this.rightTokenBalance = rightTokenBalance;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getIsEatOffer() {
        return isEatOffer;
    }

    public void setIsEatOffer(Integer isEatOffer) {
        this.isEatOffer = isEatOffer;
    }

    public Integer getEatOfferId() {
        return eatOfferId;
    }

    public void setEatOfferId(Integer eatOfferId) {
        this.eatOfferId = eatOfferId;
    }

    public Integer getIntervalBlock() {
        return intervalBlock;
    }

    public void setIntervalBlock(Integer intervalBlock) {
        this.intervalBlock = intervalBlock;
    }

    public BigDecimal getServiceCharge() {
        return serviceCharge;
    }

    public void setServiceCharge(BigDecimal serviceCharge) {
        this.serviceCharge = serviceCharge;
    }

    @Override
    public String toString() {
        return "OfferThreeData{" +
                "id=" + id +
                ", contractAddress='" + contractAddress + '\'' +
                ", leftTokenName='" + leftTokenName + '\'' +
                ", leftTokenAddress='" + leftTokenAddress + '\'' +
                ", leftTokenAmount=" + leftTokenAmount +
                ", rightTokenName='" + rightTokenName + '\'' +
                ", rightTokenAddress='" + rightTokenAddress + '\'' +
                ", rightTokenAmount=" + rightTokenAmount +
                ", priceRatio=" + priceRatio +
                ", transactionHash='" + transactionHash + '\'' +
                ", originatorWalletAddress='" + originatorWalletAddress + '\'' +
                ", blockNumber=" + blockNumber +
                ", createTime='" + createTime + '\'' +
                ", leftTokenBalance=" + leftTokenBalance +
                ", rightTokenBalance=" + rightTokenBalance +
                ", version=" + version +
                ", isEatOffer=" + isEatOffer +
                ", eatOfferId=" + eatOfferId +
                ", intervalBlock=" + intervalBlock +
                ", serviceCharge=" + serviceCharge +
                '}';
    }
}