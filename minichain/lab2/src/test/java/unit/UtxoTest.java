package unit;

import consensus.MinerNode;
import data.*;
import utils.SecurityUtil;

import java.util.ArrayList;
import java.util.List;

public class UtxoTest {

    @org.junit.Test
    public void utxoTest(){
        // 相关类初始化
        BlockChain blockChain = new BlockChain();
        TransactionPool transactionPool = new TransactionPool(1);  // 交易池有一个交易就会被矿工打包
        MinerNode minerNode = new MinerNode(transactionPool, blockChain);

        // 生成一笔特殊交易
        Transaction transaction = getOneTransaction(blockChain);
        transactionPool.put(transaction);
        minerNode.run();  // 开始挖矿
    }

    /**
     * accounts[1]支付给accounts[2] 1000元，accounts[1]使用自己的公钥对交易签名
     * 参考TransactionProducer中的getOneTransaction
     * @param blockChain
     * @return
     */
    Transaction getOneTransaction(BlockChain blockChain){
        // TODO
        Transaction transaction = null;
        Account[] accounts = blockChain.getAccounts();

        Account account1 = accounts[1];
        Account account2 = accounts[2];

        String walletAddress1 = account1.getWalletAddress();
        String walletAddress2 = account2.getWalletAddress();

        // 获取account1可用utxo并计算余额
        UTXO[] account1Utxo = blockChain.getTrueUtxos(walletAddress1);
        int amount1 = account1.getAmount(account1Utxo);
        byte[] UnlockSign1 = SecurityUtil.signature(account1.getPublicKey().getEncoded(), account1.getPrivateKey());

        if(amount1 >= 1000){
            int txAmount = 1000;  // 交易金额
            // 输入和输出utxo列表
            List<UTXO> inUtxoList = new ArrayList<>();
            List<UTXO> outUtxoList = new ArrayList<>();

            int totalInputAmount = 0;
            for(UTXO utxo : account1Utxo){
                if(utxo.unlockScript(UnlockSign1, account1.getPublicKey())){
                    totalInputAmount += utxo.getAmount();
                    inUtxoList.add(utxo);
                    if(totalInputAmount >= txAmount){
                        break;
                    }
                }
            }
            outUtxoList.add(new UTXO(walletAddress2, txAmount, account2.getPublicKey()));
            // 找零给自己
            if(totalInputAmount > txAmount){
                outUtxoList.add(new UTXO(walletAddress1, totalInputAmount - txAmount, account1.getPublicKey()));
            }

            // 导出固定UTXO数组
            UTXO[] inUtxos = inUtxoList.toArray(new UTXO[0]);
            UTXO[] outUtxos = outUtxoList.toArray(new UTXO[0]);

            // 获取要签名的数据
            byte[] data = SecurityUtil.utxos2Bytes(inUtxos, outUtxos);
            // account1使用私钥签名
            byte[] sign = SecurityUtil.signature(data, account1.getPrivateKey());
            long timestamp = System.currentTimeMillis();
            transaction = new Transaction(inUtxos, outUtxos, sign, account1.getPublicKey(), timestamp);
//            System.out.println("Success!");
        }

        return transaction;
    }
}
