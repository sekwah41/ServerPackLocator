package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ClientSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @Nullable
    private IModLocator serverModLocator;
    @Nullable
    private SimpleHttpClient clientDownloader;

    public ClientSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultclientconfig.toml")), path);
        return true;
    }

    @Override
    protected boolean validateConfig() {
        final Optional<String> remoteServer = getConfig().getOptional("client.remoteServer");
        if (remoteServer.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate remove server address. " +
                    "Repair or delete this file to continue", getConfig().getNioPath().toString());
            throw new IllegalStateException("Invalid configuation file found, please delete or correct");
        }
        return true;
    }

    @Override
    public List<ModFileOrException> scanMods() {
        final ServerManifest manifest = clientDownloader != null ? clientDownloader.waitForResult() : null;
        if (manifest == null) {
            LOGGER.info("There was a problem with the connection, there will not be any server mods");
            return List.of(new ModFileOrException(null, new ModFileLoadingException("Failed to download server pack")));
        }

        if (serverModLocator == null) {
            throw new IllegalArgumentException("Pack locator has not been initialized");
        }

        final Set<String> manifestFileList = manifest.files().stream()
                .map(ServerManifest.ModFileData::fileName)
                .collect(Collectors.toSet());

        return serverModLocator.scanMods().stream()
                .filter(entry -> {
                    final IModFile file = entry.file();
                    if (file == null) {
                        return true;
                    }
                    return manifestFileList.contains(file.getFileName());
                })
                .toList();
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        final IModDirectoryLocatorFactory locatorFactory = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
        serverModLocator = locatorFactory.build(serverModsDir, "serverpack");

        if (isValid()) {
            final List<String> excludedModIds = getConfig().<List<String>>getOptional("client.excludedModIds").orElse(List.of());
            clientDownloader = new SimpleHttpClient(this, Set.copyOf(excludedModIds), serverModsDir);
        }
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer) {
        if (serverModLocator != null) {
            serverModLocator.scanFile(modFile, pathConsumer);
        }
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return serverModLocator != null && serverModLocator.isValid(modFile);
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }
}
