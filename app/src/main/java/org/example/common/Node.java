package org.example.common;

import java.util.Objects;

/**
 * Represents a server node in the consistent hash ring
 */
public class Node {
    private final String id;
    private final String host;
    private final int port;
    private boolean active;

    public Node(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return port == node.port && Objects.equals(id, node.id) && Objects.equals(host, node.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, host, port);
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                ", address='" + getAddress() + '\'' +
                ", active=" + active +
                '}';
    }
}
