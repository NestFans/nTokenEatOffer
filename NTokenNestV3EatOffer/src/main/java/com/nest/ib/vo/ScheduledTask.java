package com.nest.ib.vo;

import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.OfferCoinPriceService;

import com.nest.ib.service.OfferThreeDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * ClassName:SendMarketTasks
 * Description:
 */
@Component
public class ScheduledTask {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(50);
        return taskScheduler;
    }

    @Autowired
    private OfferCoinPriceService offerCoinPriceService;
    @Autowired
    private EatOfferAndTransactionService eatOfferAndTransactionService;
    @Autowired
    private OfferThreeDataService offerThreeDataService;



    /**
    *   新的更新价格(实时的价格)
    */
    @Scheduled(fixedDelay = 10000)
    public void updatePrice(){
        offerCoinPriceService.getCoinPrice();
    }
    /**
    *   吃单报价
    */
    @Scheduled(fixedDelay = 10000)
    public void eatOfferContract(){
        eatOfferAndTransactionService.eatOffer();
    }
    /**
     * 取回资产
     */
    @Scheduled(fixedDelay = 60000)
    public void retrieveAssets(){
        try {
            eatOfferAndTransactionService.retrieveAssets();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
    /**
    *   存储所有的报价，吃单数据
    */
    @Scheduled(fixedDelay = 5000)
    public void saveOfferData(){
        offerThreeDataService.save();
    }

}
