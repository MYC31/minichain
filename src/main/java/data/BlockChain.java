package data;

import network.Network;
import utils.SecurityUtil;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

/**
 * 区块链的类抽象，创建该对象时会自动生成创世纪块，加入区块链中
 */
public class BlockChain {

    private final LinkedList<Block> blocks;
    private final Network network;


    public BlockChain(Network network) {
        this.network = network;
        blocks = new LinkedList<>();

        // 创世区块交易为空
        BlockHeader genesisBlockHeader = new BlockHeader(null, null, Math.abs(new Random().nextLong()));
        BlockBody genesisBlockBody = new BlockBody(null, new Transaction[]{});
        Block genesisBlock = new Block(genesisBlockHeader, genesisBlockBody);

        System.out.println("Create the genesis Block! ");
        System.out.println("And the hash of genesis Block is : " + SecurityUtil.sha256Digest(genesisBlock.toString()) +
                ", you will see the hash value in next Block's preBlockHash field.");
        System.out.println();
        blocks.add(genesisBlock);
    }

    /**
     * 遍历整个区块链获得某钱包地址相关的utxo，获得真正的utxo，即未被使用的utxo
     * @param walletAddress 钱包地址
     * @return
     */
    public UTXO[] getTrueUtxos(String walletAddress) {
        // 使用哈希表存储结果，保证每个utxo唯一
        Set<UTXO> trueUtxoSet = new HashSet<>();
        // 遍历每个区块
        for (Block block : blocks) {
            BlockBody blockBody = block.getBlockBody();
            Transaction[] transactions = blockBody.getTransactions();
            // 遍历区块中的所有交易
            for (Transaction transaction : transactions) {
                UTXO[] inUtxos = transaction.getInUtxos();
                UTXO[] outUtxos = transaction.getOutUtxos();
                // 交易中的inUtxo是已使用的utxo，故需要删除
                for (UTXO utxo : inUtxos) {
                    if (utxo.getWalletAddress().equals(walletAddress)) {
                        trueUtxoSet.remove(utxo);
                    }
                }
                // 交易中的outUtxo是新产生的utxo，可作为后续交易使用
                for (UTXO utxo : outUtxos) {
                    if (utxo.getWalletAddress().equals(walletAddress)) {
                        trueUtxoSet.add(utxo);
                    }
                }
            }
        }
        // 转化为数组形式返回
        UTXO[] trueUtxos = new UTXO[trueUtxoSet.size()];
        trueUtxoSet.toArray(trueUtxos);
        return trueUtxos;
    }

    /**
     * 向区块链中添加新的满足难度条件的区块
     *
     * @param block 新的满足难度条件的区块
     */
    public void addNewBlock(Block block) {
        blocks.offer(block);
    }

    /**
     * 获取区块链的最后一个区块，矿工在组装新的区块时，需要获取上一个区块的哈希值，通过该方法获得
     *
     * @return 区块链的最后一个区块
     */
    public Block getLatestBlock() {
        return blocks.peekLast();
    }


    public int getAllAccountAmount() {
        Account[] accounts = network.getAccounts();
        int sumAmount = 0;
        for (int i = 0; i < accounts.length; ++i) {
            UTXO[] trueUtxo = getTrueUtxos(accounts[i].getWalletAddress());
            sumAmount += accounts[i].getAmount(trueUtxo);
        }
        return sumAmount;
    }

    public LinkedList<Block> getBlocks() {
        return blocks;
    }
}
