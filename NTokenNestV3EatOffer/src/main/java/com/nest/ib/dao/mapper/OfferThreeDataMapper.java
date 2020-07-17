package com.nest.ib.dao.mapper;

import com.nest.ib.model.OfferThreeData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OfferThreeDataMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(OfferThreeData record);

    int insertSelective(OfferThreeData record);

    OfferThreeData selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(OfferThreeData record);

    int updateByPrimaryKey(OfferThreeData record);

    List<OfferThreeData> selectOverBlockNumberAddIntervalBlock(@Param("blockNumber") int blockNumber,@Param("name")String name);

    List<OfferThreeData> selectBelowBlockNumberAddIntervalBlockByWalletAddress(@Param("blockNumber") int intValue,
                                                                               @Param("walletAddress")String walletAddress,
                                                                               @Param("name")String name);

    OfferThreeData selectByTransactionHash(@Param("transactionHash") String transactionHash);
}