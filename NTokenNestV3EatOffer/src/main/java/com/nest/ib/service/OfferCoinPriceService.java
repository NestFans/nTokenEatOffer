package com.nest.ib.service;

import java.math.BigDecimal;

/**
 * ClassName:OfferCoinPriceService
 * Description:
 */
public interface OfferCoinPriceService {
    void updateCoinPrice();
    BigDecimal getCoinPrice();
}
