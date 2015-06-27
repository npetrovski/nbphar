package name.npetrovski.jphar;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;


public class DirectoryPharEntryProvider implements PharEntryProvider {

    private final Path rootPath;
    private final String localPath;
    private final PharCompression pharCompression;

    public DirectoryPharEntryProvider(final File directory, final String localPath, final PharCompression pharCompression) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Directory must be a valid directory");
        }
        if (StringUtils.isEmpty(localPath)) {
            throw new IllegalArgumentException("Local path cannot be empty");
        }
        if (pharCompression == null) {
            throw new IllegalArgumentException("Phar compression cannot be null");
        }
        this.rootPath = directory.toPath();
        this.localPath = localPath;
        this.pharCompression = pharCompression;
    }

    @Override
    public List<PharEntry> getPharEntries() throws IOException {
        List<PharEntry> pharEntries = new ArrayList();
        addPharEntriesRecursively(pharEntries, this.rootPath);
        return pharEntries;

    }

    private void addPharEntriesRecursively(final List<PharEntry> pharEntries, final Path directory) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
            for (Path element : directoryStream) {
                File file = element.toFile();
                if (file.isDirectory()) {
                    addPharEntriesRecursively(pharEntries, element);
                } else {
                    String relativePath = this.rootPath.relativize(element).toString();
                    pharEntries.add(new PharEntry(file, this.localPath + "/" + relativePath, this.pharCompression));
                }
            }
        }
    }

}
