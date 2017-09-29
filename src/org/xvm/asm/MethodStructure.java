package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a method.
 *
 * @author cp 2016.04.25
 */
public class MethodStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MethodStructure with the specified identity. This constructor is used to
     * deserialize a MethodStructure.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }

    /**
     * Construct a method structure.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     * @param aReturns   an array of Parameters representing the "out" values
     * @param aParams    an array of Parameters representing the "in" values
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId, ConditionalConstant condition,
            Parameter[] aReturns, Parameter[] aParams)
        {
        this(xsParent, nFlags, constId, condition);

        m_aReturns = aReturns;
        m_aParams  = aParams;

        if (aReturns.length > 0 && aReturns[0].isConditionalReturn())
            {
            setConditionalReturn(true);
            }

        int cTypeParams = 0;
        for (Parameter param : aParams)
            {
            if (param.isTypeParameter())
                {
                ++cTypeParams;
                }
            else
                {
                break;
                }
            }
        m_cTypeParams = cTypeParams;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the number of return values
     */
    public int getReturnCount()
        {
        return m_aReturns.length;
        }

    /**
     * Get the Parameter structure that represents the i-th return value.
     *
     * @param i  an index
     *
     * @return the i-th return value
     */
    public Parameter getReturn(int i)
        {
        return m_aReturns[i];
        }

    /**
     * @return a list of Parameter structures that represent all return values of the method
     */
    public List<Parameter> getReturns()
        {
        return Arrays.asList(m_aReturns);
        }

    /**
     * @return the number of method parameters (including the number of type parameters)
     */
    public int getParamCount()
        {
        return m_aParams.length;
        }

    /**
     * @return the number of type parameters
     */
    public int getTypeParamCount()
        {
        return m_cTypeParams;
        }

    /**
     * Get the Parameter structure that represents the i-th method parameter. The type parameters
     * come first, followed by the ordinary parameters.
     *
     * @param i  an index
     *
     * @return the i-th method parameter
     */
    public Parameter getParam(int i)
        {
        return m_aParams[i];
        }

    /**
     * @return a list of Parameter structures that represent all parameters of the method
     */
    public List<Parameter> getParams()
        {
        return Arrays.asList(m_aParams);
        }

    /**
     * @return the op-code array for this method
     */
    public Op[] getOps()
        {
        Op[] aop = m_aop;
        if (aop == null && !m_fNative)
            {
            byte[] abOps = m_abOps;
            if (abOps != null)
                {
                try
                    {
                    m_aop = aop = abOps.length == 0
                            ? Op.NO_OPS
                            : Op.readOps(new DataInputStream(new ByteArrayInputStream(abOps)), m_aconstLocal);
                    }
                catch (IOException e)
                    {
                    throw new RuntimeException(e);
                    }
                }
            }
        return m_aop;
        }

    public Constant[] getLocalConstants()
        {
        return m_aconstLocal;
        }

    /**
     * Specify the ops for this method.
     *
     * @param aop  the op-code array for this method
     */
    public void setOps(Op[] aop)
        {
        m_aop = aop;
        resetRuntimeInfo();
        markModified();
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * Initialize the runtime information. This is done automatically.
     */
    public void ensureRuntimeInfo()
        {
        if (m_cScopes == 0)
            {
            Scope scope = new Scope();
            for (int i = 0, c = getParamCount(); i < c; ++i)
                {
                scope.allocVar();
                }

            if (!m_fNative)
                {
                // calc scopes and vars using ops
                Op[] aop = getOps();
                if (aop != null)
                    {
                    for (Op op : aop)
                        {
                        op.simulate(scope);
                        }
                    }
                }

            m_cVars   = scope.getMaxVars();
            m_cScopes = scope.getMaxDepth();
            }
        }

    /**
     * Discard any runtime information.
     */
    public void resetRuntimeInfo()
        {
        m_cVars   = 0;
        m_cScopes = 0;
        m_fNative = false;
        }

    /**
     * @return the number of variables (registers) necessary for a frame running this method's code
     *         (including the parameters)
     */
    public int getMaxVars()
        {
        ensureRuntimeInfo();
        return m_cVars;
        }

    /**
     * @return the number of scopes necessary for a frame running this method's code
     */
    public int getMaxScopes()
        {
        ensureRuntimeInfo();
        return m_cScopes;
        }

    /**
     * @return true iff the method has been marked as native
     */
    public boolean isNative()
        {
        return m_fNative;
        }

    /**
     * Specifies whether or not the method implementation is provided directly by the runtime, aka
     * "native".
     *
     * @param fNative  pass true to mark the method as native
     */
    public void setNative(boolean fNative)
        {
        if (fNative)
            {
            resetRuntimeInfo();
            }
        m_fNative = fNative;
        }

    public MethodStructure getConstructFinally()
        {
        // TODO this method must calculate the value
        return m_structFinally;
        }

    public void setConstructFinally(MethodStructure structFinally)
        {
        // TODO this method must die (eventually)
        m_structFinally = structFinally;
        }

    /**
     * Indicates whether or not this method contains a call to its super.
     */
    public boolean isSuperCalled()
        {
        // TODO: the compiler would supply this information
        return getAccess() != Access.PRIVATE;
        }

    /**
     * Check if this method is accessible with the specified access policy.
     */
    public boolean isAccessible(Access access)
        {
        return getAccess().ordinal() <= access.ordinal();
        }

    /**
     * Determine if this method consumes a formal type with the specified name.
     *
     * A method _m_ "consumes" type _T_ if any of the following holds true:
     * 1. _m_ has a parameter type declared as _T_;
     * 2. _m_ has a parameter type that _"produces T"_.
     * 3. _m_ has a return type that _"consumes T"_;
     */
    public boolean consumesFormalType(String sTypeName, TypeSet types)
        {
        for (Parameter param : getParams())
            {
            if (param.getType().producesFormalType(sTypeName, types, Access.PUBLIC))
                {
                return true;
                }
            }

        for (Parameter param : getReturns())
            {
            if (param.getType().consumesFormalType(sTypeName, types, Access.PUBLIC))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if this method produces a formal type with the specified name.
     *
     * A method _m_ "produces" type _T_ if any of the following holds true:
     * 1. _m_ has a return type declared as _T_;
     * 2. _m_ has a return type that _"produces T"_;
     * 3. _m_ has a parameter type that _"consumes T"_.
     */
    public boolean producesFormalType(String sTypeName, TypeSet types)
        {
        for (Parameter param : getParams())
            {
            if (param.getType().consumesFormalType(sTypeName, types, Access.PUBLIC))
                {
                return true;
                }
            }

        for (Parameter param : getReturns())
            {
            if (param.getType().producesFormalType(sTypeName, types, Access.PUBLIC))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Check if this method could act as a substitute for the specified method.
     *
     * @param signature   the signature of the matching method
     * @param clazz       the TypeComposition in which context's the matching is evaluated
     */
    public boolean isSubstitutableFor(SignatureConstant signature, TypeComposition clazz)
        {
        int cParams = getParamCount();
        int cReturns = getReturnCount();

        if (cParams != signature.getParams().size() ||
            cReturns != signature.getReturns().size())
            {
            return false;
            }

        /*
         * From Method.x # isSubstitutableFor() (where m2 == this and m1 == that)
         *
         * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
         *    true:
         *    1. _m1_ and _m2_ have the same name
         *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
         *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
         *       1. _p1_ is assignable to _p2_
         *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
         *          the following hold true:
         *          1. _p2_ is assignable to _p1_
         *          2. _T1_ produces _p1_
         *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
         *       _m1_ and _r2_ of _m2_, the following holds true:
         *      1. _r2_ is assignable to _r1_
         */
        for (int i = 0; i < cReturns; i++)
            {
            TypeConstant typeR2 = getReturn(i).getType();
            TypeConstant typeR1 = signature.getRawReturns()[i];

            if (!typeR2.isA(typeR1, clazz))
                {
                return false;
                }
            }

        for (int i = 0; i < cParams; i++)
            {
            TypeConstant typeP2 = getParam(i).getType();
            TypeConstant typeP1 = signature.getRawParams()[i];

            if (typeP1.isA(typeP2, clazz))
                {
                continue;
                }

            if (!typeP2.isA(typeP1, clazz))
                {
                return false;
                }

            // TODO:
            // if there is an number of different formal names, then at least one of them must be
            // produced by the type T1
//            if (String[] namesThis : this.formalParamNames(loop.count))
//                {
//                if (String[] namesThat : that.formalParamNames(loop.count))
//                    {
//                    for (String name : nameThis.intersection(namesThat))
//                        {
//                        if (that.TargetType.produces(typeP1))
//                            {
//                            return true;
//                            }
//                        }
//                    }
//                }

            return false;
            }
        return true;
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public boolean isConditionalReturn()
        {
        return super.isConditionalReturn();
        }

    @Override
    public void setConditionalReturn(boolean fConditional)
        {
        if (fConditional != isConditionalReturn())
            {
            // verify that the first return value is a boolean
            Parameter paramOld = m_aReturns[0];
            if (!(paramOld.getType().isEcstasy("Boolean")))
                {
                throw new IllegalStateException("first return value is not Boolean (" + paramOld + ")");
                }

            // change the first return value as specified
            m_aReturns[0] = new Parameter(getConstantPool(), paramOld.getType(), paramOld.getName(),
                    paramOld.getDefaultValue(), true, 0, fConditional);

            super.setConditionalReturn(fConditional);
            }
        }

    @Override
    public String getName()
        {
        return getIdentityConstant().getName();
        }

    @Override
    protected boolean isChildLessVisible()
        {
        return true;
        }

    @Override
    protected Component getEldestSibling()
        {
        Component parent = getParent();
        assert parent != null;

        Component sibling = parent.getMethodByConstantMap().get(getIdentityConstant());
        assert sibling != null;

        return sibling;
        }

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }

    @Override
    public ResolutionResult resolveName(String sName, ResolutionCollector collector)
        {
        for (int i = 0, c = m_cTypeParams; i < c; ++i)
            {
            Parameter param = m_aParams[i];
            assert param.isTypeParameter();

            if (param.getName().equals(sName))
                {
                return collector.resolvedType(
                        getConstantPool().ensureRegisterConstant(getIdentityConstant(), i));
                }
            }

        // method short-circuits the search
        return ResolutionResult.UNKNOWN;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MethodConstant getIdentityConstant()
        {
        return (MethodConstant) super.getIdentityConstant();
        }

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        super.disassemble(in);

        ConstantPool   pool              = getConstantPool();
        MethodConstant constMethod       = getIdentityConstant();
        TypeConstant[] aconstReturnTypes = constMethod.getRawReturns();
        TypeConstant[] aconstParamTypes  = constMethod.getRawParams();

        int         cReturns = aconstReturnTypes.length;
        Parameter[] aReturns = new Parameter[cReturns];
        boolean     fCond    = isConditionalReturn();
        for (int i = 0; i < cReturns; ++i)
            {
            Parameter param = new Parameter(pool, in, true, i, i==0 && fCond);
            if (!param.getType().equals(aconstReturnTypes[i]))
                {
                throw new IOException("type mismatch between method constant and return " + i + " value type");
                }
            aReturns[i] = param;
            }

        int         cParams     = aconstParamTypes.length;
        Parameter[] aParams     = new Parameter[cParams];
        int         cTypeParams = readMagnitude(in);
        for (int i = 0; i < cParams; ++i)
            {
            Parameter param = new Parameter(pool, in, true, i, i < cTypeParams);
            if (!param.getType().equals(aconstParamTypes[i]))
                {
                throw new IOException("type mismatch between method constant and param " + i + " value type");
                }
            aParams[i] = param;
            }

        // read local "constant pool"
        int cConsts = readMagnitude(in);
        Constant[] aconst = cConsts == 0 ? Constant.NO_CONSTS : new Constant[cConsts];
        for (int i = 0; i < cConsts; ++i)
            {
            aconst[i] = pool.getConstant(readMagnitude(in));
            }

        // read code
        int cbOps = readMagnitude(in);
        byte[] abOps = new byte[cbOps];
        in.readFully(abOps);

        m_aReturns    = aReturns;
        m_cTypeParams = cTypeParams;
        m_aParams     = aParams;
        m_aconstLocal = aconst;
        m_abOps       = abOps;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // if the return type is Void, that means that there is no return type at all
        if (m_aReturns.length == 1 && m_aReturns[0].getType().isVoid())
            {
            m_aReturns = NO_PARAMS;
            }

        for (Parameter param : m_aReturns)
            {
            param.registerConstants(pool);
            }

        for (Parameter param : m_aParams)
            {
            param.registerConstants(pool);
            }

        // local constants:
        // (1) if the ops array exists (which would be the result either of deserialization of the
        //     ops, or the ops being specified to the method structure), then ask the ops to
        //     contribute constants, otherwise
        // (2) if the local constants array exists (which would be the result from deserialization
        //     of the method without deserialization of the ops), then use it, otherwise
        // (3) assume there are no local constants
        if (m_aop != null)
            {
            // TODO collect constants into m_aconstLocal
            }

        Constant[] aconst = m_aconstLocal;
        if (aconst != null)
            {
            for (int i = 0, c = aconst.length; i < c; ++i)
                {
                Constant constOld = aconst[i];
                Constant constNew = pool.register(constOld);
                if (constNew != constOld)
                    {
                    aconst[i] = constNew;
                    }
                }
            }
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        super.assemble(out);

        for (Parameter param : m_aReturns)
            {
            param.assemble(out);
            }

        writePackedLong(out, m_cTypeParams);
        for (Parameter param : m_aParams)
            {
            param.assemble(out);
            }

        Constant[] aconst  = m_aconstLocal;
        int        cConsts = aconst == null ? 0 : aconst.length;
        writePackedLong(out, cConsts);
        for (int i = 0; i < cConsts; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }

        if (m_aop != null)
            {
            // TODO assemble into m_abOps
            }

        byte[] abOps = m_abOps;
        int    cbOps = abOps == null ? 0 : abOps.length;
        writePackedLong(out, cbOps);
        if (cbOps > 0)
            {
            out.write(abOps);
            }
        }


    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("id=\"")
                .append(getIdentityConstant().getValueString())
                .append("\", sig=")
                .append(getIdentityConstant().getSignature())
                .append(", ")
                .append(super.getDescription())
                .append(", conditional=")
                .append(isConditionalReturn())
                .append(", type-param-count=")
                .append(m_cTypeParams)
                .toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of Parameters.
     */
    public static final Parameter[] NO_PARAMS = new Parameter[0];

    /**
     * Empty array of Ops.
     */
    public static final Op[] NO_OPS = new Op[0];

    /**
     * The return value types. (A zero-length array is "Void".)
     */
    private Parameter[] m_aReturns;

    /**
     * The number of type parameters.
     */
    private int m_cTypeParams;

    /**
     * The parameter types.
     */
    private Parameter[] m_aParams;

    /**
     * The constants used by the Ops.
     */
    private transient Constant[] m_aconstLocal;

    /**
     * The yet-to-be-deserialized ops.
     */
    private transient byte[] m_abOps;

    /**
     * The instructions that make up the method's behavior. Calculated from the ops.
     */
    private Op[] m_aop;

    /**
     * The max number of registers used by the method. Calculated from the ops.
     */
    private transient int m_cVars;

    /**
     * The max number of scopes used by the method.
     */
    private transient int m_cScopes;

    /**
     * True iff the method has been marked as "native". This is not part of the persistent method
     * structure; it exists only to support the prototype interpreter implementation.
     */
    private transient boolean m_fNative;

    /**
     * Cached method for the construct-finally that goes with this method, iff this method is a
     * construct function that has a finally.
     */
    private transient MethodStructure m_structFinally;
    }
