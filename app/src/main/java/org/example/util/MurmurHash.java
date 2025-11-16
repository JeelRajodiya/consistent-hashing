package org.example.util;

/**
 * MurmurHash3 implementation for consistent hashing This algorithm provides excellent distribution and is faster than
 * cryptographic hashes
 */
public class MurmurHash {
  private static final int SEED = 0x9747b28c;

  /**
   * Generates a 128-bit hash and returns the first 64 bits as a long
   *
   * @param key
   *          the key to hash
   * @return 64-bit hash value
   */
  public static long hash64(String key) {
    byte[] data = key.getBytes();
    return hash64(data, 0, data.length, SEED);
  }

  /**
   * MurmurHash3 128-bit variant, returning first 64 bits
   */
  public static long hash64(byte[] data, int offset, int length, int seed) {
    long h1 = seed & 0xFFFFFFFFL;
    long h2 = seed & 0xFFFFFFFFL;

    final long c1 = 0x87c37b91114253d5L;
    final long c2 = 0x4cf5ad432745937fL;

    int nblocks = length / 16;

    // Process 16-byte blocks
    for (int i = 0; i < nblocks; i++) {
      int index = offset + i * 16;

      long k1 = getLong(data, index);
      long k2 = getLong(data, index + 8);

      k1 *= c1;
      k1 = Long.rotateLeft(k1, 31);
      k1 *= c2;
      h1 ^= k1;

      h1 = Long.rotateLeft(h1, 27);
      h1 += h2;
      h1 = h1 * 5 + 0x52dce729;

      k2 *= c2;
      k2 = Long.rotateLeft(k2, 33);
      k2 *= c1;
      h2 ^= k2;

      h2 = Long.rotateLeft(h2, 31);
      h2 += h1;
      h2 = h2 * 5 + 0x38495ab5;
    }

    // Process remaining bytes
    long k1 = 0;
    long k2 = 0;

    int tail = offset + nblocks * 16;

    switch (length & 15) {
      case 15:
        k2 ^= ((long) data[tail + 14] & 0xff) << 48;
      case 14:
        k2 ^= ((long) data[tail + 13] & 0xff) << 40;
      case 13:
        k2 ^= ((long) data[tail + 12] & 0xff) << 32;
      case 12:
        k2 ^= ((long) data[tail + 11] & 0xff) << 24;
      case 11:
        k2 ^= ((long) data[tail + 10] & 0xff) << 16;
      case 10:
        k2 ^= ((long) data[tail + 9] & 0xff) << 8;
      case 9:
        k2 ^= ((long) data[tail + 8] & 0xff);
        k2 *= c2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= c1;
        h2 ^= k2;
      case 8:
        k1 ^= ((long) data[tail + 7] & 0xff) << 56;
      case 7:
        k1 ^= ((long) data[tail + 6] & 0xff) << 48;
      case 6:
        k1 ^= ((long) data[tail + 5] & 0xff) << 40;
      case 5:
        k1 ^= ((long) data[tail + 4] & 0xff) << 32;
      case 4:
        k1 ^= ((long) data[tail + 3] & 0xff) << 24;
      case 3:
        k1 ^= ((long) data[tail + 2] & 0xff) << 16;
      case 2:
        k1 ^= ((long) data[tail + 1] & 0xff) << 8;
      case 1:
        k1 ^= ((long) data[tail] & 0xff);
        k1 *= c1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= c2;
        h1 ^= k1;
    }

    // Finalization
    h1 ^= length;
    h2 ^= length;

    h1 += h2;
    h2 += h1;

    h1 = fmix64(h1);
    h2 = fmix64(h2);

    h1 += h2;

    return h1;
  }

  private static long getLong(byte[] data, int index) {
    return ((long) data[index] & 0xff) | (((long) data[index + 1] & 0xff) << 8)
      | (((long) data[index + 2] & 0xff) << 16) | (((long) data[index + 3] & 0xff) << 24)
      | (((long) data[index + 4] & 0xff) << 32) | (((long) data[index + 5] & 0xff) << 40)
      | (((long) data[index + 6] & 0xff) << 48) | (((long) data[index + 7] & 0xff) << 56);
  }

  private static long fmix64(long k) {
    k ^= k >>> 33;
    k *= 0xff51afd7ed558ccdL;
    k ^= k >>> 33;
    k *= 0xc4ceb9fe1a85ec53L;
    k ^= k >>> 33;
    return k;
  }
}
