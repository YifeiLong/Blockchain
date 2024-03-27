package network;

import config.MiniChainConfig;
import consensus.MinerPeer;
import consensus.TransactionProducer;
import data.*;
import spv.SpvPeer;
import utils.SecurityUtil;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 该类模拟一个网络环境，在该网络中主要有区块链和矿工，另外地，出于工程实现的角度，还有一个交易池和一个生成随机交易的线程
 *
 */
public class NetWork {

    private final Account[] accounts;
    private final SpvPeer[] spvPeers;
    private final TransactionPool transactionPool;
    private final TransactionProducer transactionProducer;
    private final BlockChain blockChain;
    private final MinerPeer minerPeer;

    /**
     * 系统中几个主要成员的初始化，所有成员都保持和网络的连接，以此来降低耦合度
     */
    public NetWork() {
        System.out.println("\naccounts and spvPeers config...");
        accounts = new Account[MiniChainConfig.ACCOUNT_NUM];
        spvPeers = new SpvPeer[MiniChainConfig.ACCOUNT_NUM];
        for(int i = 0; i < MiniChainConfig.ACCOUNT_NUM; ++i){
            accounts[i] = new Account();
            System.out.println("network register new account: " + accounts[i]);
            spvPeers[i] = new SpvPeer(accounts[i], this);
        }

        System.out.println("\ntransactionPool config...");
        transactionPool = new TransactionPool(MiniChainConfig.MAX_TRANSACTION_COUNT);

        System.out.println("\ntransactionProducer config...");
        transactionProducer = new TransactionProducer(this);

        System.out.println("\nblockChain config...");
        blockChain = new BlockChain(this);

        System.out.println("\nminerPeer config...");
        minerPeer = new MinerPeer(blockChain, this);

        System.out.println("\nnetwork start!\n");

        minerPeer.broadcast(blockChain.getLatestBlock());

        theyHaveADayDream();
    }

    public void theyHaveADayDream(){
        UTXO[] outUtxo = new UTXO[accounts.length];
        // 在创世块中为每个账户分配一定金额的UTXO，便于后续交易进行
        for(int i = 0; i < accounts.length; ++i){
            outUtxo[i] = new UTXO(accounts[i].getWalletAddress(), MiniChainConfig.INIT_AMOUNT,
                    accounts[i].getPublicKey());
        }
        // 公私钥
        KeyPair dayDreamKeyPair = SecurityUtil.secp256k1Generate();
        PublicKey dayDreamPublicKey = dayDreamKeyPair.getPublic();
        PrivateKey dayDreamPrivateKey = dayDreamKeyPair.getPrivate();
        // 签名内容
        byte[] sign = SecurityUtil.signature("Everything in the dream!".getBytes(StandardCharsets.UTF_8),
                dayDreamPrivateKey);
        // 构造交易
        Transaction transaction = new Transaction(new UTXO[]{}, outUtxo, sign,
                dayDreamPublicKey, System.currentTimeMillis());
        Transaction[] transactions = {transaction};
        String preBlockHash = SecurityUtil.sha256Digest(blockChain.getLatestBlock().toString());
        String merkleRootHash = SecurityUtil.sha256Digest(transaction.toString());
        // 构造区块
        BlockHeader blockHeader = new BlockHeader(preBlockHash, merkleRootHash, Math.abs(new Random().nextLong()));
        BlockBody blockBody = new BlockBody(merkleRootHash, transactions);
        Block block = new Block(blockHeader, blockBody);
        // 添加到链中
        blockChain.addNewBlock(block);
        // 通过网络获取矿工节点，然后将初始区块广播出去
        minerPeer.broadcast(block);
    }

    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    public Account[] getAccounts() {
        return accounts;
    }

    public SpvPeer[] getSpvPeers() {
        return spvPeers;
    }

    public MinerPeer getMinerPeer() {
        return minerPeer;
    }

    public BlockChain getBlockChain() {
        return blockChain;
    }

    public TransactionProducer getTransactionProducer() {
        return transactionProducer;
    }

    /**
     * 启动挖矿线程和生成随机交易的线程
     */
    public void start() {
        transactionProducer.start();
        minerPeer.start();
    }

    /**
     * 在最新区块中查找和某钱包地址有关的交易
     * @param walletAddress
     * @return
     */
    public List<Transaction> getTransactionsInLatestBlock(String walletAddress){
        List<Transaction> list = new ArrayList<>();
        Block block = blockChain.getLatestBlock();
        // 遍历所有区块所有交易所有的UTXO，查看钱包地址是否相符合
        for(Transaction transaction : block.getBlockBody().getTransactions()){
            boolean have = false;
            for(UTXO utxo : transaction.getInUtxos()){
                if(utxo.getWalletAddress().equals(walletAddress)){
                    list.add(transaction);
                    have = true;
                    break;
                }
            }
            if(have){
                continue;
            }
            for(UTXO utxo : transaction.getOutUtxos()){
                if(utxo.getWalletAddress().equals(walletAddress)){
                    list.add(transaction);
                    break;
                }
            }
        }
        return list;
    }
}
