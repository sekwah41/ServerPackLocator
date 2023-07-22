package cpw.mods.forge.serverpacklocator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirHandler {
    public static Path createOrGetDirectory(final Path root, final String name) {
        final Path newDir = root.resolve(name);
        if (Files.exists(newDir) && Files.isDirectory(newDir)) {
            return newDir;
        }

        try {
            Files.createDirectory(newDir);
            return newDir;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static Path createDirIfNeeded(final Path file) {
        try {
            Files.createDirectories(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    public static Path resolveDirectChild(final Path root, final String name) {
        final Path file = Path.of(name);
        if (file.getNameCount() != 1) {
            return null;
        }
        return switch (file.getFileName().toString()) {
            case "..", "." -> null;
            default -> {
                final Path path = root.resolve(file);
                yield path.getParent().equals(root) ? path : null;
            }
        };
    }
}
