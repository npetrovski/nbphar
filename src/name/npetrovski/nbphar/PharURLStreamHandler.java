package name.npetrovski.nbphar;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;


public class PharURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void parseURL(URL u, String spec, int start, int limit) {
        super.parseURL(u, spec, start, limit);
    }

    @Override
    protected void setURL(URL u,
                      String protocol,
                      String host,
                      int port,
                      String authority,
                      String userInfo,
                      String path,
                      String query,
                      String ref) {
        super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    }

    @Override
    protected void setURL(URL u,
                      String protocol,
                      String host,
                      int port,
                      String file,
                      String ref) {
        super.setURL(u, protocol, host, port, file, ref);
    }

}
