package com.nest.ib.service.serviceImpl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nest.ib.service.OfferCoinPriceService;
import com.nest.ib.utils.HttpClientUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * ClassName:OfferCoinPriceServiceImpl
 * Description:
 */
@Service
public class OfferCoinPriceServiceImpl implements OfferCoinPriceService{
    private static final Logger LOG = LoggerFactory.getLogger(OfferCoinPriceServiceImpl.class);
    private static final String HUOBI_PRICE_API = "https://api.huobi.pro/market/history/trade?symbol=hteth&size=1";
    // ETH-HT价格
    private static BigDecimal HT_ETH_PRICE = null;

    @Override
    public void updateCoinPrice() {
        BigDecimal price = getExchangePrice(HUOBI_PRICE_API);
        if(price != null && price.compareTo(new BigDecimal("0"))>0){
            HT_ETH_PRICE = price;
            LOG.info("更新HT-ETH价格: " + HT_ETH_PRICE);
        }
    }

    @Override
    public BigDecimal getCoinPrice() {
        return HT_ETH_PRICE;
    }


    private static BigDecimal getExchangePrice(String url){
        String s = HttpClientUtil.sendHttpGet(url);
        if(s == null){
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONArray data = jsonObject.getJSONArray("data");
        if(data == null){
            return null;
        }
        BigDecimal totalPrice = new BigDecimal("0");
        BigDecimal n = new BigDecimal("0");
        if(data.size() == 0){
            return null;
        }
        for(int i=0; i<data.size(); i++){
            Object o = data.get(i);
            JSONObject jsonObject1 = JSONObject.parseObject(String.valueOf(o));
            JSONArray data1 = jsonObject1.getJSONArray("data");
            if(data1 == null){
                continue;
            }
            if(data1.size() == 0){
                continue;
            }
            JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(data1.get(0)));
            BigDecimal price = jsonObject2.getBigDecimal("price");
            if(price == null){
                continue;
            }
            totalPrice = totalPrice.add(price);
            n = n.add(new BigDecimal("1"));
        }
        if(n.compareTo(new BigDecimal("0")) > 0){
            return totalPrice.divide(n,18,BigDecimal.ROUND_DOWN);
        }
        return null;
    }
}
