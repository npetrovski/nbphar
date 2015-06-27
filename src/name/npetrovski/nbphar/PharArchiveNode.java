package name.npetrovski.nbphar;

import javax.swing.Action;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.RequestProcessor;

final class PharArchiveNode extends DataNode {

    private static final RequestProcessor RP = new RequestProcessor(PharArchiveNode.class.getName(), 1, false, false);

    public PharArchiveNode(PharArchiveDataObject obj) {
        this(obj, new DummyChildren());
    }

    private PharArchiveNode(PharArchiveDataObject obj, DummyChildren c) {
        super(obj, c);
        c.attachAltArchiveNode(this);
        //setIconBaseWithExtension("name/npetrovski/nbphar/phar.png"); // NOI18N
    }

//    @Override
//    protected Sheet createSheet() {
//       Sheet s = Sheet.createDefault();
//        try {
//            s.get(Sheet.PROPERTIES).put(new LineCountProperty(this, this.getDataObject().getPrimaryFile()));
//        } catch (Exception ex) {
//            ErrorManager.getDefault().notify(ex);
//        }
//        return s;
//    }
//
//    private class LineCountProperty extends ReadOnly<Integer> {
//
//        private final PharArchiveNode node;
//        private final FileObject fo;
//
//        public LineCountProperty(PharArchiveNode node, FileObject fo) {
//            super("lineCount", Integer.class, "Line Count", "Number of Lines");
//            this.node = node;
//            this.fo = fo;
//        }
//
//        @Override
//        public Integer getValue() throws IllegalAccessException, InvocationTargetException {
//            int lineCount = 0;
//
//            return 10;
//        }
//    }

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

        private void attachAltArchiveNode(PharArchiveNode archiveNode) {
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
