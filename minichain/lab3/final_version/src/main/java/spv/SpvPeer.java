package spv;


import consensus.MinerPeer;
import data.*;
import network.NetWork;
import utils.SecurityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * 轻节点类，之储存区块头信息，不存储具体交易数据，可向全节点发送验证请求获取某交易的验证路径，以此验证该交易的真实性
 */

public class SpvPeer extends Thread{
    private final List<BlockHeader> headers = new ArrayList<>();
    private Account account;
    private final NetWork network;

    public SpvPeer(Account account, NetWork network){
        this.account = account;
        this.network = network;
    }

    /**
     * 添加一个区块头
     * @param blockHeader
     */
    public void accept(BlockHeader blockHeader){
        headers.add(blockHeader);
        // 接受后验证一下
//        verifyLatest();
    }

    /**
     * 如果有相关的交易，验证最新块的交易
     */
//    public void verifyLatest(){
//        List<Transaction> transactions = network.getTransactionsInLatestBlock(account.getWalletAddress());
//        if(transactions.isEmpty()){
//            return;
//        }
//
//        System.out.println("Account[" + account.getWalletAddress() + "] began to verify the transaction...");
//        for(Transaction transaction : transactions){
//            if(!simplifiedPaymentVerify(transaction.toString())){
//                System.out.println("verification failed!");
//                System.exit(-1);
//            }
//        }
//        System.out.println("Account[" + account.getWalletAddress() + "] verifies all transactions are successful!\n");
//    }

    /**
     * spv验证
     * @param txHash
     * @return
     */
    public boolean simplifiedPaymentVerify(String txHash){
        // 获取交易哈希
//        String txHash = SecurityUtil.sha256Digest(transaction.toString());

        // 通过网络向其他全节点获取验证路径
        MinerPeer minerPeer = network.getMinerPeer();
        Proof proof = minerPeer.getProof(txHash);

        if(proof == null){
            return false;
        }

        // 使用获得的验证路径计算merkle根哈希
        String hash = proof.getTxHash();
        for(Proof.Node node : proof.getPath()){
            switch (node.getOrientation()){
                case LEFT: hash = SecurityUtil.sha256Digest(node.getTxHash() + hash); break;
                case RIGHT: hash = SecurityUtil.sha256Digest(hash + node.getTxHash()); break;
                default: return false;
            }
        }

        // 获得本地区块头部中的根哈希
        int height = proof.getHeight();
        String localMerkleRootHash = headers.get(height).getMerkleRootHash();

        // 获取远程节点发送过来的哈希
        String remoteMerkleRootHash = proof.getMerkleRootHash();

        System.out.println("\n----------------> verify hash:\t" + txHash);
        System.out.println("calMerkleRootHash:\t\t" + hash);
        System.out.println("localMerkleRootHash:\t" + localMerkleRootHash);
        System.out.println("remoteMerkleRootHash:\t" + remoteMerkleRootHash);
        System.out.println();

        return hash.equals(localMerkleRootHash) && hash.equals(remoteMerkleRootHash);
    }

    @Override
    public void run() {
        while (true) {
            synchronized (network.getTransactionPool()) {
                TransactionPool transactionPool = network.getTransactionPool();
                while (transactionPool.isFull()) {
                    try {
                        transactionPool.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Scanner scan = new Scanner(System.in);
                if(account == null){
                    System.out.println("You should create a account! Please enter: create a account");
                    String str = scan.nextLine();
                    if(str.equals("create a account")){
                        account = network.create_account();
                        System.out.println("create successfully! account is " + account.getWalletAddress());
                    }
                    else{
                        System.out.println("You enter wrong.");
                    }
                }
                else{
                    // 功能说明
                    System.out.println("Now you can choose the following features:");
                    System.out.println("create your own transaction (enter: create a transaction).");
                    System.out.println("query your balance (enter: query balance).");
                    System.out.println("query a transaction (enter: query a transaction).");
                    System.out.println("create random transactions (enter: random transactions).");

                    String str = scan.nextLine();
                    System.out.println(str + ": ");
                    if(str.equals("")){
                        continue;
                    }
                    if(str.equals("create a transaction")){
                        System.out.println(
                                "please enter the index of the account you want to transfer and the number of money.");
                        int toaccount_index = scan.nextInt();
                        int amount = scan.nextInt();
                        Account toaccount = network.getAccounts().get(toaccount_index);
                        Transaction transaction = getOneTransaction(toaccount, amount);
                        if(transaction == null){
                            continue;
                        }
                        System.out.println("create a transaction, the txHash is " +
                                SecurityUtil.sha256Digest(transaction.toString()));
                        transactionPool.put(transaction);
                        if(transactionPool.isFull()){
                            transactionPool.notify();
                        }
                    }
                    else if(str.equals("query balance")){
                        int amount = getbalance();
                        System.out.println("The balance of your account is " + amount);
                    }
                    else if(str.equals("query transaction")){
                        System.out.println("please enter a txHash of transaction.");
                        String txHash = scan.nextLine();
                        if(simplifiedPaymentVerify(txHash)){
                            System.out.println("transaction exist");
                        }
                        else{
                            System.out.println("transaction doesn't exist.");
                        }
                    }
                    else if(str.equals("random transaction")){
                        while(!transactionPool.isFull()){
                            Transaction transaction = getRandomTransaction();
                            System.out.println("create random transaction, the txHash is "+
                                    SecurityUtil.sha256Digest(transaction.toString()));
                            transactionPool.put(transaction);
                            if(transactionPool.isFull()){
                                transaction.notify();
                                break;
                            }
                        }
                    }
                    System.out.println();
                }

//                Transaction randomOne = getOneTransaction();
//                transactionPool.put(randomOne);
//                if (transactionPool.isFull()) {
//                    transactionPool.notify();
//                }
            }
        }
    }

    private Transaction getOneTransaction(Account toaccount, int amount){
        Transaction transaction = null;
        List<Account> accounts = network.getAccounts();
        Account aAccount = account;
        Account bAccount = toaccount;

        // BTC不允许自己给自己转账
        if(aAccount == bAccount){
            System.out.println("you can't transfer to yourself.");
            return transaction;
        }
        String aWalletAddress = aAccount.getWalletAddress();
        String bWalletAddress = bAccount.getWalletAddress();
        UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(aWalletAddress);
        int aAmount = getbalance();
        int txAmount = amount;
        if(aAmount < amount){
            System.out.println("your balance can't afford this transaction. your balance is " + aAmount);
            return transaction;
        }

        List<UTXO> inUtxoList = new ArrayList<>();
        List<UTXO> outUtxoList = new ArrayList<>();
        byte[] aUnlockSign = SecurityUtil.signature(aAccount.getPublicKey().getEncoded(), aAccount.getPrivateKey());
        int inAmount = 0;
        for(UTXO utxo : aTrueUtxos){
            if(utxo.unlockScript(aUnlockSign, aAccount.getPublicKey())){
                inAmount += utxo.getAmount();
                inUtxoList.add(utxo);
                if(inAmount >= txAmount){
                    break;
                }
            }
        }

        if(inAmount < txAmount){
            System.out.println("the unlocked utxos is not enough.");
            return transaction;
        }
        outUtxoList.add(new UTXO(bWalletAddress, txAmount, bAccount.getPublicKey()));
        if(inAmount > txAmount){
            outUtxoList.add(new UTXO(aWalletAddress, inAmount - txAmount, aAccount.getPublicKey()));
        }

        UTXO[] inUtxos = inUtxoList.toArray(new UTXO[0]);
        UTXO[] outUtxos = outUtxoList.toArray(new UTXO[0]);
        byte[] data = SecurityUtil.utxos2Bytes(inUtxos, outUtxos);
        byte[] sign = SecurityUtil.signature(data, aAccount.getPrivateKey());
        long timestamp = System.currentTimeMillis();
        transaction = new Transaction(inUtxos, outUtxos, sign, aAccount.getPublicKey(), timestamp);
        return transaction;
    }

    private int getbalance(){
        String walletAddress = account.getWalletAddress();
        UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(walletAddress);
        int amount = account.getAmount(aTrueUtxos);
        return amount;
    }

    private Transaction getRandomTransaction() {
        Random random = new Random();
        Transaction transaction = null;  // 返回的交易
        List<Account> accounts = network.getAccounts();  // 获取账户数组

        while(true){
            // 随机获取两个账户A和B
            Account aAccount = accounts.get(random.nextInt(accounts.size()));
            Account bAccount = accounts.get(random.nextInt(accounts.size()));
            // BTC不允许给自己转账
            if(aAccount == bAccount){
                continue;
            }

            // 获得钱包地址
            String aWalletAddress = aAccount.getWalletAddress();
            String bWalletAddress = bAccount.getWalletAddress();

            // 获取A可用的utxo并计算余额
            UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(aWalletAddress);
            int aAmount = aAccount.getAmount(aTrueUtxos);
            // 若A账户的余额为0，则无法构建交易，重新随机生成
            if(aAmount == 0){
                continue;
            }

            // 随机生成交易数额[1, aAmount]之间
            int txAmount = random.nextInt(aAmount) + 1;
            // 构建InUtxo和OutUtxo
            List<UTXO> inUtxoList = new ArrayList<>();
            List<UTXO> outUtxoList = new ArrayList<>();

            byte[] aUnlockSign = SecurityUtil.signature(aAccount.getPublicKey().getEncoded(), aAccount.getPrivateKey());

            // 选择输入总额 >= 交易数额的utxo
            int inAmount = 0;
            for(UTXO utxo : aTrueUtxos){
                // 解锁成功才能使用该utxo
                if(utxo.unlockScript(aUnlockSign, aAccount.getPublicKey())){
                    inAmount += utxo.getAmount();
                    inUtxoList.add(utxo);
                    if(inAmount >= txAmount){
                        break;
                    }
                }
            }
            // 可解锁的utxo总额仍不足以支付交易数额，则重新随机
            if(inAmount < txAmount){
                continue;
            }

            // 构建输出OutUtxos，A账户向B账户支付txAmount，同时输入对方的公钥以供生成公钥哈希
            outUtxoList.add(new UTXO(bWalletAddress, txAmount, bAccount.getPublicKey()));
            // 如果有余额，则找零，给自己的utxo
            if(inAmount > txAmount){
                outUtxoList.add(new UTXO(aWalletAddress, inAmount - txAmount, aAccount.getPublicKey()));
            }

            // 导出固定utxo数组
            UTXO[] inUtxos = inUtxoList.toArray(new UTXO[0]);
            UTXO[] outUtxos = outUtxoList.toArray(new UTXO[0]);

            // A账户需要对整个交易进行私钥签名，确保交易不会被篡改
            // 获取要签名的数据
            byte[] data = SecurityUtil.utxos2Bytes(inUtxos, outUtxos);
            // A账户使用私钥签名
            byte[] sign = SecurityUtil.signature(data, aAccount.getPrivateKey());
            // 交易时间戳
            long timestamp = System.currentTimeMillis();
            // 构造交易
            transaction = new Transaction(inUtxos, outUtxos, sign, aAccount.getPublicKey(), timestamp);
            // 成功构造一笔交易，退出循环
            break;
        }
        return transaction;
    }
}
