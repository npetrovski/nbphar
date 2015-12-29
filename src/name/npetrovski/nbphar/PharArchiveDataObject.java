package name.npetrovski.nbphar;

import java.io.IOException;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

@Messages({
    "LBL_PHAR_LOADER=PHP Phar Archives"
})
@MIMEResolver.ExtensionRegistration(
    displayName = "#LBL_PHAR_LOADER",
    mimeType = "application/x-phar",
    extension = {"phar", "PHAR"}
)
@DataObject.Registration(
    mimeType = "application/x-phar",
    iconBase = "name/npetrovski/nbphar/phar.png",
    displayName = "#LBL_PHAR_LOADER",
    position = 300
)
@ActionReferences({
    @ActionReference(
            id = 
                    @ActionID(category = "Edit", id = "org.openide.actions.CutAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 200, 
            separatorBefore = 100), 
    @ActionReference(
            id = 
                    @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 300, separatorAfter = 400), 
    @ActionReference(
            id = 
                    @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 500), 
    @ActionReference(
            id = 
                    @ActionID(category = "System", id = "org.openide.actions.RenameAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 600, 
            separatorAfter = 700), 
    @ActionReference(
            id = 
                    @ActionID(category = "System", id = "org.openide.actions.SaveAsTemplateAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 800, 
            separatorAfter = 900), 
    @ActionReference(
            id = 
                    @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 1000), 
    @ActionReference(
            id = 
                    @ActionID(category = "System", id = "org.openide.actions.ToolsAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 1200), 
    @ActionReference(
            id = 
                    @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"), 
            path = "Loaders/application/x-phar/Actions", 
            position = 1300)
})
public class PharArchiveDataObject extends MultiDataObject {

    public PharArchiveDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(pf, loader);
    }

    @Override
    protected Node createNodeDelegate() {
        return new PharArchiveNode(this);
    }

    @Override
    public Lookup getLookup() {
        return getCookieSet().getLookup();
    }
}
