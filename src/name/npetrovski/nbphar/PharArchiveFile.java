package name.npetrovski.nbphar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import name.npetrovski.jphar.Phar;
import name.npetrovski.jphar.PharCompression;
import name.npetrovski.jphar.PharEntry;

public class PharArchiveFile {

    private static String STUB_NAME = "__STUB__";

    private SourceHandler _source;

    public PharArchiveFile(File file) throws IOException {
        _source = new PharSourceHandler(file, file.getName());
    }

    public PharArchiveFile(String path) throws IOException {
        this(new File(path));
    }

    public ArchiveEntry getArchiveEntry(String name) throws IOException {
        if (name == null) {
            throw new IllegalArgumentException("Illegal name");
        }

        if (_source._entries.isEmpty()) {
            enumerateEntries();
        }

        ArchiveEntry entry = _source._entries.get(name);
        if (entry == null) {
            entry = _source._entries.get(name + "/");
        }

        return entry;
    }

    public boolean isArchive() {
        return _source._isArchive;
    }

    public String getName() {
        return _source._name;
    }

    public Enumeration<ArchiveEntry> entries() throws IOException {
        enumerateEntries();
        return Collections.enumeration(_source._entries.values());
    }

    private void enumerateEntries() throws IOException {
        _source.enumerateEntries();
    }

    public InputStream getInputStream(final ArchiveEntry entry) throws IOException {
        return _source.getInputStream(entry);
    }

    abstract class ArchiveEntry {

        final String _name;

        public ArchiveEntry(String name) {
            _name = name;
        }

        abstract public long getSize();

        abstract public String getName();

        abstract public long getTime();

        // equals based on unique name
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ArchiveEntry) {
                return (_name.equals(((ArchiveEntry) obj)._name));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + this._name.hashCode();
            return hash;
        }
    }

    class SinglePharEntry extends ArchiveEntry {

        private final PharEntry _entry;

        public SinglePharEntry(PharEntry entry) {
            super(entry.getName());
            _entry = entry;
        }

        public SinglePharEntry(String name) {
            super(name);
            _entry = null;
        }

        @Override
        public long getSize() {
            return _entry.getSize();
        }

        @Override
        public String getName() {
            return _entry.getName();
        }

        @Override
        public long getTime() {
            return _entry.getLastModifiedDate().getTime();
        }

    }

    // keeps source info on archive
    private abstract class SourceHandler {

        final String _name;

        final File _file;

        boolean _hasEnumerated;

        @SuppressWarnings("unchecked")
        Map<String, ArchiveEntry> _entries = Collections.EMPTY_MAP;

        boolean _isArchive;

        SourceHandler(File file, String name) throws IOException {
            _name = name;
            _file = file;
        }

        abstract void enumerateEntries() throws IOException;

        abstract InputStream getInputStream(ArchiveEntry entry) throws IOException;

    }

    private final class PharSourceHandler extends SourceHandler {

        PharSourceHandler(File file, String name) throws IOException {
            super(file, name);
            _isArchive = true;
        }

        @Override
        void enumerateEntries() throws IOException {
            if (_hasEnumerated) {
                return;
            }
            _hasEnumerated = true;
            if (!_isArchive) {
                return;
            }

            _entries = new HashMap();

            Phar pa = new Phar(_file);

            _entries.put(STUB_NAME, new SinglePharEntry(new PharEntry(null, STUB_NAME, PharCompression.NONE)));
            for (Object row : pa.getEntries()) {
                PharEntry entry = (PharEntry) row;
                _entries.put(entry.getName(), new SinglePharEntry(entry));
            }
        }

        @Override
        InputStream getInputStream(ArchiveEntry entry) throws IOException {
            if (entry == null || _file == null) {
                return null;
            }

            Phar pa = new Phar(_file);

            if (STUB_NAME.equals(entry._name)) {
                return new ByteArrayInputStream(pa.stub.getStubCode());
            }

            PharEntry target = pa.getEntry(entry._name);
            if (target != null) {
                return new ByteArrayInputStream(target.getContents());
            }

            return null;
        }

    }

}
