package name.npetrovski.nbphar;

import de.ailis.pherialize.Mixed;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.Action;
import name.npetrovski.jphar.Phar;
import name.npetrovski.jphar.PharCompression;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.RequestProcessor;
import org.openide.filesystems.FileUtil;

final class PharArchiveNode extends DataNode {

    private static final RequestProcessor RP = new RequestProcessor(PharArchiveNode.class.getName(), 1, false, false);

    private Phar base;

    public PharArchiveNode(PharArchiveDataObject obj) {
        this(obj, new DummyChildren());
        try {
            this.base = new Phar(FileUtil.toFile(this.getDataObject().getPrimaryFile()));
        } catch (IOException ex) {
            // shouldnt
        }
    }

    private PharArchiveNode(PharArchiveDataObject obj, DummyChildren c) {
        super(obj, c);
        c.attachArchiveNode(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = Sheet.createDefault();
        Sheet.Set def = sheet.get(Sheet.PROPERTIES);
        if (def == null) {
            def = Sheet.createPropertiesSet();
            sheet.put(def);
        }
        def.put(new PropertySupport.Name(this));

        Property<String> version = new PropertySupport.ReadOnly<String>("pharVersion", String.class, "Version", "Phar Version") {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                return PharArchiveNode.this.base.getVersion();
            }
        };

        def.put(version);

        Property<String> compression = new PropertySupport.ReadOnly<String>("pharCompression", String.class, "Compression", "Phar Compression") {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                PharCompression c = PharArchiveNode.this.base.getCompression();

                switch (c) {
                    case BZIP2:
                        return "BZIP2";
                    case GZIP:
                        return "GZIP";
                    default:
                        return "NONE";
                }
            }
        };

        def.put(compression);

        Mixed metadata = this.base.getMetadata();
        
        if (metadata != null) {
            Sheet.Set metaset = Sheet.createPropertiesSet();
            metaset.setDisplayName("Metadata");
            metaset.setName("Metadata");
            metaset.setValue("Metadata", "Metadata");

            final String data = String.valueOf(this.base.getMetadata().getValue());
 
            metaset.put(new PropertySupport.ReadOnly<String>("value", String.class, "value", null) {
                @Override
                public String getValue() throws IllegalAccessException, InvocationTargetException {
                    return data;
                }
            });


            sheet.put(metaset);
        }

        return sheet;
    }

    @Override
    public Action getPreferredAction() {
        return null;
    }

    private static Children childrenFor(FileObject fo) {
//        if (!PharFileUtil.isArchiveFile(fo)) {
        // Maybe corrupt, etc.
        //          return Children.LEAF;
        //       }
        FileObject root = PharFileUtil.getArchiveRoot(fo);
        if (root != null) {
            DataFolder df = DataFolder.findFolder(root);
            return df.createNodeChildren(DataFilter.ALL);
        } else {
            return Children.LEAF;
        }
    }

    /**
     * There is no nice way to lazy create delegating node's children. So, in order to fix #83595, here is a little hack
     * that schedules replacement of this dummy children on addNotify call.
     */
    final static class DummyChildren extends Children implements Runnable {

        private PharArchiveNode node;

        @Override
        protected void addNotify() {
            super.addNotify();
            assert node != null;
            RP.post(this);
        }

        private void attachArchiveNode(PharArchiveNode archiveNode) {
            this.node = archiveNode;
        }

        @Override
        public void run() {
            node.setChildren(childrenFor(node.getDataObject().getPrimaryFile()));
        }

        @Override
        public boolean add(final Node[] nodes) {
            // no-op
            return false;
        }

        @Override
        public boolean remove(final Node[] nodes) {
            // no-op
            return false;
        }

    }

}
