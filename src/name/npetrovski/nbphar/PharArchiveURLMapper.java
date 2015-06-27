package name.npetrovski.nbphar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.JarFileSystem;
import org.openide.filesystems.Repository;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = URLMapper.class, position = 0)
public class PharArchiveURLMapper extends URLMapper {

    static final String PHARARCHIVE_PROTOCOL = "jar";   //NOI18N

    private static Map<File, SoftReference<FileSystem>> mountRoots = new ConcurrentHashMap();

    public @Override
    URL getURL(FileObject fo, int type) {
        assert fo != null;
        if (type == URLMapper.EXTERNAL || type == URLMapper.INTERNAL) {
            if (fo.isValid()) {
                try {
                    FileSystem fs = fo.getFileSystem();
                    if (fs instanceof PharArchiveFileSystem) {
                        PharArchiveFileSystem jfs = (PharArchiveFileSystem) fs;
                        File archiveFile = jfs.getArchiveFile();
                        if (isRoot(archiveFile)) {
                            URI uri = archiveFile.toURI();
                            URL url;
                            /*if (fo.getMIMEType().equals("application/x-java-archive")) {
                             String x ="jar:aar:file:";
                             return new URL(x);
                             }*/
                            url = new URL(PHARARCHIVE_PROTOCOL, null, uri.getPort(), "file:" + uri.getPath() + "!/"
                                    + new URI(null, fo.getPath(), null).getRawSchemeSpecificPart()
                                    + (fo.isFolder() && !fo.isRoot() ? "/" : ""), new PharURLStreamHandler()); // NOI18N
                            return url;
                        }
                    }
                } catch (FileStateInvalidException | URISyntaxException | MalformedURLException e) {
                    Exceptions.printStackTrace(e);
                }
            }
        }
        return null;
    }

    public @Override
    FileObject[] getFileObjects(URL url) {
        assert url != null;
        String protocol = url.getProtocol();
        if (PHARARCHIVE_PROTOCOL.equals(protocol) || url.toExternalForm().startsWith("jar:" + PHARARCHIVE_PROTOCOL)) {
            String path = url.getPath();
            int index = path.lastIndexOf('!');
            if (index >= 0) {
                try {
                    URL archiveFileURL;
                    URI archiveFileURI = new URI(path.substring(0, index));
                    try {
                        if (archiveFileURI.getScheme().equals(PHARARCHIVE_PROTOCOL)) {
                            archiveFileURL = new URL(PHARARCHIVE_PROTOCOL, null, -1, path.substring(4, index), new PharURLStreamHandler());
                        } else {
                            archiveFileURL = archiveFileURI.toURL();
                        }
                    } catch (IllegalArgumentException x) {
                        //ModuleLayeredFileSystem.err.log(Level.INFO, "checking " + archiveFileURI, x);
                        return null;
                    }
                    FileObject fo = URLMapper.findFileObject(archiveFileURL);
                    if (fo == null || fo.isVirtual()) {
                        return null;
                    }
                    boolean isJar = fo.getMIMEType().equals("application/x-java-archive");
                    File archiveFile = org.openide.filesystems.FileUtil.toFile(fo);
                    if (archiveFile == null) {
                        archiveFile = copyJAR(fo, archiveFileURI, false);
                    }
                    // XXX new URI("substring").getPath() might be better?
                    String offset = path.length() > index + 2 ? URLDecoder.decode(path.substring(index + 2), "UTF-8") : "";   //NOI18N
                    FileSystem fs = getFileSystem(archiveFile, isJar);
                    FileObject resource = fs.findResource(offset);
                    //FileObject resource = fs.getRoot();
                    if (resource != null) {
                        return new FileObject[]{resource};
                    }
                } catch (IOException e) {
                    //ModuleLayeredFileSystem.err.log(Level.INFO, "checking " + url, e);
                } catch (URISyntaxException e) {
                    Exceptions.printStackTrace(e);
                }
            }
        }
        return null;
    }

    /**
     * #177052 - not necessary to be synchronized.
     */
    private static boolean isRoot(File file) {
        return mountRoots.containsKey(file);
    }

    private static synchronized FileSystem getFileSystem(File file, boolean isJar) throws IOException {
        Reference<FileSystem> reference = mountRoots.get(file);
        FileSystem jfs = null;
        if (reference == null || (jfs = reference.get()) == null) {
            jfs = findAltArchiveFileSystemInRepository(file);
            if (jfs == null) {
                File aRoot = org.openide.filesystems.FileUtil.normalizeFile(file);
                if (isJar) {
                    jfs = new JarFileSystem(aRoot);
                } else {
                    jfs = new PharArchiveFileSystem(aRoot);
                }
            }
            mountRoots.put(file, new JFSReference(jfs));
        }
        return jfs;
    }

    // More or less copied from URLMapper:
    private static PharArchiveFileSystem findAltArchiveFileSystemInRepository(File aarFile) {
        @SuppressWarnings("deprecation") // for compat only
        Enumeration<? extends FileSystem> en = Repository.getDefault().getFileSystems();
        while (en.hasMoreElements()) {
            FileSystem fs = en.nextElement();
            if (fs instanceof PharArchiveFileSystem) {
                PharArchiveFileSystem jfs = (PharArchiveFileSystem) fs;
                if (aarFile.equals(jfs.getArchiveFile())) {
                    return jfs;
                }
            }
        }
        return null;
    }

    /**
     * After deleting and recreating of phar file there must be properly refreshed cached map "mountRoots".
     */
    private static class JFSReference extends SoftReference<FileSystem> {

        private FileChangeListener fcl;

        public JFSReference(FileSystem jfs) throws IOException {
            super(jfs);
            final File root;
            if (jfs instanceof PharArchiveFileSystem) {
                root = ((PharArchiveFileSystem) jfs).getArchiveFile();
            } else {
                root = ((JarFileSystem) jfs).getJarFile();
            }
            URI nestedRootURI = null;
            FileObject rootFo = null;
            if (copiedJARs.values().contains(root)) {
                // nested phar
                for (Map.Entry<URI, File> entry : copiedJARs.entrySet()) {
                    if (entry.getValue().equals(root)) {
                        nestedRootURI = entry.getKey();
                        URL nestedRootURL = new URL(PHARARCHIVE_PROTOCOL, null, -1, nestedRootURI.toString().substring(4), new PharURLStreamHandler());
                        rootFo = URLMapper.findFileObject(nestedRootURL);
                    }
                }
            } else {
                // regular phar
                rootFo = org.openide.filesystems.FileUtil.toFileObject(root);
            }
            final URI nestedRootURIFinal = nestedRootURI;
            if (rootFo != null) {
                fcl = new FileChangeAdapter() {
                    public @Override
                    void fileDeleted(FileEvent fe) {
                        releaseMe(root);
                    }

                    public @Override
                    void fileRenamed(FileRenameEvent fe) {
                        releaseMe(root);
                    }

                    @Override
                    public void fileChanged(FileEvent fe) {
                        if (nestedRootURIFinal != null) {
                            try {
                                // update copy of nested aar and re-register root
                                copyJAR(fe.getFile(), nestedRootURIFinal, true);
                                releaseMe(root);
                                // and register again
                                getFileSystem(root, false); // @todo - do not think this will nest jar or ever create
                            } catch (IOException e) {
                                Exceptions.printStackTrace(e);
                            }
                        }
                    }
                };
                rootFo.addFileChangeListener(org.openide.filesystems.FileUtil.weakFileChangeListener(fcl, rootFo));
            }
        }

        void releaseMe(final File root) {
            FileSystem jfs = get();
            if (jfs != null) {
                synchronized (PharArchiveURLMapper.class) {
                    File keyToRemove;
                    if (jfs instanceof PharArchiveFileSystem) {
                        keyToRemove = (root != null) ? root : ((PharArchiveFileSystem) jfs).getArchiveFile();
                    } else {
                        keyToRemove = (root != null) ? root : ((JarFileSystem) jfs).getJarFile();
                    }
                    mountRoots.remove(keyToRemove);
                }
            }
        }
    }

    private static final Map<URI, File> copiedJARs = new HashMap();

    private static File copyJAR(FileObject fo, URI archiveFileURI, boolean replace) throws IOException {
        synchronized (copiedJARs) {
            File copy = copiedJARs.get(archiveFileURI);
            if (copy == null || replace) {
                if (copy == null) {
                    copy = File.createTempFile("copy", "-" + archiveFileURI.toString().replaceFirst(".+/", "")); // NOI18N
                    copy.deleteOnExit();
                }
                InputStream is = fo.getInputStream();
                try {
                    OutputStream os = new FileOutputStream(copy);
                    try {
                        org.openide.filesystems.FileUtil.copy(is, os);
                    } finally {
                        os.close();
                    }
                } finally {
                    is.close();
                }
                copiedJARs.put(archiveFileURI, copy);
            }
            return copy;
        }
    }
}
