package spv;

import consensus.MinerPeer;
import data.BlockHeader;
import data.Account;
import data.Transaction;
import network.Network;
import utils.SecurityUtil;

import java.util.ArrayList;
import java.util.List;

public class SpvPeer {
    private final List<BlockHeader> headers = new ArrayList<>();

    private final Account account;

    private final Network network;

    public SpvPeer(Account account, Network network) {
        this.account = account;
        this.network = network;
    }

    public void accept(BlockHeader blockHeader) {
        headers.add(blockHeader);
        verifyLatest();
    }

    public boolean simplifiedPaymentVerify(Transaction transaction) {
        String txHash = SecurityUtil.sha256Digest(transaction.toString());

        MinerPeer minerPeer = network.getMinerPeer();
        Proof proof = minerPeer.getProof(txHash);

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 36.\n");

        if (proof == null) {
            return false;
        }

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 42.\n");

        String hash = proof.getTxHash();
        for (Proof.Node node : proof.getPath()) {
            switch (node.getOrientation()) {
                case LEFT: hash = SecurityUtil.sha256Digest(node.getTxHash() + hash); break;
                case RIGHT: hash = SecurityUtil.sha256Digest(hash + node.getTxHash()); break;
                default: return false;
            }
        }

        /* debug code */
        System.out.println("simplifiedPaymentVerify hit line 53.\n");

        int height = proof.getHeight();
        String localMerkleRootHash = headers.get(height).getMerkleRootHash();
        String remoteMerkleRootHash = proof.getMerkleRootHash();

        System.out.println("\n----------> verify hash:\t" + txHash);
        System.out.println("calMerkleRootHash:\t\t" + hash);
        System.out.println("localMerkleRootHash:\t" + localMerkleRootHash);
        System.out.println("remoteMerkleRootHash:\t" + remoteMerkleRootHash);
        System.out.println();

        return hash.equals(localMerkleRootHash) && hash.equals(remoteMerkleRootHash);
    }

    public void verifyLatest() {
        List<Transaction> transactions = network.getTransactionsInLatestBlock(account.getWalletAddress());
        if (transactions.isEmpty()) {
            return;
        }

        System.out.println("Account[" + account.getWalletAddress() + "] began to verify the transaction...");
        for (Transaction transaction : transactions) {
            if (!simplifiedPaymentVerify(transaction)) {
                System.out.println("verification failed!");
                System.exit(-1);
            }
        }
        System.out.println("Account[" + account.getWalletAddress() + "] verifies all transactions are successfull!\n");

    }

}
