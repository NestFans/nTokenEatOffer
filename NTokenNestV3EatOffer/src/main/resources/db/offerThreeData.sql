CREATE TABLE `offer_three_data`  (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `contract_address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '报价合约地址',
  `left_token_name` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT 'ETH',
  `left_token_address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '报价ETH地址：0xethereum',
  `left_token_amount` decimal(30, 0) NOT NULL COMMENT '报价ETH数量',
  `right_token_name` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '报价ERC20代币名字',
  `right_token_address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '报价ERC20代币地址',
  `right_token_amount` decimal(30, 0) NOT NULL COMMENT '报价ERC20数量',
  `price_ratio` decimal(30, 18) NOT NULL COMMENT '报价产生的价格',
  `transaction_hash` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '交易hah',
  `originator_wallet_address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '报价人地址',
  `block_number` int(11) NOT NULL COMMENT '交易完成时区块高度',
  `create_time` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL COMMENT '交易打包时间',
  `left_token_balance` decimal(30, 0) NOT NULL COMMENT '未被吃掉的ETH余额',
  `right_token_balance` decimal(30, 0) NOT NULL COMMENT '未被吃掉的ERC20余额',
  `version` int(2) NOT NULL COMMENT '0：25区块内的未被吃完的报价；1：超过25区块或者被吃完的报价',
  `is_eat_offer` int(2) NULL DEFAULT NULL COMMENT '0：正常报价；1：吃单后的报价',
  `eat_offer_id` int(11) NULL DEFAULT NULL COMMENT '吃掉的报价ID',
  `interval_block` int(11) NOT NULL COMMENT '确认的区块数量',
  `service_charge` decimal(30, 0) NOT NULL COMMENT '手续费',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `transaction_hash`(`transaction_hash`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 106 CHARACTER SET = latin1 COLLATE = latin1_swedish_ci ROW_FORMAT = Compact;

SET FOREIGN_KEY_CHECKS = 1;