package org.example.ring;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;
import org.example.common.Node;

/** Consistent Hash Ring implementation with virtual nodes */
public class ConsistentHashRing {
  private static final Logger LOGGER = Logger.getLogger(ConsistentHashRing.class.getName());

  private final TreeMap<Long, Node> ring;
  private final Map<String, List<Long>> nodeHashes;
  private final int virtualNodes;
  private final MessageDigest md;

  public ConsistentHashRing(int virtualNodes) {
    this.ring = new TreeMap<>();
    this.nodeHashes = new HashMap<>();
    this.virtualNodes = virtualNodes;

    try {
      this.md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 algorithm not found", e);
    }
  }

  /** Add a node to the ring */
  public synchronized void addNode(Node node) {
    if (nodeHashes.containsKey(node.getId())) {
      LOGGER.warning("Node " + node.getId() + " already exists in the ring");
      return;
    }

    List<Long> hashes = new ArrayList<>();
    for (int i = 0; i < virtualNodes; i++) {
      String virtualNodeKey = node.getId() + "#" + i;
      long hash = hash(virtualNodeKey);
      ring.put(hash, node);
      hashes.add(hash);
    }

    nodeHashes.put(node.getId(), hashes);
    LOGGER.info(
        "✓ Added node "
            + node.getId()
            + " to the ring with "
            + virtualNodes
            + " virtual nodes. Total nodes: "
            + nodeHashes.size());
  }

  /** Remove a node from the ring */
  public synchronized void removeNode(String nodeId) {
    List<Long> hashes = nodeHashes.get(nodeId);
    if (hashes == null) {
      LOGGER.warning("Node " + nodeId + " not found in the ring");
      return;
    }

    for (Long hash : hashes) {
      ring.remove(hash);
    }

    nodeHashes.remove(nodeId);
    LOGGER.info("✗ Removed node " + nodeId + " from the ring. Total nodes: " + nodeHashes.size());
  }

  /** Get the node responsible for the given key */
  public synchronized Node getNode(String key) {
    if (ring.isEmpty()) {
      return null;
    }

    long hash = hash(key);
    Map.Entry<Long, Node> entry = ring.ceilingEntry(hash);

    if (entry == null) {
      entry = ring.firstEntry();
    }

    return entry.getValue();
  }

  /** Hash function using MD5 */
  private long hash(String key) {
    md.reset();
    md.update(key.getBytes());
    byte[] digest = md.digest();

    long hash =
        ((long) (digest[3] & 0xFF) << 24)
            | ((long) (digest[2] & 0xFF) << 16)
            | ((long) (digest[1] & 0xFF) << 8)
            | ((long) (digest[0] & 0xFF));

    return hash & 0xFFFFFFFFL;
  }

  /** Get all nodes in the ring */
  public synchronized Set<Node> getAllNodes() {
    return new HashSet<>(ring.values());
  }

  /** Get the number of physical nodes */
  public synchronized int getNodeCount() {
    return nodeHashes.size();
  }

  /** Get ring statistics for debugging */
  public synchronized String getStats() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== Consistent Hash Ring Stats ===\n");
    sb.append("Physical Nodes: ").append(nodeHashes.size()).append("\n");
    sb.append("Virtual Nodes per Physical Node: ").append(virtualNodes).append("\n");
    sb.append("Total Positions in Ring: ").append(ring.size()).append("\n");
    sb.append("Active Nodes:\n");

    for (String nodeId : nodeHashes.keySet()) {
      Node node =
          ring.values().stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null);
      if (node != null) {
        sb.append("  - ").append(node).append("\n");
      }
    }
    sb.append("==================================\n");

    return sb.toString();
  }
}
