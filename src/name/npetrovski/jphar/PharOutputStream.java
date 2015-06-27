package name.npetrovski.jphar;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class PharOutputStream extends FilterOutputStream {


    public PharOutputStream(final OutputStream outputStream) {
        super(outputStream);
    }

    public void writeInt(final int i) throws IOException {
        this.out.write((i >>> 0) & 0xFF);
        this.out.write((i >>> 8) & 0xFF);
        this.out.write((i >>> 16) & 0xFF);
        this.out.write((i >>> 24) & 0xFF);
    }

    public void writeString(final String s) throws IOException {
        if (s == null) {
            throw new NullPointerException("String cannot be null");
        }
        this.out.write(s.getBytes(Phar.STRING_ENCODING));
    }

    public void write(final PharWritable writable) throws IOException {
        if (writable == null) {
            throw new NullPointerException("Writable cannot be null");
        }
        writable.write(this);
    }

}
