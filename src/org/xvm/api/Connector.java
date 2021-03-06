package org.xvm.api;

import java.io.File;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHeap;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.TemplateRegistry;

/**
 * The API between Java host environment and an XVM runtime.
 *
 * For a given Connector there is one and only one Runtime and one and only one top level
 * Container. All underlying Containers will use the same Runtime.
 *
 * Normally, the usage of the Connector follows these steps:
 * <ul>
 *   <li> instantiate a Connector
 *   <li> add necessary module repositories
 *   <li> load a [main] module getting the Runtime back
 *   <li> configure the Runtime and the Container
 *   <li> start the Runtime and the Container for the main module
 *   <li> wait for the natural termination or explicitly shutdown the container at some point
 * </ul>
 */
public class Connector
    {
    /**
     * Construct the Connector based on the specified ModuleRepository.
     */
    public Connector(ModuleRepository repository)
        {
        m_repository = repository;
        f_runtime    = new Runtime();

        ModuleStructure moduleRoot = repository.loadModule(Constants.ECSTASY_MODULE);

        f_templates  = new TemplateRegistry(moduleRoot);
        f_heapGlobal = new ObjectHeap(moduleRoot.getConstantPool(), f_templates);

        ConstantPool.setCurrentPool(moduleRoot.getConstantPool());

        f_templates.loadNativeTemplates(moduleRoot);

        ConstantPool.setCurrentPool(null);
        }

    /**
     * Add a module repository.
     */
    public void addModuleRepository(File fileRepo)
            throws IOException
        {
        if (m_container != null)
            {
            throw new IllegalStateException("Connector is already activated");
            }

        ModuleRepository repo = fileRepo.isFile()
            ? new FileRepository(fileRepo, true)
            : new DirRepository(fileRepo, true);

        if (m_repository == null)
            {
            m_repository = repo;
            }
        else if (m_repository instanceof LinkedRepository)
            {
            List<ModuleRepository> listRepo = ((LinkedRepository) m_repository).asList();
            listRepo.add(repo);

            m_repository = new LinkedRepository(
                listRepo.toArray(new ModuleRepository[listRepo.size()]));
            }
        else
            {
            m_repository = new LinkedRepository(m_repository, repo);
            }
        }

    /**
     * Create the container for the specified module.
     */
    public void loadModule(String sAppName)
        {
        if (m_container != null)
            {
            throw new IllegalStateException("Connector is already activated");
            }

        m_container = new Container(f_runtime, sAppName, m_repository, f_templates, f_heapGlobal);
        }

    public Runtime getRuntime()
        {
        return f_runtime;
        }

    public Container getContainer()
        {
        return m_container;
        }

    /**
     * Start the Runtime and the main Container.
     */
    public void start()
        {
        if (!m_fStarted)
            {
            f_runtime.start();
            m_fStarted = true;
            }

        m_container.start();
        }

    /**
     * Invoke a method with a void return and specified arguments.
     *
     * @param sMethodName  the method name
     * @param ahArg        arguments
     */
    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        if (!m_fStarted)
            {
            throw new IllegalStateException("The container has not been started");
            }
        m_container.invoke0(sMethodName, ahArg);
        }

    public ObjectHandle invoke1(String sMethodName, ObjectHandle... ahArg)
        {
        return null;
        }

    public ObjectHandle[] invokeN(String sMethodName, ObjectHandle... ahArg)
        {
        return null;
        }

    public void join()
            throws InterruptedException
        {
        // extremely naive; replace
        do {
            Thread.sleep(500);
            }
        while (!f_runtime.isIdle() || !m_container.isIdle());

        m_container = null;
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The module repository.
     */
    private ModuleRepository m_repository;

    /**
     * The runtime associated with this Connector.
     */
    private final Runtime f_runtime;

    /**
     * The template registry.
     */
    private final TemplateRegistry f_templates;

    /**
     * The template registry.
     */
    private final ObjectHeap f_heapGlobal;

    /**
     * The container associated with this Connector.
     */
    private Container m_container;

    /**
     * Status indicator.
     */
    private boolean m_fStarted;
    }
