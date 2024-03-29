package data;

import utils.SecurityUtil;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Stack;

public class UTXO {
    private final String walletAddress;
    private final int amount;
    private final byte[] publicKeyHash;

    /**
     * 构建一个UTXO
     * @param walletAddress 交易获得方的钱包地址
     * @param amount 比特币数额
     * @param publicKey 交易获得方的公钥（公开）
     */
    public UTXO(String walletAddress, int amount, PublicKey publicKey){
        this.walletAddress = walletAddress;
        this.amount = amount;
        // 对公钥进行哈希摘要，作为解锁脚本数据
        publicKeyHash = SecurityUtil.ripemd160Digest(SecurityUtil.sha256Digest(publicKey.getEncoded()));
    }

    // 解锁脚本
    public boolean unlockScript(byte[] sign, PublicKey publicKey){
        Stack<byte[]> stack = new Stack<>();
        // <sig> 签名入栈
        stack.push(sign);
        // <PubK> 公钥入栈
        stack.push(publicKey.getEncoded());
        // DUP 复制一份栈顶数据，peek()为java容器获取栈顶元素的函数
        stack.push(stack.peek());
        // HASH160 弹出栈顶元素，进行哈希摘要，然后将其入栈
        byte[] data = stack.pop();  // 栈顶元素为PubK
        stack.push(SecurityUtil.ripemd160Digest(SecurityUtil.sha256Digest(data)));
        // <PubHash> utxo先前保存的公钥哈希入栈
        stack.push(publicKeyHash);
        // EQUALVERIFY 比较栈顶的两个公钥哈希是否相同，不相同则解锁失败
        byte[] publicKeyHash1 = stack.pop();
        byte[] publicKeyHash2 = stack.pop();
        if(!Arrays.equals(publicKeyHash1, publicKeyHash2)){
            return false;
        }
        // CHECKSIG 检查签名是否正确，正确则入栈 TRUE
        byte[] publicKeyEncoded = stack.pop();  // 弹出的是二进制，这里无法用来验签，所以仍用PublicKey形式的公钥验签
        byte[] sign1 = stack.pop();
        // 栈内：TRUE（验证正确情况下）
        return SecurityUtil.verify(publicKey.getEncoded(), sign1, publicKey);
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public int getAmount() {
        return amount;
    }

    public byte[] getPublicKeyHash() {
        return publicKeyHash;
    }

    @Override
    public String toString() {
        return "\n\tUTXO{" +
                "walletAddress='" + walletAddress + '\'' +
                ", amount=" + amount +
                ", publicKeyHash=" + SecurityUtil.bytes2HexString(publicKeyHash) +
                '}';
    }
}
