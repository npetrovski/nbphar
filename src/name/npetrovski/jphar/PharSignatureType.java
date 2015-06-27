package name.npetrovski.jphar;

/**
 * @see
 * <a href="http://www.php.net/manual/en/phar.fileformat.signature.php">Phar Signature format</a>
 */
public enum PharSignatureType {

    MD5(0x0001, "MD5", 16),
    SHA1(0x0002, "SHA-1", 20),
    SHA256(0x0004, "SHA-256", 32),
    SHA512(0x0008, "SHA-512", 64);

    private final int flag;
    private final String algorithm;
    private final int numberOfBytes;

    private PharSignatureType(final int flag, final String algorithm, final int numberOfBytes) {
        this.flag = flag;
        this.algorithm = algorithm;
        this.numberOfBytes = numberOfBytes;
    }

    public int getFlag() {
        return this.flag;
    }

    public String getAlgorithm() {
        return this.algorithm;
    }

    public int getNumberOfBytes() {
        return this.numberOfBytes;
    }

    public static PharSignatureType getEnumByFlag(int code) {
        for (PharSignatureType e : PharSignatureType.values()) {
            if (code == e.getFlag()) {
                return e;
            }
        }
        return null;
    }
}
