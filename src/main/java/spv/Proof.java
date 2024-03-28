package spv;


import java.util.List;

public class Proof {

    public enum Orientation {
        LEFT, RIGHT;
    }

    public static class Node {
        private final String txHash;
        private final Orientation orientation;

        public Node(String txHash, Orientation orientation) {
            this.txHash = txHash;
            this.orientation = orientation;
        }

        public String getTxHash() { return txHash; }

        public Orientation getOrientation() {return orientation;}
    }

    private final String txHash;
    private final String merkleRootHash;
    private final int height;
    private final List<Node> path;

    public Proof(String txHash, String merkelRootHash, int height, List<Node> path) {
        this.txHash = txHash;
        this.merkleRootHash = merkelRootHash;
        this.height = height;
        this.path = path;
    }

    public List<Node> getPath() { return path;}

    public String getTxHash() {return txHash;}

    public String getMerkleRootHash() {return merkleRootHash;}

    public int getHeight() {return height;}

}
