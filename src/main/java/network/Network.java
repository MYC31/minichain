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
public class Network {

    private final List<Account> accounts = new ArrayList<>();
    private final SpvPeer spvPeer;
//    private final SpvPeer[] spvPeers;
    private final TransactionPool transactionPool;
//    private final TransactionProducer transactionProducer;
    private final BlockChain blockChain;
    private final MinerPeer minerPeer;

    /**
     * 系统中几个主要成员的初始化，所有成员都保持和网络的连接，以此来降低耦合度
     */
    public Network() {

        // 初始化用户和spv节点, 并注册到网络中
        System.out.println("\naccounts and spvPeers config...");
        for (int i = 0; i < MiniChainConfig.ACCOUNT_NUM; ++i) {
            accounts.add(new Account());
            System.out.println("network register new account: " + accounts.get(i));
            // 每个账户创建一个spv轻节点， 并获得与网络的连接
        }

        // 创建交易池，网络中会有交易涌入
        System.out.println("\ntransactionPool config...");
        transactionPool = new TransactionPool(MiniChainConfig.MAX_TRANSACTION_COUNT);

//        // 交易生产者，负责生产交易，维持与网络的连接
//        System.out.println("\ntransactionProducer config...");
//        transactionProducer = new TransactionProducer(this);
        spvPeer = new SpvPeer(null, this);

        // 初始化一条区块链，后续由矿工节点维护，可当作这条链在网络中存储于矿工节点
        System.out.println("\nblockChain config...");
        blockChain = new BlockChain(this);

        // 创建矿工节点，维持与网络的连接
        System.out.println("\nminerPeer config...");
        minerPeer = new MinerPeer(blockChain, this);

        System.out.println("\nnetwork start!\n");

        minerPeer.broadcast(blockChain.getLatestBlock());

        // 发送神秘大礼：在第一个区块放入一些utxo，让所有账户拥有一笔金额
        theyHaveADayDream();
    }

    /**
     * 让人富有的神秘函数
     */
    public void theyHaveADayDream() {

        // 在创世区块中为每个账户分配一定金额的 utxo，便于后面交易的进行
        UTXO[] outUtxos = new UTXO[accounts.size()];
        for (int i = 0;  i < accounts.size(); ++i) {
            outUtxos[i] = new UTXO(accounts.get(i).getWalletAddress(), MiniChainConfig.INIT_AMOUNT, accounts.get(i).getPublicKey());
        }
        // 神秘的公私钥
        KeyPair dayDreamKeyPair = SecurityUtil.secp256k1Generate();
        PublicKey dayDreamPublicKey = dayDreamKeyPair.getPublic();
        PrivateKey dayDreamPrivateKey = dayDreamKeyPair.getPrivate();
        // 神秘的签名内容
        byte[] sign = SecurityUtil.signature("Everything in the dream!".getBytes(StandardCharsets.UTF_8), dayDreamPrivateKey);
        // 构造交易
        Transaction transaction = new Transaction(new UTXO[]{}, outUtxos, sign, dayDreamPublicKey, System.currentTimeMillis());
        // 交易数组只有这一个交易
        Transaction[] transactions = { transaction };
        // 前一个区块的哈希
        String preBlockHash = SecurityUtil.sha256Digest(blockChain.getLatestBlock().toString());
        // 因为本区块只有一个交易，所以merkle根哈希即为该交易的哈希
        String merkleRootHash = SecurityUtil.sha256Digest(transaction.toString());
        // 构建区块
        BlockHeader blockHeader = new BlockHeader(preBlockHash, merkleRootHash, Math.abs(new Random().nextLong()));
        BlockBody blockBody = new BlockBody(merkleRootHash, transactions);
        Block block = new Block(blockHeader, blockBody);
        // 添加到链中
        blockChain.addNewBlock(block);

        minerPeer.broadcast(block);


    }

    /**
     * 启动挖矿线程和生成随机交易的线程
     */
    public void start() {
        minerPeer.start();
        spvPeer.start();
    }

    public BlockChain getBlockChain() {
        return blockChain;
    }

    public MinerPeer getMinerPeer() {
        return minerPeer;
    }

    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

//    public TransactionProducer getTransactionProducer() {
//        return transactionProducer;
//    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public SpvPeer getSpvPeer() {return spvPeer;}

    public List<Transaction> getTransactionsInLatestBlock(String walletAddress) {
        List<Transaction> list = new ArrayList<>();
        Block block = blockChain.getLatestBlock();
        for (Transaction transaction : block.getBlockBody().getTransactions()) {
            boolean have = false;
            for (UTXO utxo : transaction.getInUtxos()) {
                if (utxo.getWalletAddress().equals(walletAddress)) {
                    list.add(transaction);
                    have = true;
                    break;
                }
            }
            if (have) {
                continue;
            }
            for (UTXO utxo : transaction.getOutUtxos()) {
                if (utxo.getWalletAddress().equals(walletAddress)) {
                    list.add(transaction);
                    break;
                }
            }
        }
        return list;
    }

    public Account create_account() {
        Account account = new Account();
        accounts.add(account);
        UTXO[] outUtxos = new UTXO[1];
        outUtxos[0] = new UTXO(account.getWalletAddress(), MiniChainConfig.INIT_AMOUNT, account.getPublicKey());

        KeyPair dayDreamKeyPair = SecurityUtil.secp256k1Generate();
        PublicKey dayDreamPublicKey = dayDreamKeyPair.getPublic();
        PrivateKey dayDreamPrivateKey = dayDreamKeyPair.getPrivate();

        byte[] sign = SecurityUtil.signature("Everything in the dream!".getBytes(StandardCharsets.UTF_8), dayDreamPrivateKey);
        Transaction transaction = new Transaction(new UTXO[]{}, outUtxos, sign, dayDreamPublicKey, System.currentTimeMillis());
        Transaction[] transactions = { transaction };

        String preBlockHash = SecurityUtil.sha256Digest(blockChain.getLatestBlock().toString());
        String merkleRootHash = SecurityUtil.sha256Digest(transaction.toString());

        BlockHeader blockHeader = new BlockHeader(preBlockHash, merkleRootHash, Math.abs(new Random().nextLong()));
        BlockBody blockBody = new BlockBody(merkleRootHash, transactions);
        Block block = new Block(blockHeader, blockBody);

        blockChain.addNewBlock(block);
        minerPeer.broadcast(block);
        return account;
    }

}
