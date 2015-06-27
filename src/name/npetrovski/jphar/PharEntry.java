package name.npetrovski.jphar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.zip.CRC32;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

public final class PharEntry {

    private File file;

    private final String name;

    private final PharCompression compression;

    private int checksum = 0;

    private byte[] compressedBytes;

    private byte[] decompressedBytes;

    private int decompressedSize;

    private long offset;

    private int size;
    
    private int unixModTime = (int) (System.currentTimeMillis() / 1000L);

    public PharEntry(final File file, final String localPath, final PharCompression pharCompression) throws IOException {

        this.name = localPath;
        this.compression = pharCompression;

        if (null != file) {
            this.file = file;
            this.decompressedSize = (int) file.length();

            this.decompressedBytes = Files.readAllBytes(this.file.toPath());

            CRC32 crc = new CRC32();
            crc.update(this.decompressedBytes);

            this.checksum = (int) crc.getValue();

            ByteArrayOutputStream compressed = new ByteArrayOutputStream();

            OutputStream compressor = null;
            try {
                compressor = getCompressorOutputStream(compressed);
                Files.copy(this.file.toPath(), compressor);
                compressor.flush();
            } finally {
                compressor.close();
            }

            if (this.compression == PharCompression.GZIP) {
                this.compressedBytes = Arrays.copyOfRange(compressed.toByteArray(), 10, compressed.toByteArray().length - 8);
            } else {
                this.compressedBytes = compressed.toByteArray();
            }
        }
    }

    public void unpack(byte[] compressedBytes) throws IOException {

        InputStream decompressor = null;

        try {
            switch (this.compression) {
                case GZIP:

                    compressedBytes = ArrayUtils.addAll(new byte[]{0x1f, (byte) 0x8b, 0x08, 0, 0, 0, 0, 0, 0, (byte) 0xff}, compressedBytes);
                    compressedBytes = ArrayUtils.addAll(compressedBytes, ByteBuffer.allocate(4).putInt(Integer.reverseBytes((int) this.checksum)).array());
                    compressedBytes = ArrayUtils.addAll(compressedBytes, ByteBuffer.allocate(4).putInt(Integer.reverseBytes((int) this.decompressedSize)).array());
                case BZIP2:
                // Full format is presented
            }

            this.compressedBytes = compressedBytes;

            decompressor = getCompressorInputStream(new ByteArrayInputStream(compressedBytes));
            this.decompressedBytes = IOUtils.toByteArray(decompressor);

        } finally {
            decompressor.close();
        }
    }

    private OutputStream getCompressorOutputStream(final OutputStream outputStream) throws IOException {
        switch (this.compression) {
            case GZIP:
                return new GzipCompressorOutputStream(outputStream);
            case BZIP2:
                return new BZip2CompressorOutputStream(outputStream);
            case NONE:
                return outputStream;
            default:
                throw new IllegalArgumentException("Compression is not supported: " + this.compression.name());
        }
    }

    private InputStream getCompressorInputStream(final InputStream inputStream) throws IOException {
        switch (this.compression) {
            case GZIP:
                return new GzipCompressorInputStream(inputStream, true);
            case BZIP2:
                return new BZip2CompressorInputStream(inputStream, true);
            case NONE:
                return inputStream;
            default:
                throw new IllegalArgumentException("Compression is not supported: " + this.compression.name());
        }
    }

    public PharWritable getPharEntryManifest() {
        return new PharWritable() {

            @Override
            public void write(PharOutputStream out) throws IOException {

                byte[] filenameBytes = PharEntry.this.name.replace('\\', '/').getBytes("UTF-8");

                // Filename length
                out.writeInt(filenameBytes.length);

                // write filename
                out.write(filenameBytes);

                // Un-compressed file size
                out.writeInt((int) PharEntry.this.decompressedSize);

                // Unix timestamp of file
                out.writeInt((int) (PharEntry.this.file.lastModified() / 1000));

                // Compressed file size
                out.writeInt((int) PharEntry.this.compressedBytes.length);

                // CRC32 checksum of un-compressed file contents
                out.writeInt((int) PharEntry.this.checksum);

                // Bit-mapped File-specific flags
                out.write(PharEntry.this.compression.getBitmapFlag());

                // write meta-data length
                out.writeInt(0);
            }
        };
    }

    public PharWritable getPharEntryData() {
        return new PharWritable() {

            @Override
            public void write(final PharOutputStream out) throws IOException {
                out.write(PharEntry.this.compressedBytes);
            }
        };
    }

    public boolean isDirectory() {
        return this.name.endsWith("/");
    }

    public String getName() {
        return this.name;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
    
    public byte[] getContents() {
        return this.decompressedBytes;
    }
    
    public Date getLastModifiedDate() {
        return new Date(unixModTime * 1000);
    }

    public void setModTime(int ts) {
        this.unixModTime = ts;
    }

    public int getModTime() {
        return this.unixModTime;
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }

    public void setDecompressedSize(int decompressedSize) {
        this.decompressedSize = decompressedSize;
    }
     
}
