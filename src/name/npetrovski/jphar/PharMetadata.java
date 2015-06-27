package name.npetrovski.jphar;

import java.io.IOException;
import java.util.Map;

import de.ailis.pherialize.Pherialize;


public final class PharMetadata implements PharWritable {

    private final Map<String, String> metadata;

    public PharMetadata(final Map<String, String> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        this.metadata = metadata;
    }

    @Override
    public void write(PharOutputStream out) throws IOException {
        byte[] metadataBytes = new byte[0];
        if (!this.metadata.isEmpty()) {
            metadataBytes = Pherialize.serialize(this.metadata).getBytes("UTF-8");
        }
        out.writeInt(metadataBytes.length);
        out.write(metadataBytes);
    }

}
