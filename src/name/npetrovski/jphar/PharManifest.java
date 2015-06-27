package name.npetrovski.jphar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;


public final class PharManifest implements PharWritable {

    private static final int BITMAP_SIGNATURE_FLAG = 0x00010000;

    private final File pharFile;
    
    private final byte[] pharFiles;
    
    private List<PharEntry> pharEntries;
    
    private PharMetadata pharMetadata;


    public PharManifest(
            final File pharFile,
            final byte[] pharFiles,
            final List<PharEntry> pharEntries,
            final PharMetadata pharMetadata) {

        if (pharFile == null) {
            throw new IllegalArgumentException("Phar file cannot be null");
        }
        if (pharFiles == null) {
            throw new IllegalArgumentException("Phar files bytes cannot be null");
        }
        if (pharEntries == null) {
            throw new IllegalArgumentException("Phar entries cannot be null");
        }
        if (pharMetadata == null) {
            throw new IllegalArgumentException("Phar metadata cannot be null");
        }
        this.pharFile = pharFile;
        this.pharFiles = pharFiles;
        this.pharEntries = pharEntries;
        this.pharMetadata = pharMetadata;
    }

    @Override
    public void write(final PharOutputStream out) throws IOException {
        byte[] pharAlias = this.pharFile.getName().getBytes(Phar.STRING_ENCODING);


        ByteArrayOutputStream metadataOutputStream = new ByteArrayOutputStream();
        
        PharOutputStream pharOutputStream = new PharOutputStream(metadataOutputStream);
        pharOutputStream.write(this.pharMetadata);
        pharOutputStream.flush();
        pharOutputStream.close();

        byte[] metadataBytes = metadataOutputStream.toByteArray();

        int manifestLength = metadataBytes.length + this.pharFiles.length + pharAlias.length + 14;
        out.writeInt(manifestLength);
        out.writeInt(this.pharEntries.size());

        // version
        out.write(PharVersion.getVersionNibbles(Phar.PHAR_VERSION));

        // global bitmapped flags
        out.writeInt(BITMAP_SIGNATURE_FLAG);

        // write alias
        out.writeInt(pharAlias.length);
        out.write(pharAlias);

        // write metadata
        out.write(metadataBytes);
    }


}
