package org.example.ring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.common.Node;
import org.example.util.MurmurHash;

/** Consistent Hash Ring implementation with virtual nodes */
public class ConsistentHashRing {
  private static final Logger LOGGER = Logger.getLogger(ConsistentHashRing.class.getName());

  private final TreeMap<Long, Node> ring;
  private final Map<String, List<Long>> nodeHashes;
  private final int virtualNodes;

  public ConsistentHashRing(int virtualNodes) {
    this.ring = new TreeMap<>();
    this.nodeHashes = new HashMap<>();
    this.virtualNodes = virtualNodes;
  }

  /** Add a node to the ring */
  public synchronized void addNode(Node node) {
    if (nodeHashes.containsKey(node.getId())) {
      LOGGER.log(Level.WARNING, "Node {0} already exists in the ring", node.getId());
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
    LOGGER.log(Level.INFO, "Added node {0} with {1} virtual nodes (total: {2} nodes)",
      new Object[] { node.getId(), virtualNodes, nodeHashes.size() });
  }

  /** Remove a node from the ring */
  public synchronized void removeNode(String nodeId) {
    List<Long> hashes = nodeHashes.get(nodeId);
    if (hashes == null) {
      LOGGER.log(Level.WARNING, "Node {0} not found in the ring", nodeId);
      return;
    }

    for (Long hash : hashes) {
      ring.remove(hash);
    }

    nodeHashes.remove(nodeId);
    LOGGER.log(Level.INFO, "Removed node {0} (total: {1} nodes)", new Object[] { nodeId, nodeHashes.size() });
  }

  /** Get the node responsible for the given key, skipping inactive nodes */
  public synchronized Node getNode(String key) {
    if (ring.isEmpty()) {
      return null;
    }

    long hash = hash(key);
    Map.Entry<Long, Node> entry = ring.ceilingEntry(hash);

    if (entry == null) {
      entry = ring.firstEntry();
    }

    // If the first node is inactive, find the next active node
    Node node = entry.getValue();
    if (!node.isActive()) {
      // Try to find an active node by iterating forward
      for (Map.Entry<Long, Node> e : ring.tailMap(entry.getKey()).entrySet()) {
        if (e.getValue().isActive()) {
          return e.getValue();
        }
      }
      // If no active node found forward, wrap around and search from the beginning
      for (Map.Entry<Long, Node> e : ring.headMap(entry.getKey()).entrySet()) {
        if (e.getValue().isActive()) {
          return e.getValue();
        }
      }
      // If still no active node found, return null
      return null;
    }

    return node;
  }

  /**
   * Hash function using MurmurHash3 for excellent distribution MurmurHash3 is specifically designed for hash tables and
   * provides superior uniformity compared to cryptographic hashes
   */
  private long hash(String key) {
    return MurmurHash.hash64(key);
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
      Node node = ring.values().stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null);
      if (node != null) {
        sb.append("  - ").append(node).append("\n");
      }
    }
    sb.append("==================================\n");

    return sb.toString();
  }
}
