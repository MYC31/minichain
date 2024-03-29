package spv;

import com.sun.tools.jdeprscan.scan.Scan;
import consensus.MinerPeer;
import data.*;
import network.Network;
import utils.SecurityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class SpvPeer extends Thread {
    private final List<BlockHeader> headers = new ArrayList<>();

    private Account account;

    private final Network network;

    public SpvPeer(Account account, Network network) {
        this.account = account;
        this.network = network;
    }

    public void run() {
        while (true) {
            // 从网络中获取交易池
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
                if (account == null) {
                    System.out.println("You should create an account! Please enetr:create an account");
                    String str = scan.nextLine();
                    if (str.equals("create an account")) {
                        account = network.create_account();
                        System.out.println("create successfully! account is " + account.getWalletAddress());
                    } else {
                        System.out.println("You enter wrong.");
                    }
                } else {
                    System.out.println("Now you can choose the following features:");
                    System.out.println("create your cown transaction(enter:create a transaction).");
                    System.out.println("query your balance(enter:query balance).");
                    System.out.println("query a transaction(enter:query transaction).");
                    System.out.println("create random transactions(enter:random transaction).");

                    String str = scan.nextLine();
                    System.out.println(str + ":");
                    if (str.isEmpty()) {
                        continue;
                    }
                    if (str.equals("create a transaction")) {
                        System.out.println("please enter the index of the account you want to transfer and the number of the money.");
                        int toaccount_index = scan.nextInt();
                        int amount = scan.nextInt();
                        Account toaccount = network.getAccounts().get(toaccount_index);
                        Transaction transaction = getOneTransaction(toaccount, amount);
                        if (transaction == null) continue;
                        System.out.println("create a transaction, the txHash is " + SecurityUtil.sha256Digest(transaction.toString()));
                        transactionPool.put(transaction);
                        if (transactionPool.isFull()) {
                            transactionPool.notify();
                        }
                    } else if (str.equals("query balance")) {
                        int amount = getbalance();
                        System.out.println("The balance of your account is " + amount);
                    } else if (str.equals("query transaction")) {
                        System.out.println("please enter a txHash of transaction.");
                        String txHash = scan.nextLine();
                        if (simplifiedPaymentVerify(txHash) == 0) {
                            System.out.println("transaction exist.");
                        } else {
                            System.out.println("transaction doesn't exist.");
                        }
                    } else if (str.equals("random transaction")) {
                        while (!transactionPool.isFull()) {
                            Transaction transaction = getRandomTransaction();
                            System.out.println("create random transaction, the txHash is " + SecurityUtil.sha256Digest(transaction.toString()));
                            transactionPool.put(transaction);
                            if (transactionPool.isFull()) {
                                transactionPool.notify();
                                break;
                            }
                        }
                    }
                    System.out.println();
                }
            }
        }
    }

    public void accept(BlockHeader blockHeader) {
        headers.add(blockHeader);
        verifyLatest();
    }

    public int simplifiedPaymentVerify(String txHash) {
//        String txHash = SecurityUtil.sha256Digest(transaction.toString());

        MinerPeer minerPeer = network.getMinerPeer();
        Proof proof = minerPeer.getProof(txHash);

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 36!.\n");

        if (proof == null) {
            return 1;
        }

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 42!.\n");

        String hash = proof.getTxHash();
        for (Proof.Node node : proof.getPath()) {
            switch (node.getOrientation()) {
                case LEFT: hash = SecurityUtil.sha256Digest(node.getTxHash() + hash); break;
                case RIGHT: hash = SecurityUtil.sha256Digest(hash + node.getTxHash()); break;
                default: return 2;
            }
        }

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 53!.\n");

        int height = proof.getHeight();
        String localMerkleRootHash = headers.get(height).getMerkleRootHash();
        String remoteMerkleRootHash = proof.getMerkleRootHash();

        System.out.println("\n----------> verify hash:\t" + txHash);
        System.out.println("calMerkleRootHash:\t\t" + hash);
        System.out.println("localMerkleRootHash:\t" + localMerkleRootHash);
        System.out.println("remoteMerkleRootHash:\t" + remoteMerkleRootHash);
        System.out.println();

        return hash.equals(localMerkleRootHash) && hash.equals(remoteMerkleRootHash) ? 0:3;
    }

    public void verifyLatest() {
        List<Transaction> transactions = network.getTransactionsInLatestBlock(account.getWalletAddress());
        if (transactions.isEmpty()) {
            return;
        }

        System.out.println("Account[" + account.getWalletAddress() + "] began to verify the transaction...");
        for (Transaction transaction : transactions) {
            int err;
            String txHash = SecurityUtil.sha256Digest(transaction.toString());
            if ((err = simplifiedPaymentVerify(txHash)) != 0) {
                System.out.println("verification failed! Error code = " + err);
                System.exit(-1);
            }
        }
        System.out.println("Account[" + account.getWalletAddress() + "] verifies all transactions are successfull!\n");

    }

    private Transaction getOneTransaction(Account toaccount, int amount) {
        Transaction transaction = null;
        List<Account> accounts = network.getAccounts();
        Account aAccount = account;
        Account bAccount = toaccount;

        if (aAccount == bAccount) {
            System.out.println("you can't transfer to  yourself.");
            return transaction;
        }
        String aWalletAddress = aAccount.getWalletAddress();
        String bWalletAddress = bAccount.getWalletAddress();
        UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(aWalletAddress);
        int aAmount = getbalance();
        int txAmount = amount;
        if (aAmount < amount) {
            System.out.println("your balance can't afford this transaction. your balance is " + aAmount);
            return transaction;
        }
        List<UTXO> inUtxoList = new ArrayList<>();
        List<UTXO> outUtxoList = new ArrayList<>();

        byte[] aUnlockSign = SecurityUtil.signature(aAccount.getPublicKey().getEncoded(), aAccount.getPrivateKey());
        int inAmount = 0;
        for (UTXO utxo : aTrueUtxos) {
            if (utxo.unlockScript(aUnlockSign, aAccount.getPublicKey())) {
                inAmount += utxo.getAmount();
                inUtxoList.add(utxo);
                if (inAmount >= txAmount) break;
            }
        }
        if (inAmount < txAmount) {
            System.out.println("the unlock utxos is not enough.");
            return transaction;
        }

        outUtxoList.add(new UTXO(bWalletAddress, txAmount, bAccount.getPublicKey()));
        if (inAmount > txAmount) {
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

    private int getbalance() {
        String walletAddress = account.getWalletAddress();
        UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(walletAddress);
        int amount = account.getAmount(aTrueUtxos);
        return amount;
    }

    private Transaction getRandomTransaction() {

        Random random = new Random();   // random.nextInt(bound) 在[0, bound) 中取值
        Transaction transaction = null; // 返回的交易
        List<Account> accounts = network.getAccounts();  // 从网络中获取账户数组

        while (true) {
            // 随机获取两个账户A和B
            Account aAccount = accounts.get(random.nextInt(accounts.size()));
            Account bAccount = accounts.get(random.nextInt(accounts.size()));
            // BTC不允许自己给自己转账
            if (aAccount == bAccount) {
                continue;
            }

            // 获得钱包地址
            String aWalletAddress = aAccount.getWalletAddress();
            String bWalletAddress = bAccount.getWalletAddress();

            // 获取A可用的Utxo并计算余额
            UTXO[] aTrueUtxos = network.getBlockChain().getTrueUtxos(aWalletAddress);
            int aAmount = aAccount.getAmount(aTrueUtxos);
            // 如果A账户的余额为0，则无法构建交易，重新随机生成
            if (aAmount == 0) {
                continue;
            }

            // 随机生成交易数额 [1, aAmount] 之间
            int txAmount = random.nextInt(aAmount) + 1;
            // 构建InUtxo和OutUtxo
            List<UTXO> inUtxoList = new ArrayList<>();
            List<UTXO> outUtxoList = new ArrayList<>();

            // A账户需先解锁才能使用自己的utxo，解锁需要私钥签名和公钥去执行解锁脚本，这里先生成需要解锁的签名
            // 签名的数据我们约定为公钥的二进制数据
            byte[] aUnlockSign = SecurityUtil.signature(aAccount.getPublicKey().getEncoded(), aAccount.getPrivateKey());

            // 选择输入总额>=交易数额的 utxo
            int inAmount = 0;
            for (UTXO utxo : aTrueUtxos) {
                // 解锁成功才能使用该utxo
                if (utxo.unlockScript(aUnlockSign, aAccount.getPublicKey())) {
                    inAmount += utxo.getAmount();
                    inUtxoList.add(utxo);
                    if (inAmount >= txAmount) {
                        break;
                    }
                }
            }
            // 可解锁的utxo总额仍不足以支付交易数额，则重新随机
            if (inAmount < txAmount) {
                continue;
            }

            // 构建输出OutUtxos，A账户向B账户支付txAmount，同时输入对方的公钥以供生成公钥哈希
            outUtxoList.add(new UTXO(bWalletAddress, txAmount, bAccount.getPublicKey()));
            // 如果有余额，则“找零”，即给自己的utxo
            if (inAmount > txAmount) {
                outUtxoList.add(new UTXO(aWalletAddress, inAmount - txAmount, aAccount.getPublicKey()));
            }

            // 导出固定utxo数组
            UTXO[] inUtxos = inUtxoList.toArray(new UTXO[0]);
            UTXO[] outUtxos = outUtxoList.toArray(new UTXO[0]);

            // A账户需对整个交易进行私钥签名，确保交易不会被篡改，因为交易会传输到网络中，而上述步骤可在本地离线环境中构造
            // 获取要签名的数据，这个数据需要囊括交易信息
            byte[] data = SecurityUtil.utxos2Bytes(inUtxos, outUtxos);
            // A账户使用私钥签名
            byte[] sign = SecurityUtil.signature(data, aAccount.getPrivateKey());
            // 交易时间戳
            long timestamp = System.currentTimeMillis();
            // 构造交易
            transaction = new Transaction(inUtxos, outUtxos, sign, aAccount.getPublicKey(), timestamp);
            // 成功构造一笔交易，推出循环
            break;
        }
        // 返回随机生成的交易
        return transaction;
    }
}



