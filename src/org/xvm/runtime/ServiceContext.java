package org.xvm.runtime;


import java.util.Queue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.asm.op.Return_0;

import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.Function.FunctionHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.Service;
import org.xvm.runtime.template.Service.PropertyOperation;
import org.xvm.runtime.template.Service.PropertyOperation10;
import org.xvm.runtime.template.Service.PropertyOperation01;
import org.xvm.runtime.template.Service.PropertyOperation11;
import org.xvm.runtime.template.Service.ServiceHandle;


/**
 * The service context.
 *
 * @author gg 2017.02.15
 */
public class ServiceContext
    {
    public final Container f_container;
    public final TypeSet f_types;
    public final ObjectHeap f_heapGlobal;
    public final ConstantPool f_pool;

    private final Queue<Message> f_queueMsg;
    private final Queue<Response> f_queueResponse;

    private final int f_nId; // the service id
    public final String f_sName; // the service name

    protected ServiceHandle m_hService;

    public int m_iFrameCounter; // used to create the Frame id

    /**
     * The current Timeout that will be used by the service when it invokes other services.
     */
    public long m_cTimeoutMillis;

    // Metrics: the total time (in nanos) this service has been running
    protected long m_cRuntimeNanos;

    private Frame m_frameCurrent;
    private FiberQueue f_queueSuspended = new FiberQueue(); // suspended fibers

    enum Reentrancy {Prioritized, Open, Exclusive, Forbidden}
    volatile Reentrancy m_reentrancy = Reentrancy.Prioritized;

    enum ServiceStatus
        {
        Idle,
        Busy,
        ShuttingDown,
        Terminated,
        }
    volatile ServiceStatus m_status = ServiceStatus.Idle;

    final static ThreadLocal<ServiceContext> s_tloContext = new ThreadLocal<>();

    ServiceContext(Container container, String sName, int nId)
        {
        f_container = container;
        f_sName = sName;
        f_nId = nId;

        f_heapGlobal = container.f_heapGlobal;
        f_types = container.f_types;
        f_pool = container.f_pool;
        f_queueMsg = new ConcurrentLinkedQueue<>();
        f_queueResponse = new ConcurrentLinkedQueue<>();
        }

    public Frame getCurrentFrame()
        {
        return m_frameCurrent;
        }

    public static ServiceContext getCurrentContext()
        {
        return s_tloContext.get();
        }

    public ServiceHandle getService()
        {
        return m_hService;
        }

    public void setService(ServiceHandle hService)
        {
        assert m_hService == null;
        m_hService = hService;
        }

    public void addRequest(Message msg)
        {
        f_queueMsg.add(msg);
        }

    public void respond(Response response)
        {
        f_queueResponse.add(response);
        }

    // get a next frame ready for execution
    public Frame nextFiber()
        {
        // responses have the highest priority and no user code runs there;
        // process all we've got so far
        Queue<Response> qResponse = f_queueResponse;
        Response response;
        while ((response = qResponse.poll()) != null)
            {
            response.run();
            }

        // pickup all the messages, but keep them in the "initial" state
        Queue<Message> qMsg = f_queueMsg;
        Message message;
        while ((message = qMsg.poll()) != null)
            {
            s_tloContext.set(this);
            Frame frame = message.createFrame(this);

            suspendFiber(frame);
            }

        // allow initial timeouts to be processed always, since they won't run any used code
        // TODO: return ?f_queueSuspended.getInitialTimeout();

        Frame frameCurrent = m_frameCurrent;
        if (frameCurrent != null)
            {
            // resume the paused frame (could be forbidden reentrancy)
            return frameCurrent.f_fiber.isReady() ? frameCurrent : null;
            }

        FiberQueue qSuspended = f_queueSuspended;
        if (qSuspended.isEmpty())
            {
            // nothing to do
            return null;
            }

        switch (m_reentrancy)
            {
            default:
            case Forbidden:
                throw new IllegalStateException(); // assert

            case Exclusive:
                // don't allow a new fiber unless it belongs to already existing thread of execution
                return qSuspended.getAssociatedOrYielded();

            case Prioritized:
                // give priority to already existing thread of execution
                Frame frameNext = qSuspended.getAssociatedOrYielded();
                if (frameNext != null)
                    {
                    return frameNext;
                    }
                // fall through

            case Open:
                return qSuspended.getAnyReady();
            }
        }

    public void suspendFiber(Frame frame)
        {
        switch (frame.f_fiber.getStatus())
            {
            case Running:
                throw new IllegalStateException(); // assert

            case InitialNew:
            case InitialAssociated:
                f_queueSuspended.add(frame);
                break;

            case Waiting:
            case Yielded:
                if (m_reentrancy == Reentrancy.Forbidden)
                    {
                    m_frameCurrent = frame;
                    }
                else
                    {
                    m_frameCurrent = null;
                    f_queueSuspended.add(frame);
                    }
                break;

            case Paused:
                // we must resume this frame
                m_frameCurrent = frame;
                break;
            }
        }

    // return null iff there the context popped up all frames
    public Frame execute(Frame frame)
        {
        Fiber fiber = frame.f_fiber;
        int iPC = frame.m_iPC;
        int iPCLast = iPC;

        m_frameCurrent = frame;
        s_tloContext.set(this);

        if (fiber.isTimedOut())
            {
            iPC = frame.raiseException(xException.makeHandle("The service has timed-out"));
            }
        else if (fiber.getStatus() == FiberStatus.Waiting)
            {
            switch (frame.checkWaitingRegisters())
                {
                case Op.R_BLOCK:
                    // there are still some "waiting" registers
                    return frame;

                case Op.R_EXCEPTION:
                    iPC = Op.R_EXCEPTION;
                    break;

                case Op.R_NEXT:
                    // proceed as is
                    break;
                }
            }

        fiber.setStatus(FiberStatus.Running);
        fiber.m_fResponded = false;

        Op[] abOp = frame.f_aOp;

        int nOps = 0;

    nextOp:
        while (true)
            {
            while (iPC >= 0) // main loop
                {
                frame.m_iPC = iPC;

                if (++nOps > 10)
                    {
                    fiber.setStatus(FiberStatus.Paused);
                    return frame;
                    }

                iPC = abOp[iPC].process(frame, iPCLast = iPC);

                if (iPC == Op.R_NEXT)
                    {
                    iPC = iPCLast + 1;
                    }
                }

            switch (iPC)
                {
                case Op.R_CALL:
                    m_frameCurrent = frame.m_frameNext;
                    frame.m_iPC = iPCLast + 1;
                    frame.m_frameNext = null;
                    frame = m_frameCurrent;
                    abOp = frame.f_aOp;
                    iPC = 0;
                    break;

                case Op.R_BLOCK_RETURN:
                    fiber.setStatus(FiberStatus.Waiting);
                    // fall through

                case Op.R_RETURN:
                    {
                    Frame frameCaller = frame.f_framePrev;
                    Frame.Continuation continuation = frame.m_continuation;
                    if (continuation != null)
                        {
                        switch (continuation.proceed(frameCaller))
                            {
                            case Op.R_NEXT:
                                break;

                            case Op.R_EXCEPTION:
                                // continuation is allowed to "throw"
                                assert frameCaller.m_hException != null;

                                iPC = Op.R_EXCEPTION;
                                continue nextOp;

                            case Op.R_CALL:
                                assert frameCaller.m_frameNext != null;

                                frame = m_frameCurrent = frameCaller.m_frameNext;
                                frameCaller.m_frameNext = null;
                                abOp = frame.f_aOp;
                                iPC = 0;
                                continue nextOp;

                            default:
                                throw new IllegalStateException();
                            }
                        }

                    frame = m_frameCurrent = frameCaller;
                    if (frame == null)
                        {
                        // all done
                        fiber.setStatus(FiberStatus.Terminated);
                        return null;
                        }

                    if (fiber.getStatus() == FiberStatus.Waiting)
                        {
                        return frame;
                        }
                    abOp = frame.f_aOp;
                    iPC = frame.m_iPC;
                    break;
                    }

                case Op.R_RETURN_EXCEPTION:
                    frame = frame.f_framePrev;
                    // fall-through

                case Op.R_EXCEPTION:
                    {
                    ExceptionHandle hException = frame.m_hException;
                    assert hException != null;

                    while (true)
                        {
                        iPC = frame.findGuard(hException);
                        if (iPC >= 0)
                            {
                            // handled exception; go to the handler
                            m_frameCurrent = frame;
                            abOp = frame.f_aOp;
                            break;
                            }

                        // not handled by this frame
                        Frame frameCaller = frame.f_framePrev;
                        if (frameCaller != null)
                            {
                            frame = frameCaller;
                            continue;
                            }

                        // no one handled the exception and we have reached the "proto-frame";
                        // it will process the exception
                        if (frame.m_continuation == null)
                            {
                            throw new IllegalStateException("Proto-frame is missing the continuation: " + hException);
                            }

                        frame.raiseException(hException);

                        switch (frame.m_continuation.proceed(null))
                            {
                            case Op.R_NEXT:
                                // this fiber is done
                                break;

                            case Op.R_CALL:
                                assert frame.m_frameNext != null;

                                iPC = Op.R_CALL;
                                continue nextOp;

                            default:
                                // the proto-frame never throws
                                throw new IllegalStateException();
                            }

                        fiber.setStatus(FiberStatus.Terminated);
                        return m_frameCurrent = null;
                        }
                    break;
                    }

                case Op.R_REPEAT:
                    frame.m_iPC = iPCLast;
                    fiber.setStatus(FiberStatus.Waiting);
                    return frame;

                case Op.R_BLOCK:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Waiting);
                    return frame;

                case Op.R_YIELD:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Yielded);
                    return frame;

                default:
                    throw new IllegalStateException("Invalid code: " + iPC);
                }
            }
        }

    // create a new frame that returns zero or one value into the specified slot
    public Frame createFrame1(Frame framePrev, MethodStructure template,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(framePrev, template, hTarget, ahVar, iReturn, null);
        }

    public Frame createFrameN(Frame framePrev, MethodStructure template,
                             ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return new Frame(framePrev, template, hTarget, ahVar, Frame.RET_MULTI, aiReturn);
        }

    // create a "proto"-frame
    protected Frame createServiceEntryFrame(Message msg, int cReturns, Op[] aopNative)
        {
        // create a pseudo frame that has variables to collect
        // the return values
        ObjectHandle[] ahVar = new ObjectHandle[cReturns];

        Fiber fiber = new Fiber(this, msg);
        Frame frame = new Frame(fiber, msg.f_iCallerPC, aopNative, ahVar, Frame.RET_UNUSED, null);

        for (int nVar = 0; nVar < cReturns; nVar++)
            {
            frame.f_aInfo[nVar] = new Frame.VarInfo(xObject.CLASS, null, Frame.VAR_STANDARD);
            }
        return frame;
        }

    // ----- x:Service methods -----

    public boolean isContended()
        {
        return !f_queueMsg.isEmpty() || !f_queueSuspended.isEmpty() || m_frameCurrent != null;
        }

    // send and asynchronous "construct service" message to this context
    public CompletableFuture<ServiceHandle> sendConstructRequest(Frame frameCaller,
                MethodStructure constructor, TypeComposition clazz, ObjectHandle[] ahArg)
        {
        CompletableFuture<ServiceHandle> future = new CompletableFuture<>();

        addRequest(new ConstructRequest(frameCaller, constructor, clazz, future, ahArg));

        return future.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // the construction failed; we need to kill the service
                f_container.removeServiceContext(this);
                }
            });
        }

    // send and asynchronous "invoke" message with zero or one return value
    public CompletableFuture<ObjectHandle> sendInvoke1Request(Frame frameCaller,
                FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle> future = cReturns == 0 ? null : new CompletableFuture<>();

        addRequest(new Invoke1Request(frameCaller, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous "invoke" message with multiple return values
    public CompletableFuture<ObjectHandle[]> sendInvokeNRequest(Frame frameCaller,
                FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle[]> future = new CompletableFuture<>();

        addRequest(new InvokeNRequest(frameCaller, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous property operation message
    public CompletableFuture<ObjectHandle> sendProperty01Request(Frame frameCaller,
                                            String sPropName, PropertyOperation01 op)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new PropertyOpRequest(frameCaller, sPropName, null, 1, future, op));

        return future;
        }

    // send and asynchronous property operation message
    public void sendProperty10Request(Frame frameCaller,
                                      String sPropName, ObjectHandle hValue, PropertyOperation10 op)
        {
        addRequest(new PropertyOpRequest(frameCaller, sPropName, hValue, 0, null, op));
        }

    public int callLater(FunctionHandle hFunction, ObjectHandle[] ahArg)
        {
        sendInvoke1Request(null, hFunction, ahArg, 0);
        return Op.R_NEXT;
        }

    public int callUnhandledExceptionHandler(Frame frame)
        {
        // TODO: call the handler (via invokeLater)
        Utils.log("Unhandled exception: " + frame.m_hException + " " + frame.m_hException.m_mapFields);
        return Op.R_NEXT;
        }

    // ----- helpers ------

    // send one results back to the caller
    protected static void sendResponse1(Fiber fiberCaller, Frame frame, CompletableFuture future)
        {
        ObjectHandle hReturn = frame.f_ahVar[0];

        // TODO: validate that all the arguments are immutable or ImmutableAble;
        //       replace functions with proxies
        fiberCaller.f_context.respond(
                new Response(fiberCaller, hReturn, frame.m_hException, future));
        }

    // send all results back to the caller
    protected static void sendResponseN(Fiber fiberCaller, Frame frame, CompletableFuture future)
        {
        fiberCaller.f_context.respond(
                new Response(fiberCaller, frame.f_ahVar, frame.m_hException, future));
        }

    @Override
    public String toString()
        {
        return "Service \"" + f_sName + "\" (id=" + f_nId + ')';
        }

    // --- inner classes

    public abstract static class Message
        {
        public final Fiber f_fiberCaller;
        public final MethodStructure f_fnCaller;
        public final int f_iCallerId; // the FrameId of the caller
        public final int f_iCallerPC; // the PC of the caller

        protected Message(Frame frameCaller)
            {
            if (frameCaller == null)
                {
                f_fiberCaller = null;
                f_fnCaller = null;
                f_iCallerId = 0;
                f_iCallerPC = -1;
                }
            else
                {
                f_fiberCaller = frameCaller.f_fiber;
                f_fnCaller = frameCaller.f_function;
                f_iCallerId = frameCaller.f_iId;
                f_iCallerPC = frameCaller.m_iPC;
                }
            }

        abstract Frame createFrame(ServiceContext context);
        }

    /**
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class ConstructRequest
            extends Message
        {
        private final MethodStructure f_constructor;
        private final TypeComposition f_clazz;
        private final ObjectHandle[] f_ahArg;
        private final CompletableFuture<ServiceHandle> f_future;

        public ConstructRequest(Frame frameCaller, MethodStructure constructor, TypeComposition clazz,
                                CompletableFuture<ServiceHandle> future, ObjectHandle[] ahArg)
            {
            super(frameCaller);

            f_constructor = constructor;
            f_clazz = clazz;
            f_ahArg = ahArg;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opConstruct = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    IdentityConstant constClass = f_constructor.getParent().getParent().getIdentityConstant();
                    Service service = (Service) frame.f_context.f_types.getTemplate(constClass);

                    return service.constructSync(frame, f_constructor, f_clazz, f_ahArg, 0);
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, 1,
                    new Op[]{opConstruct, Return_0.INSTANCE});

            frame0.setContinuation((_null) ->
                {
                sendResponse1(f_fiberCaller, frame0, f_future);
                return Op.R_NEXT;
                });

            return frame0;
            }
        }

    /**
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class Invoke1Request
            extends Message
        {
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;

        public Invoke1Request(Frame frameCaller, FunctionHandle hFunction,
                              ObjectHandle[] ahArg, int cReturns,
                              CompletableFuture<ObjectHandle> future)
            {
            super(frameCaller);

            f_hFunction = hFunction;
            f_ahArg = ahArg;
            f_cReturns = cReturns;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return f_hFunction.call1(frame, context.getService(), f_ahArg, f_cReturns == 1 ? 0 : Frame.RET_UNUSED);
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                    new Op[] {opCall, Return_0.INSTANCE});

            frame0.setContinuation((_null) ->
                {
                if (f_cReturns == 0)
                    {
                    if (frame0.m_hException != null)
                        {
                        return context.callUnhandledExceptionHandler(frame0);
                        }
                    }
                else
                    {
                    sendResponse1(f_fiberCaller, frame0, f_future);
                    }
                return Op.R_NEXT;
                });

            return frame0;
            }
        }

    /**
     * Represents an invoke request from one service onto another with multiple return values.
     */
    public static class InvokeNRequest
            extends Message
        {
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle[]> f_future;

        public InvokeNRequest(Frame frameCaller, FunctionHandle hFunction,
                             ObjectHandle[] ahArg, int cReturns,
                             CompletableFuture<ObjectHandle[]> future)
            {
            super(frameCaller);

            f_hFunction = hFunction;
            f_ahArg = ahArg;
            f_cReturns = cReturns;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            // the pseudo-frame's vars are the return values
            int[] aiReturn = new int[f_cReturns];
            for (int i = 0; i < f_cReturns; i++)
                {
                aiReturn[i] = i;
                }

            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return f_hFunction.callN(frame, context.getService(), f_ahArg, aiReturn);
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                new Op[] {opCall, Return_0.INSTANCE});

            frame0.setContinuation((_null) ->
                {
                sendResponseN(f_fiberCaller, frame0, f_future);
                return Op.R_NEXT;
                });

            return frame0;
            }
        }

    /**
     * Represents a property operation request from one service onto another
     * that takes zero or one parameter and returns zero or one value.
     */
    public static class PropertyOpRequest
            extends Message
        {
        private final String f_sPropName;
        private final ObjectHandle f_hValue;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;
        private final PropertyOperation f_op;

        public PropertyOpRequest(Frame frameCaller, String sPropName,
                                 ObjectHandle hValue, int cReturns,
                                 CompletableFuture<ObjectHandle> future, PropertyOperation op)
            {
            super(frameCaller);

            f_sPropName = sPropName;
            f_hValue = hValue;
            f_cReturns = cReturns;
            f_future = future;
            f_op = op;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            int cReturns = f_cReturns;

            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return cReturns == 0
                            ? ((PropertyOperation10) f_op).invoke(frame, context.m_hService, f_sPropName, f_hValue)
                       : f_hValue == null
                            ? ((PropertyOperation01) f_op).invoke(frame, context.m_hService, f_sPropName, 0)
                            : ((PropertyOperation11) f_op).invoke(frame, context.m_hService, f_sPropName, f_hValue, 0);
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, cReturns,
                    new Op[]{opCall, Return_0.INSTANCE});

            frame0.setContinuation((_null) ->
                {
                if (cReturns == 0)
                    {
                    if (frame0.m_hException != null)
                        {
                        return context.callUnhandledExceptionHandler(frame0);
                        }
                    }
                else
                    {
                    sendResponse1(f_fiberCaller, frame0, f_future);
                    }
                return Op.R_NEXT;
                });

            return frame0;
            }
        }

    /**
     * Represents a service call return.
     */
    public static class Response<T>
            implements Runnable
        {
        private final Fiber f_fiberCaller;
        private final T f_return;
        private final ExceptionHandle f_hException;
        private final CompletableFuture<T> f_future;

        public Response(Fiber fiberCaller, T returnValue, ExceptionHandle hException,
                        CompletableFuture<T> future)
            {
            f_fiberCaller = fiberCaller;
            f_hException = hException;
            f_return = returnValue;
            f_future = future;
            }

        @Override
        public void run()
            {
            f_fiberCaller.m_fResponded = true;

            if (f_hException == null)
                {
                f_future.complete(f_return);
                }
            else
                {
                f_future.completeExceptionally(f_hException.getException());
                }
            }
        }
    }