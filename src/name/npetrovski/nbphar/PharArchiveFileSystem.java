package name.npetrovski.nbphar;

import java.beans.PropertyVetoException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import org.openide.filesystems.AbstractFileSystem;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.BaseUtilities;

public class PharArchiveFileSystem extends AbstractFileSystem {

    /**
     * generated Serialized Version UID
     */
    static final long serialVersionUID = -1L;

    private static final Logger LOGGER = Logger.getLogger(PharArchiveFileSystem.class.getName());

    /**
     * One request proccesor shared for all instances of PharArchiveFileSystem
     */
    private static final RequestProcessor req = new RequestProcessor("PharFs - Modification watcher", 1, false, false);

    /**
     * maxsize for passing ByteArrayInputStream
     */
    private static final long MEM_STREAM_SIZE = 100000;

    private transient Object closeSync = new Object();
    private int checkTime = 10000;

    private transient PharArchiveFile af;

    /**
     * Time of request for opening of pharArchiveFile.
     */
    private transient long openRequestTime = 0;

    /**
     * Cached image of PharArchiveFile capable of answering queries on type and children. There is a strong reference
     * held while there is a living FileObject and a SoftReference for caching after all FOs are freed.
     */
    private transient Cache strongCache;

    private transient long lastModification = 0;
    /**
     * Actual time for which closing of phar is postponed.
     */
    private transient int closeDelay = 300;

    /**
     * The soft part of the cache reference. For simplicity never null
     */
    private transient Reference<Cache> softCache = new SoftReference<Cache>(null);
    private transient FileObject foRoot;
    private transient FileChangeListener fcl;

    /**
     * Watches modification on root file
     */
    private transient RequestProcessor.Task watcherTask = null;
    private transient RequestProcessor.Task closeTask = null;

    /**
     * Archive file.1
     */
    private File root = new File("."); // NOI18N

    /**
     * number of FileObjects in using. If no one is used then the cached data is freed
     */
    private transient long aliveCount = 0;

    /**
     * Opened zip file of this file-system is stored here or null.
     */
    private transient PharArchiveFile pharArchiveFile;

    /**
     * Default constructor.
     * <p>
     * <strong>Most module code should never create an instance of this class directly.</strong>
     * Use {@link FileUtil#getArchiveRoot(FileObject)} instead.</p>
     */
    public PharArchiveFileSystem() {
        Impl impl = new Impl(this);
        this.list = impl;
        this.info = impl;
        this.change = impl;
        this.attr = impl;
    }

    PharArchiveFileSystem(File aRoot) throws IOException {
        this();
        try {
            PharArchiveFileSystem.this.setArchiveFile(aRoot, true, false);
        } catch (PropertyVetoException ex) {
            // cannot happen, setSystemName can throw the exception only
            // if the filesystem is already in Repository, which this one
            // is not.
            throw new IOException(ex);
        }
    }

    public void setArchiveFile(final File aRoot)
            throws IOException, PropertyVetoException {
        PharArchiveFileSystem.this.setArchiveFile(aRoot, true, true);
    }

    @SuppressWarnings("deprecation") // need to set it for compat
    private void _setSystemName(String s) throws PropertyVetoException {
        setSystemName(s);
    }

    private void setArchiveFile(final File aRoot, boolean refreshRoot, boolean openArchive)
            throws IOException, PropertyVetoException {
        if (!aRoot.equals(FileUtil.normalizeFile(aRoot))) {
            throw new IllegalArgumentException(
                    "Parameter aRoot was not "
                    + "normalized. Was " + aRoot + " instead of " + FileUtil.normalizeFile(aRoot)
            );
        }

        FileObject newRoot = null;
        String oldDisplayName = getDisplayName();

        if (getRefreshTime() > 0) {
            setRefreshTime(0);
        }

        if (aRoot == null) {
            throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_NotValidFile", aRoot));
        }

        if (!aRoot.exists()) {
            throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_FileNotExists", aRoot.getAbsolutePath()));
        }

        if (!aRoot.canRead()) {
            throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_CanntRead", aRoot.getAbsolutePath()));
        }

        if (!aRoot.isFile()) {
            throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_NotValidFile", aRoot.getAbsolutePath()));
        }

        String s;
        s = aRoot.getAbsolutePath();
        s = s.intern();

        PharArchiveFile tempArchive = null;

        if (openArchive) {
            try {
                tempArchive = new PharArchiveFile(s);
                LOGGER.log(Level.FINE, "opened: " + System.currentTimeMillis() + "   " + s);
            } catch (ZipException e) {
                throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_NotValidJarFile2", e.getLocalizedMessage(), s));
            }
        }

        synchronized (closeSync) {
            _setSystemName(s);

            closeCurrentRoot(false);
            setArchive(tempArchive);
            openRequestTime = System.currentTimeMillis();
            root = new File(s);

            if (refreshRoot) {
                strongCache = null;
                softCache.clear();
                aliveCount = 0;
                newRoot = refreshRoot();
                lastModification = 0;

                if (newRoot != null) {
                    firePropertyChange("root", null, newRoot);
                }
            }
        }

        firePropertyChange(PROP_DISPLAY_NAME, oldDisplayName, getDisplayName());

        foRoot = FileUtil.toFileObject(root);

        if ((foRoot != null) && (fcl == null)) {
            fcl = new FileChangeAdapter() {

                @Override
                public void fileChanged(FileEvent fe) {
                    if (watcherTask == null) {
                        parse(true);
                    }
                }

                @Override
                public void fileRenamed(FileRenameEvent fe) {
                    File f = FileUtil.toFile(fe.getFile());

                    if ((f != null) && !f.equals(aRoot)) {
                        try {
                            PharArchiveFileSystem.this.setArchiveFile(f, false, true);
                        } catch (IOException iex) {
                            LOGGER.log(Level.INFO, null, iex);
                        } catch (PropertyVetoException pvex) {
                            LOGGER.log(Level.OFF, null, pvex);
                        }
                    }
                }

                @Override
                public void fileDeleted(FileEvent fe) {
                    Enumeration<? extends FileObject> en = existingFileObjects(getRoot());

                    while (en.hasMoreElements()) {
                        // probably do not need delete event support
                        FileObject fo = (FileObject) en.nextElement();
                        // fo.validFlag = false;
                        //fo.fileDeleted0(new FileEvent(fo));
                    }

                    refreshRoot();
                }
            };

            if (refreshRoot) {
                foRoot.addFileChangeListener(FileUtil.weakFileChangeListener(fcl, foRoot));
            }
        }
    }

    /**
     * Get the file path for the ZIP or JAR file.
     *
     * @return the file path
     */
    public File getArchiveFile() {
        return root;
    }

    /*
     * Provides name of the system that can be presented to the user.
     * @return user presentable name of the filesystem
     */
    @Override
    public String getDisplayName() {
        return root != null ? root.getAbsolutePath() : NbBundle.getMessage(System.class, "JAR_UnknownJar");
    }

    protected InputStream inputStream(String name) throws java.io.FileNotFoundException {
        InputStream is = null;

        try {
            synchronized (closeSync) {
                PharArchiveFile j = reOpenArchiveFile();

                if (j != null) {
                    PharArchiveFile.ArchiveEntry je = j.getArchiveEntry(name);

                    if (je != null) {
                        if (je.getSize() < MEM_STREAM_SIZE) {
                            is = getMemInputStream(j, je);
                        } else {
                            is = getTemporaryInputStream(j, je, (strongCache != null));
                        }
                    }
                }
            }
        } catch (java.io.FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            FileNotFoundException fnfe = new FileNotFoundException(root.getAbsolutePath());
            fnfe.initCause(e);
            throw fnfe;
        } catch (RuntimeException e) {
            FileNotFoundException fnfe = new FileNotFoundException(root.getAbsolutePath());
            fnfe.initCause(e);
            throw fnfe;
        } finally {
            closeCurrentRoot(false);
        }

        if (is == null) {
            throw new java.io.FileNotFoundException(name);
        }

        return is;
    }

    /* Creates Reference. In FileSystem, which subclasses AbstractFileSystem, you can overload method
     * createReference(FileObject fo) to achieve another type of Reference (weak, strong etc.)
     * @param fo is FileObject. It`s reference yourequire to get.
     * @return Reference to FileObject
     */
    @Override
    protected <T extends FileObject> Reference<T> createReference(T fo) {
        aliveCount++;

        if ((checkTime > 0) && (watcherTask == null)) {
            watcherTask = req.post(watcherTask(), checkTime);
        }

        return new Ref<T>(fo);
    }

    private InputStream getMemInputStream(PharArchiveFile jf, PharArchiveFile.ArchiveEntry je)
            throws IOException {
        InputStream is = jf.getInputStream(je);
        ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());

        try {
            FileUtil.copy(is, os);
        } finally {
            os.close();
        }

        return new ByteArrayInputStream(os.toByteArray());
    }

    private void freeReference() {
        aliveCount--;

        // Nobody uses this JarFileSystem => stop watcher, close JarFile and throw away cache.
        if (aliveCount == 0) {
            if (watcherTask != null) {
                watcherTask.cancel();
                watcherTask = null;
            }

            strongCache = null; // no more active FO, keep only soft ref
            closeCurrentRoot(false);
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    /**
     * Use soft-references to not throw away the data that quickly. JarFS if often queried for its FOs e.g. by java
     * parser, which leaves the references immediately.
     */
    private class Ref<T extends FileObject> extends WeakReference<T> implements Runnable {

        public Ref(T fo) {
            super(fo, BaseUtilities.activeReferenceQueue());
        }

        // do the cleanup
        @Override
        public void run() {
            freeReference();
        }
    }

    /**
     * Initializes the root of FS.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        closeSync = new Object();
        strongCache = null;
        softCache = new SoftReference<Cache>(null);
        aliveCount = 0;

        try {
            setArchiveFile(root);
        } catch (PropertyVetoException ex) {
            throw new IOException(ex.getMessage());
        } catch (IOException iex) {
            LOGGER.log(Level.INFO, iex.getLocalizedMessage());
        }
    }

    /**
     * Must be called from synchronized block
     */
    private PharArchiveFile reOpenArchiveFile() throws IOException {
        synchronized (closeSync) {
            if (closeTask != null) {
                closeTask.cancel();
            }

            // #167527 - calculate adaptive delay before closing pharArchiveFile
            long now = System.currentTimeMillis();
            long requestPeriod = now - openRequestTime;
            openRequestTime = now;
            // 150% of time from last open request, but between CLOSE_DELAY_MIN and CLOSE_DELAY_MAX
            closeDelay = (int) Math.min(500, Math.max(300, (1.5 * requestPeriod)));

            return getArchive(true);
        }
    }

    /**
     * Performs a clean-up After close of JarFile must be always reference to JarFile set to null
     */
    private void closeCurrentRoot(boolean isRealClose) {
        synchronized (closeSync) {
            if (closeTask != null) {
                closeTask.cancel();
            }

            if (isRealClose) {
                realClose().run();
            } else {
                closeTask = req.post(realClose(), closeDelay);
            }
        }
    }

    private Runnable realClose() {
        return new Runnable() {
            public void run() {
                synchronized (closeSync) {
                    final PharArchiveFile file = getArchive(false);
                    if (file != null) {
                        try {
                            LOGGER.log(Level.FINE, "closed: " + System.currentTimeMillis() + "   " + root.getAbsolutePath());//NOI18N
                        } catch (Exception exc) {
                            // ignore exception during closing, just log it
                            LOGGER.log(Level.OFF, null, exc);
                        } finally {
                            setArchive(null);
                            closeTask = null;
                        }
                    }
                }
            }
        };
    }

    /**
     * Getter for entry.
     */
    private final PharArchiveFile.ArchiveEntry getEntry(String file) {
        PharArchiveFile j = null;

        try {
            synchronized (closeSync) {
                j = reOpenArchiveFile();

                PharArchiveFile.ArchiveEntry je = null;
                if (j != null) {
                    je = j.getArchiveEntry(file);
                }

                if (je != null) {
                    return je;
                }
            }
        } catch (IOException iox) {
        }

        return null;
    }

    /**
     * @return PharArchiveFile
     */
    private PharArchiveFile getArchive(boolean create) {
        assert Thread.holdsLock(closeSync);
        if (pharArchiveFile == null && create) {
            try {
                if (root.canRead()) {
                    pharArchiveFile = new PharArchiveFile(root);
                    LOGGER.log(Level.FINE, "opened: {0} {1}", new Object[]{root.getAbsolutePath(), System.currentTimeMillis()}); //NOI18N
                    return pharArchiveFile;
                }
            } catch (ZipException ex) {
                LOGGER.log(Level.INFO, ex.getMessage(), ex);
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, ex.getMessage(), ex);
            }
            LOGGER.log(Level.WARNING, "cannot open {0}", root.getAbsolutePath());
        }
        return pharArchiveFile;
    }

    private InputStream getTemporaryInputStream(PharArchiveFile jf, PharArchiveFile.ArchiveEntry je, boolean forceRecreate)
            throws IOException {
        String filePath = jf.getName();
        String entryPath = je.getName();
        StringBuilder aarCacheFolder = new StringBuilder("pharfscache"); //NOI18N
        aarCacheFolder.append(System.getProperty("user.name")).append("/"); //NOI18N

        File pharfscache = new File(System.getProperty("java.io.tmpdir"), aarCacheFolder.toString()); //NOI18N

        if (!pharfscache.exists()) {
            pharfscache.mkdirs();
        }

        File f = new File(pharfscache, temporaryName(filePath, entryPath));

        boolean createContent = !f.exists();

        if (createContent) {
            f.createNewFile();
        } else {
            forceRecreate |= (Math.abs((System.currentTimeMillis() - f.lastModified())) > 10000);
        }

        if (createContent || forceRecreate) {
            InputStream is = jf.getInputStream(je);

            try {
                OutputStream os = new FileOutputStream(f);

                try {
                    FileUtil.copy(is, os);
                } finally {
                    os.close();
                }
            } finally {
                is.close();
            }
        }

        f.deleteOnExit();

        return new FileInputStream(f);
    }

    private static String temporaryName(String filePath, String entryPath) {
        String fileHash = String.valueOf(filePath.hashCode());
        String entryHash = String.valueOf(entryPath.hashCode());

        StringBuilder sb = new StringBuilder();
        sb.append("f").append(fileHash).append("e").append(entryHash);

        return sb.toString().replace('-', 'x'); //NOI18N
    }

    private Cache getCache() {
        Cache ret = strongCache;

        if (ret == null) {
            ret = softCache.get();
        }

        if (ret == null) {
            ret = parse(false);
        }

        assert ret != null;

        return ret;
    }

    /**
     * refreshes children recursively.
     */
    private void refreshExistingFileObjects() {
        Cache cache = getCache();
        String[] empty = new String[0];

        Enumeration<? extends FileObject> en = existingFileObjects(getRoot());

        while (en.hasMoreElements()) {
            // AbstractFolder is package...might have to resort to reflection...
            /*AbstractFolder fo = (AbstractFolder) en.nextElement();
             assert fo != null;

             if (fo.isFolder() && !fo.isInitialized()) {
             continue;
             }

             String[] children = cache.getChildrenOf(fo.getPath());

             if (children == null) {
             children = empty;
             }

             fo.refresh(null, null, true, true, children);*/
        }
    }

    /**
     * parses entries of JarFile into EntryCache hierarchical structure and sets lastModified to actual value.
     */
    private Cache parse(boolean refresh) {
        // force watcher to reschedule us if not succesfull
        PharArchiveFile j = null;
        long start;

        //beginAtomicAction();
        try {
            synchronized (closeSync) {
                start = System.currentTimeMillis();

                lastModification = 0;
                closeCurrentRoot(false);

                for (int i = 0; i <= 2; i++) {
                    try {
                        j = reOpenArchiveFile();

                        break;
                    } catch (IOException ex) {
                        if (i >= 2) {
                            return Cache.INVALID;
                        }

                        continue;
                    }
                }

                try {
                    Enumeration<PharArchiveFile.ArchiveEntry> en = j.entries();
                    // #144166 - If duplicate entries found in pharArchiveFile, it is logged
                    // and only unique entries are show. It can happen because
                    // Ant's pharArchiveFile task can produce such aars.
                    Set<String> duplicateCheck = new HashSet<String>();
                    Set<PharArchiveFile.ArchiveEntry> uniqueEntries = new HashSet<PharArchiveFile.ArchiveEntry>();
                    boolean duplicateReported = false;
                    while (en.hasMoreElements()) {
                        PharArchiveFile.ArchiveEntry entry = en.nextElement();
                        String name = entry.getName();
                        if (duplicateCheck.add(name)) {
                            uniqueEntries.add(entry);
                        } else {
                            if (!duplicateReported) {
                                LOGGER.warning("Duplicate entries in " + getArchiveFile() + ": " + name + "; please report to JAR creator.");
                                // report just once
                                duplicateReported = true;
                            }
                        }
                    }
                    Cache newCache = new Cache(uniqueEntries);
                    lastModification = root.lastModified();
                    strongCache = newCache;
                    softCache = new SoftReference<Cache>(newCache);

                    return newCache;
                } catch (Throwable t) {
                    // pharArchiveFile is invalid; perhaps it's being rebuilt
                    // don't touch filesystem
                    return Cache.INVALID;
                }
            }
        } finally {
            closeCurrentRoot(false);

            if (refresh) {
                refreshExistingFileObjects();
            }

            if ((checkTime > 0) && (watcherTask == null)) {
                watcherTask = req.post(watcherTask(), checkTime);
            }

            //finishAtomicAction();
        }
    }

    /* Anonymous Runnable class - responsible for checking whether JarFile was modified => standalone thread.
     * If JarFile was modified, parsing is invoked.
     */
    private Runnable watcherTask() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (root == null) {
                        return;
                    }

                    /**
                     * JarFile was modified => parse it and refresh existing FileObjects
                     */
                    if (root.lastModified() != lastModification) {
                        parse(true);
                    }
                } finally {
                    /**
                     * reschedule watcherTask
                     */
                    if (watcherTask != null) {
                        watcherTask.schedule(checkTime);
                    }
                }
            }
        };
    }

    private void setArchive(PharArchiveFile af) {
        this.af = af;
    }

    //
    // List
    //
    protected String[] children(String name) {
        Cache cache = getCache();

        return cache.getChildrenOf(name);
    }

    //
    // Change
    //
    protected void createFolder(String name) throws java.io.IOException {
        throw new IOException();
    }

    protected void createData(String name) throws IOException {
        throw new IOException();
    }

    protected void rename(String oldName, String newName)
            throws IOException {
        throw new IOException();
    }

    protected void delete(String name) throws IOException {
        throw new IOException();
    }

    //
    // Info
    //
    protected Date lastModified(String name) {
        long t = 0;
        if (name.length() == 0) {
            t = getArchiveFile().lastModified();
        } else {
            try {
                PharArchiveFile.ArchiveEntry e = getEntry(name);
                if (null != e) {
                    t = e.getTime();
                }
            } finally {
                closeCurrentRoot(false);
            }
        }
        return new Date(t);
    }

    protected boolean folder(String name) {
        if ("".equals(name)) {
            return true;
        }

        Cache cache = getCache();

        return cache.isFolder(name);
    }

    protected boolean readOnly(String name) {
        return true;
    }

    protected String mimeType(String name) {
        return null;
    }

    protected long size(String name) {
        long retVal = getEntry(name).getSize();
        closeCurrentRoot(false);

        return (retVal == -1) ? 0 : retVal;
    }

    protected OutputStream outputStream(String name) throws java.io.IOException {
        throw new IOException();
    }

    protected void lock(String name) throws IOException {
        throw new IOException(NbBundle.getMessage(PharArchiveFileSystem.class, "EXC_CannotLock_JAR", name, root));
    }

    protected void unlock(String name) {
    }

    protected void markUnimportant(String name) {
    }

    protected Object readAttribute(String name, String attrName) {
        if ("java.io.File".equals(attrName)) {
            return null;
        }
        return null;
    }

    protected void writeAttribute(String name, String attrName, Object value)
            throws IOException {
        throw new IOException("Setting attribute not allowed for PharArchiveFileSystem [" + this.getDisplayName() + "!" + name + " <- " + attrName + "=" + value + "]");  //NOI18N
    }

    // attributes of tar entry
    protected Enumeration<String> attributes(String name) {
        return org.openide.util.Enumerations.empty();
    }

    protected void renameAttributes(String oldName, String newName) {
    }

    protected void deleteAttributes(String name) {
    }

    private static class Cache {

        private static final Set<PharArchiveFile.ArchiveEntry> EMPTY_SET = Collections.emptySet();
        static final Cache INVALID = new Cache(EMPTY_SET);
        byte[] names = new byte[1000];
        private int nameOffset = 0;
        int[] EMPTY = new int[0];
        private final Map<String, Folder> folders = new HashMap<String, Folder>();

        public Cache(Set<PharArchiveFile.ArchiveEntry> entries) {
            parse(entries);
            trunc();
        }

        public boolean isFolder(String name) {
            return folders.get(name) != null;
        }

        public String[] getChildrenOf(String folder) {
            Folder fol = folders.get(folder);

            if (fol != null) {
                return fol.getNames();
            }

            return new String[]{};
        }

        private void parse(Set<PharArchiveFile.ArchiveEntry> entries) {
            folders.put("", new Folder()); // root folder

            for (PharArchiveFile.ArchiveEntry entry : entries) {
                String name = entry.getName();
                boolean isFolder = false;

                // work only with slashes
                name = name.replace('\\', '/');

                if (name.startsWith("/")) {
                    name = name.substring(1);
                }

                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                    isFolder = true;
                }

                int lastSlash = name.lastIndexOf('/');
                String dirName = ""; // root
                String realName = name;

                if (lastSlash > 0) {
                    dirName = name.substring(0, lastSlash); // or folder
                    realName = name.substring(lastSlash + 1);
                }

                if (isFolder) {
                    getFolder(name); // will create the folder item
                } else {
                    Folder fl = getFolder(dirName);
                    fl.addChild(realName);
                }
            }
        }

        private Folder getFolder(String name) {
            Folder fl = folders.get(name);

            if (fl == null) {
                // add all the superfolders on the way to the root
                int lastSlash = name.lastIndexOf('/');
                String dirName = ""; // root
                String realName = name;

                if (lastSlash > 0) {
                    dirName = name.substring(0, lastSlash); // or folder
                    realName = name.substring(lastSlash + 1);
                }

                getFolder(dirName).addChild(realName);

                fl = new Folder();
                folders.put(name, fl);
            }

            return fl;
        }

        private void trunc() {
            // strip the name array:
            byte[] newNames = new byte[nameOffset];
            System.arraycopy(names, 0, newNames, 0, nameOffset);
            names = newNames;

            // strip all the indices arrays:
            for (Iterator<?> it = folders.values().iterator(); it.hasNext();) {
                ((Folder) it.next()).trunc();
            }
        }

        private int putName(byte[] name) {
            int start = nameOffset;

            if ((start + name.length) > names.length) {
                byte[] newNames = new byte[(names.length * 2) + name.length];
                System.arraycopy(names, 0, newNames, 0, start);
                names = newNames;
            }

            System.arraycopy(name, 0, names, start, name.length);
            nameOffset += name.length;

            return start;
        }

        private class Folder {

            private int[] indices = EMPTY;
            private int idx = 0;

            public Folder() {
            }

            public String[] getNames() {
                String[] ret = new String[idx / 2];

                for (int i = 0; i < ret.length; i++) {
                    byte[] name = new byte[indices[(2 * i) + 1]];
                    System.arraycopy(names, indices[2 * i], name, 0, name.length);

                    try {
                        ret[i] = new String(name, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new InternalError("No UTF-8");
                    }
                }

                return ret;
            }

            void addChild(String name) {
                // ensure enough space
                if ((idx + 2) > indices.length) {
                    int[] newInd = new int[(2 * indices.length) + 2];
                    System.arraycopy(indices, 0, newInd, 0, idx);
                    indices = newInd;
                }

                try {
                    byte[] bytes = name.getBytes("UTF-8");
                    indices[idx++] = putName(bytes);
                    indices[idx++] = bytes.length;
                } catch (UnsupportedEncodingException e) {
                    throw new InternalError("No UTF-8");
                }
            }

            void trunc() {
                if (indices.length > idx) {
                    int[] newInd = new int[idx];
                    System.arraycopy(indices, 0, newInd, 0, idx);
                    indices = newInd;
                }
            }
        }
    }

    /**
     * Implementation of all interfaces List, Change, Info and Attr that delegates to JarFileSystem
     */
    public static class Impl extends Object implements AbstractFileSystem.List, AbstractFileSystem.Info,
            AbstractFileSystem.Change, AbstractFileSystem.Attr {

        /**
         * generated Serialized Version UID
         */
        static final long serialVersionUID = -67233308132567232L;

        /**
         * the pointer to filesystem
         */
        private PharArchiveFileSystem fs;

        /**
         * Constructor.
         *
         * @param fs the filesystem to delegate to
         */
        public Impl(PharArchiveFileSystem fs) {
            this.fs = fs;
        }

        /*
         *
         * Scans children for given name
         */
        @Override
        public String[] children(String name) {
            return fs.children(name);
        }

        //
        // Change
        //

        /*
         * Creates new folder named name.
         * @param name name of folder
         * @throws IOException if operation fails
         */
        @Override
        public void createFolder(String name) throws java.io.IOException {
            fs.createFolder(name);
        }

        /*
         * Create new data file.
         *
         * @param name name of the file
         *
         * @return the new data file object
         * @exception IOException if the file cannot be created (e.g. already exists)
         */
        @Override
        public void createData(String name) throws IOException {
            fs.createData(name);
        }

        /*
         * Renames a file.
         *
         * @param oldName old name of the file
         * @param newName new name of the file
         */
        @Override
        public void rename(String oldName, String newName)
                throws IOException {
            fs.rename(oldName, newName);
        }

        /*
         * Delete the file.
         *
         * @param name name of file
         * @exception IOException if the file could not be deleted
         */
        @Override
        public void delete(String name) throws IOException {
            fs.delete(name);
        }

        //
        // Info
        //

        /*
         *
         * Get last modification time.
         * @param name the file to test
         * @return the date
         */
        @Override
        public java.util.Date lastModified(String name) {
            return fs.lastModified(name);
        }

        /*
         * Test if the file is folder or contains data.
         * @param name name of the file
         * @return true if the file is folder, false otherwise
         */
        @Override
        public boolean folder(String name) {
            return fs.folder(name);
        }

        /*
         * Test whether this file can be written to or not.
         * @param name the file to test
         * @return <CODE>true</CODE> if file is read-only
         */
        @Override
        public boolean readOnly(String name) {
            return fs.readOnly(name);
        }

        /*
         * Get the MIME type of the file.
         * Uses {@link FileUtil#getMIMEType}.
         *
         * @param name the file to test
         * @return the MIME type textual representation, e.g. <code>"text/plain"</code>
         */
        @Override
        public String mimeType(String name) {
            return fs.mimeType(name);
        }

        /*
         * Get the size of the file.
         *
         * @param name the file to test
         * @return the size of the file in bytes or zero if the file does not contain data (does not
         *  exist or is a folder).
         */
        @Override
        public long size(String name) {
            return fs.size(name);
        }

        /*
         * Get input stream.
         *
         * @param name the file to test
         * @return an input stream to read the contents of this file
         * @exception FileNotFoundException if the file does not exists or is invalid
         */
        @Override
        public InputStream inputStream(String name) throws java.io.FileNotFoundException {
            return fs.inputStream(name);
        }

        /*
         * Get output stream.
         *
         * @param name the file to test
         * @return output stream to overwrite the contents of this file
         * @exception IOException if an error occures (the file is invalid, etc.)
         */
        @Override
        public OutputStream outputStream(String name) throws java.io.IOException {
            return fs.outputStream(name);
        }

        /*
         * Does nothing to lock the file.
         *
         * @param name name of the file
         */
        @Override
        public void lock(String name) throws IOException {
            fs.lock(name);
        }

        /*
         * Does nothing to unlock the file.
         *
         * @param name name of the file
         */
        @Override
        public void unlock(String name) {
            fs.unlock(name);
        }

        /*
         * Does nothing to mark the file as unimportant.
         *
         * @param name the file to mark
         */
        @Override
        public void markUnimportant(String name) {
            fs.markUnimportant(name);
        }

        /*
         * Get the file attribute with the specified name.
         * @param name the file
         * @param attrName name of the attribute
         * @return appropriate (serializable) value or <CODE>null</CODE> if the attribute is unset (or could not be properly restored for some reason)
         */
        @Override
        public Object readAttribute(String name, String attrName) {
            return fs.readAttribute(name, attrName);
        }

        /*
         * Set the file attribute with the specified name.
         * @param name the file
         * @param attrName name of the attribute
         * @param value new value or <code>null</code> to clear the attribute. Must be serializable, although particular filesystems may or may not use serialization to store attribute values.
         * @exception IOException if the attribute cannot be set. If serialization is used to store it, this may in fact be a subclass such as {@link NotSerializableException}.
         */
        @Override
        public void writeAttribute(String name, String attrName, Object value)
                throws IOException {
            fs.writeAttribute(name, attrName, value);
        }

        /*
         * Get all file attribute names for the file.
         * @param name the file
         * @return enumeration of keys (as strings)
         */
        @Override
        public Enumeration<String> attributes(String name) {
            return fs.attributes(name);
        }

        /*
         * Called when a file is renamed, to appropriatelly update its attributes.
         * <p>
         * @param oldName old name of the file
         * @param newName new name of the file
         */
        @Override
        public void renameAttributes(String oldName, String newName) {
            fs.renameAttributes(oldName, newName);
        }

        /*
         * Called when a file is deleted to also delete its attributes.
         *
         * @param name name of the file
         */
        @Override
        public void deleteAttributes(String name) {
            fs.deleteAttributes(name);
        }
    }

}
