import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Native OS Directory implementation.
 */
const OSDirectory
        extends OSFileNode
        implements Directory
    {
    @Override
    Iterator<String> names()
        {
        return store.names(this:protected).iterator();
        }

    @Override
    Iterator<Directory> dirs();

    @Override
    Iterator<File> files();

    @Override
    Iterator<File> filesRecursively();

    @Override
    conditional Directory|File find(String name)
        {
        return store.find(path + name);
        }

    @Override
    Directory dirFor(String name)
        {
        return store.dirFor(path + name);
        }

    @Override
    File fileFor(String name)
        {
        return store.fileFor(path + name);
        }

    @Override
    Boolean deleteRecursively();

    @Override
    Cancellable watch(FileWatcher watcher)
        {
        return store.watchDir(this, watcher);
        }

    @Override
    Cancellable watchRecursively(FileWatcher watcher);
    }
