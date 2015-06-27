package name.npetrovski.jphar;

import java.io.IOException;


public interface PharWritable {

    void write(PharOutputStream out) throws IOException;

}
