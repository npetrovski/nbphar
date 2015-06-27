package name.npetrovski.jphar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @see <a href="http://www.php.net/manual/en/phar.fileformat.signature.php">Phar Signature format</a>
 */
public final class PharSignature implements PharWritable {

    static final String MAGIC_GBMB = "GBMB";
    
    private final File pharFile;
    private final PharSignatureType signatureType;

    public PharSignature(final File pharFile, final PharSignatureType pharSignatureType) {
        if (pharFile == null) {
            throw new IllegalArgumentException("Phar file cannot be null");
        }
        if (pharSignatureType == null) {
            throw new IllegalArgumentException("Phar signature type cannot be null");
        }
        this.pharFile = pharFile;
        this.signatureType = pharSignatureType;
    }

    @Override
    public void write(final PharOutputStream out) throws IOException {
        MessageDigest signature = null;
        try {
            signature = MessageDigest.getInstance(this.signatureType.getAlgorithm());

            // create signature
            signature.update(Files.readAllBytes(this.pharFile.toPath()));

            // write signature
            out.write(signature.digest());

            // write signature flag
            out.writeInt(this.signatureType.getFlag());

            // write magic GBMB
            out.write(MAGIC_GBMB.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not write signature to phar", e);
        }
    }

}
