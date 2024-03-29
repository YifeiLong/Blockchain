package consensus;

import config.MiniChainConfig;
import data.*;
import network.NetWork;
import spv.Proof;
import spv.SpvPeer;
import utils.MinerUtil;
import utils.SecurityUtil;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import java.util.Random;

/**
 * 矿工线程
 *
 * 该线程的主要工作就是不断的进行交易打包、Merkle树根哈希值计算、构造区块，
 * 然后尝试使用不同的随机字段（nonce）进行区块的哈希值计算以生成新的区块添加到区块中
 *
 * 这里需要你实现的功能函数为：getBlockBody、getMerkleRootHash、mine和getBlock，具体的需求见上述方法前的注释，
 * 除此之外，该类中的其他方法、变量，以及其他类中的方法和变量，均无需修改，否则可能影响系统的正确运行
 *
 * 如有疑问，及时交流
 *
 */
public class MinerPeer extends Thread {

    private final BlockChain blockChain;
    private final NetWork netWork;

    public MinerPeer(BlockChain blockChain, NetWork netWork){
        this.blockChain = blockChain;
        this.netWork = netWork;
    }

    /**
     * 线程运行主函数
     */
    @Override
    public void run(){
        while(true){
            // 锁住网络中的交易池
            synchronized(netWork.getTransactionPool()){
                TransactionPool transactionPool = netWork.getTransactionPool();

                while(!transactionPool.isFull()){
                    try{
                        transactionPool.wait();
                    } catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }

                Transaction[] transactions = transactionPool.getAll();

                // 对该交易的签名进行验签
                if(!check(transactions)){
                    System.out.println("transactions error!");
                    System.exit(-1);
                }

                BlockBody blockBody = getBlockBody(transactions);
                Block block = mine(blockBody);
                broadcast(block);

                System.out.println("the sum of all account amount: " + blockChain.getAllAccountAmount());
                transactionPool.notify();
            }
        }
    }

    /**
     * 矿工检查每笔交易的签名是否正确，是否有篡改
     * @param transactions
     * @return
     */
    private boolean check(Transaction[] transactions){
        for(int i = 0; i < transactions.length; ++i){
            Transaction transaction = transactions[i];
            // 签名的数据是该交易的inUtxos和outUtxos
            byte[] data = SecurityUtil.utxos2Bytes(transaction.getInUtxos(), transaction.getOutUtxos());
            byte[] sign = transaction.getSendSign();
            PublicKey publicKey = transaction.getSendPublicKey();
            if(!SecurityUtil.verify(data, sign, publicKey)){
                return false;
            }
        }
        return true;
    }

    /**
     * 该方法根据传入的参数中的交易，构造并返回一个相应的区块体对象
     *
     * 查看BlockBody类中的字段以及构造方法你会发现，还需要根据这些交易计算Merkle树的根哈希值
     *
     * @param transactions 一批次的交易
     *
     * @return 根据参数中的交易构造出的区块体
     */
    public BlockBody getBlockBody(Transaction[] transactions) {
        assert transactions != null && transactions.length == MiniChainConfig.MAX_TRANSACTION_COUNT;
        // todo
        List<String> list = new ArrayList<>();
        for(Transaction transaction : transactions){
            String txHash = SecurityUtil.sha256Digest(transaction.toString());
            list.add(txHash);
        }

        while(list.size() != 1){
            List<String> newList = new ArrayList<>();
            for(int i = 0; i < list.size(); i += 2){
                String leftHash = list.get(i);
                String rightHash = (i + 1 < list.size() ? list.get(i + 1) : leftHash);
                String parentHash = SecurityUtil.sha256Digest(leftHash + rightHash);
                newList.add(parentHash);
            }
            list = newList;
        }
        BlockBody blockBody = new BlockBody(list.get(0), transactions);
        return blockBody;
    }

    /**
     * 该方法即在循环中完成"挖矿"操作，其实就是通过不断的变换区块中的nonce字段，直至区块的哈希值满足难度条件，
     * 即可将该区块加入区块链中
     *
     * @param blockBody 区块体
     */
    private Block mine(BlockBody blockBody) {
        Block block = getBlock(blockBody);
        while (true) {
            String blockHash = SecurityUtil.sha256Digest(block.toString());
            if (blockHash.startsWith(MinerUtil.hashPrefixTarget())) {
                System.out.println("Mined a new Block! Detail of the new Block : ");
                System.out.println(block.toString());
                System.out.println("And the hash of this Block is : " + SecurityUtil.sha256Digest(block.toString()) +
                                    ", you will see the hash value in next Block's preBlockHash field.");
                System.out.println();
                blockChain.addNewBlock(block);
                break;
            } else {
                // todo
                long nonce = new Random().nextLong();
                block.getBlockHeader().setNonce(nonce);
            }
        }
        return block;
    }

    /**
     * 该方法供mine方法调用，其功能为根据传入的区块体参数，构造一个区块对象返回，
     * 也就是说，你需要构造一个区块头对象，然后用一个区块对象组合区块头和区块体
     *
     * 建议查看BlockHeader类中的字段和注释，有助于你实现该方法
     *
     * @param blockBody 区块体
     *
     * @return 相应的区块对象
     */
    public Block getBlock(BlockBody blockBody) {
        //todo
        Block lastBlock = blockChain.getLatestBlock();
        String newHash = SecurityUtil.sha256Digest(lastBlock.toString());
        Random num = new Random();
        BlockHeader newHeader = new BlockHeader(newHash, blockBody.getMerkleRootHash(), num.nextLong());
        return new Block(newHeader, blockBody);
    }

    /**
     * 返回某交易哈希的验证路径
     * @param proofTxHash spv请求验证交易的哈希
     * @return 返回验证路径，如果不存在该交易则返回null
     */
    public Proof getProof(String proofTxHash){
        Block proofBlock = null;
        int proofHeight = -1;

        // 遍历链上所有区块内的交易，计算其哈希，找出要验证哈希值的交易所在的区块
        for(Block block : blockChain.getBlocks()){
            ++ proofHeight;
            for(Transaction transaction : block.getBlockBody().getTransactions()){
                String txHash = SecurityUtil.sha256Digest(transaction.toString());
                if(txHash.equals(proofTxHash)){
                    proofBlock = block;
                    break;
                }
            }
            if(proofBlock != null){
                break;
            }
        }

        // 没找到该哈希的交易，返回null
        if(proofBlock == null){
            return null;
        }

        // 重新计算merkle树获得路径哈希值，同时记录相关的节点偏向信息，构建验证路径节点
        List<Proof.Node> proofPath = new ArrayList<>();
        List<String> list = new ArrayList<>();
        String pathHash = proofTxHash;
        for(Transaction transaction : proofBlock.getBlockBody().getTransactions()){
            String txHash = SecurityUtil.sha256Digest(transaction.toString());
            list.add(txHash);
        }

        while(list.size() != 1){
            List<String> newList = new ArrayList<>();
            for(int i = 0; i < list.size(); i += 2){
                String leftHash = list.get(i);
                String rightHash = (i + 1 < list.size() ? list.get(i + 1) : leftHash);
                String parentHash = SecurityUtil.sha256Digest(leftHash + rightHash);
                newList.add(parentHash);

                if(pathHash.equals(leftHash)){
                    Proof.Node proofNode = new Proof.Node(rightHash, Proof.Orientation.RIGHT);
                    proofPath.add(proofNode);
                    pathHash = parentHash;
                }
                else if(pathHash.equals(rightHash)){
                    Proof.Node proofNode = new Proof.Node(leftHash, Proof.Orientation.LEFT);
                    proofPath.add(proofNode);
                    pathHash = parentHash;
                }
            }
            list = newList;
        }
        String proofMerkleRootHash = list.get(0);

        return new Proof(proofTxHash, proofMerkleRootHash, proofHeight, proofPath);
    }

    public void broadcast(Block block){
        // 每个spv节点接受区块头
        SpvPeer spvPeer = netWork.getSpvPeer();
        spvPeer.accept(block.getBlockHeader());
    }
}
