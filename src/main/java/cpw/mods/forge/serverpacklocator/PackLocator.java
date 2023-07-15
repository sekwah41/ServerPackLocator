package cpw.mods.forge.serverpacklocator;

import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path serverModsPath;
    private final SidedPackHandler serverPackLocator;
    private IModLocator dirLocator;

    public PackLocator() {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
        final Path gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        serverPackLocator = SidedPackHandler.buildFor(LaunchEnvironmentHandler.INSTANCE.getDist(), serverModsPath);
        if (!serverPackLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
    }

    @Override
    public List<ModFileOrException> scanMods() {
        boolean successfulDownload = serverPackLocator.waitForDownload();

        final List<ModFileOrException> scannedMods = dirLocator.scanMods();

        final List<ModFileOrException> finalModList = new ArrayList<>();
        if (successfulDownload) {
            List<IModFile> mods = new ArrayList<>(scannedMods.size());
            for (ModFileOrException scannedMod : scannedMods) {
                if (scannedMod.file() != null) {
                    mods.add(scannedMod.file());
                }
                if (scannedMod.ex() != null) {
                    finalModList.add(scannedMod);
                }
            }
            for (IModFile modFile : serverPackLocator.processModList(mods)) {
                finalModList.add(new ModFileOrException(modFile, null));
            }
        } else {
            finalModList.add(new ModFileOrException(null, new ModFileLoadingException("Failed to download server pack")));
        }

        ModAccessor.statusLine = "ServerPack: " + (successfulDownload ? "loaded" : "NOT loaded");
        return finalModList;
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer) {
        dirLocator.scanFile(modFile, pathConsumer);
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        final IModDirectoryLocatorFactory modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
        dirLocator = modFileLocator.build(serverModsPath, "serverpack");

        serverPackLocator.setForgeVersion((String) arguments.get("forgeVersion"));
        serverPackLocator.setMcVersion((String) arguments.get("mcVersion"));

        if (serverPackLocator.isValid()) {
            serverPackLocator.initialize(dirLocator);
        }

        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        LOGGER.info("Loading server pack locator from: " + url.toString());
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return dirLocator.isValid(modFile);
    }
}
