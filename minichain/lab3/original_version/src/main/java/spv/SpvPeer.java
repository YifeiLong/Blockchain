package spv;


import consensus.MinerPeer;
import data.Account;
import data.BlockHeader;
import data.Transaction;
import network.NetWork;
import utils.SecurityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻节点类，之储存区块头信息，不存储具体交易数据，可向全节点发送验证请求获取某交易的验证路径，以此验证该交易的真实性
 */

public class SpvPeer {
    private final List<BlockHeader> headers = new ArrayList<>();
    private final Account account;
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
        verifyLatest();
    }

    /**
     * 如果有相关的交易，验证最新块的交易
     */
    public void verifyLatest(){
        List<Transaction> transactions = network.getTransactionsInLatestBlock(account.getWalletAddress());
        if(transactions.isEmpty()){
            return;
        }

        System.out.println("Account[" + account.getWalletAddress() + "] began to verify the transaction...");
        for(Transaction transaction : transactions){
            if(!simplifiedPaymentVerify(transaction)){
                System.out.println("verification failed!");
                System.exit(-1);
            }
        }
        System.out.println("Account[" + account.getWalletAddress() + "] verifies all transactions are successful!\n");
    }

    /**
     * spv验证
     * @param transaction
     * @return
     */
    public boolean simplifiedPaymentVerify(Transaction transaction){
        // 获取交易哈希
        String txHash = SecurityUtil.sha256Digest(transaction.toString());

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
}
