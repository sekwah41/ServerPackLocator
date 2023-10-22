package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.PackBuilder;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class ServerSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path clientModsDir;
    private ServerFileManager serverFileManager;

    private Set<String> excludedModIds = Set.of();
    @Nullable
    private IModLocator serverModLocator;
    @Nullable
    private IModLocator clientModLocator;

    public ServerSidedPackHandler(final Path serverModsDir, final Path clientModsDir) {
        super(serverModsDir);
        this.clientModsDir = clientModsDir;
    }

    @Override
    protected boolean validateConfig() {
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (port.isPresent()) {
            return true;
        } else {
            LOGGER.fatal("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuration found");
        }
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultserverconfig.toml")), path);
        return true;
    }

    @Override
    public List<ModFileOrException> scanMods() {
        if (serverModLocator == null || clientModLocator == null) {
            throw new IllegalArgumentException("Pack locator has not been initialized");
        }

        final PackBuilder packBuilder = new PackBuilder(excludedModIds);

        final List<ModFileOrException> result = new ArrayList<>();
        final List<IModFile> combinedPack = new ArrayList<>();
        final List<IModFile> serverPack = new ArrayList<>();

        for (final ModFileOrException mod : serverModLocator.scanMods()) {
            if (mod.file() != null) {
                serverPack.add(mod.file());
                combinedPack.add(mod.file());
            } else if (mod.ex() != null) {
                result.add(mod);
            }
        }
        for (final ModFileOrException mod : clientModLocator.scanMods()) {
            if (mod.file() != null) {
                combinedPack.add(mod.file());
            } else if (mod.ex() != null) {
                result.add(mod);
            }
        }

        serverFileManager.buildManifest(packBuilder.buildModList(combinedPack));

        for (final IModFile file : packBuilder.buildModList(serverPack)) {
            result.add(new ModFileOrException(file, null));
        }

        return result;
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        final IModDirectoryLocatorFactory locatorFactory = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
        serverModLocator = locatorFactory.build(serverModsDir, "serverpack");
        clientModLocator = locatorFactory.build(clientModsDir, "clientpack");

        if (!isValid()) {
            return;
        }

        final FileConfig config = getConfig();
        final int port = config.getOptionalInt("server.port").orElse(8443);
        excludedModIds = Set.copyOf(config.<List<String>>getOptional("server.excludedModIds").orElse(List.of()));

        final SslContext sslContext = buildSslContext(config.get("server.ssl.certificateChainFile"), config.get("server.ssl.keyFile"));

        final Path manifestPath = serverModsDir.resolve("servermanifest.json");
        final List<Path> modRoots = List.of(serverModsDir, clientModsDir);
        final String forgeVersion = arguments.get("mcVersion") + "-" + arguments.get("forgeVersion");
        serverFileManager = new ServerFileManager(manifestPath, modRoots, forgeVersion);

        SimpleHttpServer.run(serverFileManager, port, sslContext);
    }

    @Nullable
    private static SslContext buildSslContext(@Nullable final String certificateChainFile, @Nullable final String keyFile) {
        if (certificateChainFile == null || keyFile == null) {
            return null;
        }
        try (
                final InputStream certificateChain = Files.newInputStream(Path.of(certificateChainFile));
                final InputStream key = Files.newInputStream(Path.of(keyFile))
        ) {
            return SslContextBuilder.forServer(certificateChain, key).build();
        } catch (final Exception e) {
            LOGGER.error("Failed to initialize SSL context for server", e);
        }
        return null;
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
