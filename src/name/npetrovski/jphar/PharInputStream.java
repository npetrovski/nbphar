package name.npetrovski.jphar;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PharInputStream extends FilterInputStream {

    protected long pos = 0;

    public PharInputStream(InputStream input) throws IOException {
        super(input);
    }

    /**
     * <p>
     * Get the stream position.</p>
     *
     * <p>
     * Eventually, the position will roll over to a negative number. Reading 1 Tb per second, this would occur after
     * approximately three months. Applications should account for this possibility in their design.</p>
     *
     * @return the current stream position.
     */
    public synchronized long getPosition() {
        return pos;
    }

    @Override
    public synchronized int read()
            throws IOException {
        int b = super.read();
        if (b >= 0) {
            pos += 1;
        }
        return b;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len)
            throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            pos += n;
        }
        return n;
    }

    protected int readRInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
    }
}
