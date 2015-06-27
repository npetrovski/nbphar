package name.npetrovski.jphar;

import java.io.IOException;
import java.util.List;


public interface PharEntryProvider {

    List<PharEntry> getPharEntries() throws IOException;

}
