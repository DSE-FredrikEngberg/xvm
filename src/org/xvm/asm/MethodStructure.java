package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

import org.xvm.asm.Op.Prefix;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a method or a function.
 */
public class MethodStructure
        extends Component
        implements GenericTypeResolver
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
     * @param xsParent     the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags       the Component bit flags
     * @param constId      the constant that specifies the identity of the Module
     * @param condition    the optional condition for this ModuleStructure
     * @param annotations  an array of Annotations
     * @param aReturns     an array of Parameters representing the "out" values
     * @param aParams      an array of Parameters representing the "in" values
     * @param fUsesSuper   true indicates that the method is known to reference "super"
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId,
            ConditionalConstant condition,
            Annotation[] annotations, Parameter[] aReturns, Parameter[] aParams, boolean fUsesSuper)
        {
        this(xsParent, nFlags, constId, condition);

        m_aAnnotations = annotations;
        m_aReturns     = aReturns;
        m_aParams      = aParams;

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
        m_FUsesSuper  = fUsesSuper;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this is a function, not a method
     */
    public boolean isFunction()
        {
        return isStatic();
        }

    /**
     * @return the number of annotations
     */
    public int getAnnotationCount()
        {
        return m_aAnnotations == null ? 0 : m_aAnnotations.length;
        }

    /**
     * Get the Annotation structure that represents the i-th annotation.
     *
     * @param i  an index
     *
     * @return the i-th annotation
     */
    public Annotation getAnnotation(int i)
        {
        return i < 0 || i >= getAnnotationCount() ? null : m_aAnnotations[i];
        }

    /**
     * @return an array of Annotation structures that represent all annotations of the method, or
     *         null during assembly before the annotations have been split out from the return types
     */
    public Annotation[] getAnnotations()
        {
        return m_aAnnotations;
        }

    /**
     * Find an annotation with the specified annotation class.
     *
     * @param clzClass  the annotation class to search for
     *
     * @return the annotation of that annotation class, or null
     */
    public Annotation findAnnotation(ClassConstant clzClass)
        {
        for (Annotation annotation : m_aAnnotations)
            {
            if (annotation.getAnnotationClass().equals(clzClass))
                {
                return annotation;
                }
            }

        return null;
        }

    /**
     * @return true if the annotations have been resolved; false if this method has to be called
     *         later in order to resolve annotations
     */
    public boolean resolveAnnotations()
        {
        if (getReturnCount() == 0)
            {
            return true;
            }

        int cMove = 0;
        for (Annotation annotation : m_aAnnotations)
            {
            if (annotation.containsUnresolved())
                {
                return false;
                }

            if (!annotation.getAnnotationType().getExplicitClassInto().isIntoMethodType())
                {
                ++cMove;
                }
            }
        if (cMove == 0)
            {
            return true;
            }

        Annotation[] aAll  = m_aAnnotations;
        int          cAll  = aAll.length;
        if (cMove == cAll)
            {
            addReturnAnnotations(aAll);
            m_aAnnotations = Annotation.NO_ANNOTATIONS;
            return true;
            }

        int          cKeep = cAll - cMove;
        Annotation[] aKeep = new Annotation[cKeep];
        Annotation[] aMove = new Annotation[cMove];
        int          iKeep = 0;
        int          iMove = 0;
        for (Annotation annotation : m_aAnnotations)
            {
            if (annotation.getAnnotationType().getExplicitClassInto().isIntoMethodType())
                {
                aKeep[iKeep++] = annotation;
                }
            else
                {
                aMove[iMove++] = annotation;
                }
            }

        addReturnAnnotations(aMove);
        m_aAnnotations = aKeep;
        return true;
        }

    /**
     * @param annotations  the annotations to add to the return type (or the first non-conditional
     *                     return type, if there are multiple return types)
     */
    private void addReturnAnnotations(Annotation[] annotations)
        {
        assert m_aReturns != null;
        assert m_aReturns.length > 0;

        // determine which return value to modify (i.e. not the "conditional" value)
        int       iRet = 0;
        Parameter ret  = m_aReturns[iRet];
        if (ret.isConditionalReturn())
            {
            ret = m_aReturns[++iRet];
            }

        assert !ret.isConditionalReturn();
        assert iRet == ret.getIndex();

        TypeConstant type = ret.getType();
        ConstantPool pool = getConstantPool();
        for (int i = annotations.length - 1; i >= 0; --i)
            {
            type = pool.ensureAnnotatedTypeConstant(annotations[i], type);
            }
        m_aReturns[iRet] = new Parameter(pool, type, ret.getName(), ret.getDefaultValue(), true, iRet, false);
        }

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
     * @return an array of Parameter structures that represent all return values of the method
     */
    public Parameter[] getReturnArray()
        {
        return m_aReturns;
        }

    /**
     * @return an array of TypeConstant structures that represent the return types of the method
     */
    public TypeConstant[] getReturnTypes()
        {
        return toTypeArray(m_aReturns);
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
     * @return an array of Parameter structures that represent all parameters of the method
     */
    public Parameter[] getParamArray()
        {
        return m_aParams;
        }

    /**
     * @return an array of Parameter structures that represent all return values of the method
     */
    public TypeConstant[] getParamTypes()
        {
        return toTypeArray(m_aParams);
        }

    /**
     * @return an array of types for the specified array of parameters
     */
    private static TypeConstant[] toTypeArray(Parameter[] aParam)
        {
        int            cParam = aParam.length;
        TypeConstant[] aType  = new TypeConstant[cParam];
        for (int i = 0; i < cParam; ++i)
            {
            aType[i] = aParam[i].getType();
            }
        return aType;
        }

    /**
     * Ensure that a Code object exists. If the MethodStructure was disassembled, then the Code will
     * hold the deserialized Ops. If the MethodStructure has been created but no Code has already
     * been created, then the Code will be empty. If the Code was already created, then that
     * previous Code will be returned.
     *
     * @return a Code object, or null iff this MethodStructure has been marked as native
     */
    public Code ensureCode()
        {
        if (isNative() || isAbstract())
            {
            return null;
            }

        Code code = m_code;
        if (code == null)
            {
            m_code = code = new Code(this);
            }
        return code;
        }

    /**
     * Create an empty Code object.
     *
     * @return a new and empty Code object
     */
    public Code createCode()
        {
        Code code;

        resetRuntimeInfo();
        m_fAbstract   = false;
        m_fNative     = false;
        m_FUsesSuper  = null;
        m_aconstLocal = null;
        m_abOps       = null;
        m_code = code = new Code(this);

        markModified();

        return code;
        }

    /**
     * @return the op-code array for this method
     */
    public Op[] getOps()
        {
        Code code = ensureCode();
        if (code == null)
            {
            throw new IllegalStateException("Method \"" +
                getIdentityConstant().getPathString() + "\" has not been compiled");
            }

        return code.getAssembledOps();
        }

    public Constant[] getLocalConstants()
        {
        return m_aconstLocal;
        }

    /**
     * Specify the ops for this method.
     *
     * @param aop  the op-code array for this method
     *
     * @deprecated
     */
    public void setOps(Op[] aop)
        {
        resetRuntimeInfo();
        m_aconstLocal = null;
        m_abOps       = null;
        m_code        = new Code(this, aop);
        m_fAbstract   = false;
        m_fNative     = false;
        m_FUsesSuper  = null;
        markModified();
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * @return a scope containing just the parameters to the method
     */
    Scope createInitialScope()
        {
        Scope scope = new Scope();
        for (int i = 0, c = getParamCount(); i < c; ++i)
            {
            scope.allocVar();
            }
        return scope;
        }

    /**
     * Initialize the runtime information. This is done automatically.
     */
    public void ensureRuntimeInfo()
        {
        if (m_cScopes == 0)
            {
            if (m_fNative)
                {
                Scope scope = createInitialScope();
                m_cVars   = scope.getMaxVars();
                m_cScopes = scope.getMaxDepth();
                }
            else
                {
                ensureCode().ensureAssembled();
                assert m_cScopes > 0;
                }
            }
        }

    /**
     * Discard any runtime information.
     */
    public void resetRuntimeInfo()
        {
        m_code    = null;
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
     * @return true iff the method is present, but has no implementation at this virtual level
     */
    public boolean isAbstract()
        {
        return m_fAbstract;
        }

    /**
     * Specifies whether or not the method implementation is implemented at this virtual level.
     *
     * @param fAbstract  pass true to mark the method as abstract
     */
    public void setAbstract(boolean fAbstract)
        {
        if (fAbstract)
            {
            m_abOps   = null;
            m_code    = null;
            m_fNative = false;
            }

        m_fAbstract = fAbstract;
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
            m_fAbstract = false;
            resetRuntimeInfo();
            }
        m_fNative = fNative;
        }

    /**
     * Determine if this method might act as a property initializer. For example, in the property
     * declaration:
     * <p/>
     * <code><pre>
     *     Int MB = KB * KB;
     * </pre></code>
     * <p/>
     * ... the value of the property could be compiled as an initializer function named "=":
     * <p/>
     * <code><pre>
     *     Int MB
     *       {
     *       Int "="()
     *         {
     *         return KB * KB;
     *         }
     *       }
     * </pre></code>
     *
     *
     * @return true iff this method is a public method (not function) named "get" that takes no
     *         parameters and returns a single value
     */
    public boolean isPotentialInitializer()
        {
        return getName().equals("=")
                && getReturnCount() == 1 && getParamCount() == 0
                && isFunction() && !isConditionalReturn();
        }

    /**
     * Determine if this method is declared in a way that it could act as a property initializer for
     * the specified type.
     *
     * @param type      the RefType of the reference
     * @param resolver  an optional GenericTypeResolver that is used to resolve the property type
     *                  and the types in the signature if necessary
     *
     * @return true iff this method is a public method (not function) named "get" that takes no
     *         parameters and returns a single value of the specified type
     */
    public boolean isInitializer(TypeConstant type, GenericTypeResolver resolver)
        {
        return isPotentialInitializer() && (getReturn(0).getType().equals(type) || resolver != null &&
                getReturn(0).getType().resolveGenerics(resolver).equals(type.resolveGenerics(resolver)));
        }

    /**
     * Determine if this method might act as a {@code Ref.get()}, aka a "getter".
     *
     * @return true iff this method is a public method (not function) named "get" that takes no
     *         parameters and returns a single value
     */
    public boolean isPotentialGetter()
        {
        return getName().equals("get")
                && getAccess() == Access.PUBLIC
                && getReturnCount() == 1 && getParamCount() == 0
                && !isFunction() && !isConditionalReturn();
        }

    /**
     * Determine if this method is declared in a way that it could act as a {@code Ref.get()} for
     * the specified type.
     *
     * @param type      the RefType of the reference
     * @param resolver  an optional GenericTypeResolver that is used to resolve the property type
     *                  and the types in the signature if necessary
     *
     * @return true iff this method is a public method (not function) named "get" that takes no
     *         parameters and returns a single value of the specified type
     */
    public boolean isGetter(TypeConstant type, GenericTypeResolver resolver)
        {
        return isPotentialGetter() && (getReturn(0).getType().equals(type) || resolver != null &&
                getReturn(0).getType().resolveGenerics(resolver).equals(type.resolveGenerics(resolver)));
        }

    /**
     * Determine if this method might act as a {@code Ref.set()}, aka a "setter".
     *
     * @return true iff this method is a public method (not function) named "set" that takes one
     *         parameter and returns no value
     */
    public boolean isPotentialSetter()
        {
        return getName().equals("set")
                && getAccess() == Access.PUBLIC
                && getReturnCount() == 0 && getParamCount() == 1
                && !isFunction() && !isConditionalReturn();
        }

    /**
     * Determine if this method is declared in a way that it could act as a {@code Ref.set()} for
     * the specified type.
     *
     * @param type      the RefType of the reference
     * @param resolver  an optional GenericTypeResolver that is used to resolve the property type
     *                  and the types in the signature if necessary
     *
     * @return true iff this method is a public method (not function) named "set" that takes one
     *         parameter of the specified type and returns no value
     */
    public boolean isSetter(TypeConstant type, GenericTypeResolver resolver)
        {
        return isPotentialSetter() && (getParam(0).getType().equals(type) || resolver != null &&
                    getParam(0).getType().resolveGenerics(resolver).equals(type.resolveGenerics(resolver)));
        }

    /**
     * @deprecated
     */
    public MethodStructure getConstructFinally()
        {
        // TODO this method must calculate the value
        return m_structFinally;
        }

    /**
     * @deprecated
     */
    public void setConstructFinally(MethodStructure structFinally)
        {
        // TODO this method must die (eventually)
        m_structFinally = structFinally;
        }

    /**
     * Indicates whether or not this method contains a call to its super.
     */
    public boolean usesSuper()
        {
        if (m_FUsesSuper != null)
            {
            return m_FUsesSuper;
            }

        Code code = ensureCode();
        if (code == null)
            {
            return false;
            }

        return m_FUsesSuper = code.usesSuper();
        }

    /**
     * Check if this method is accessible with the specified access policy.
     */
    public boolean isAccessible(Access access)
        {
        return getAccess().ordinal() <= access.ordinal();
        }

    /**
     * Determine if this method produces a formal type with the specified name.
     *
     * A method _m_ "produces" type _T_ if any of the following holds true:
     * 1. _m_ has a return type declared as _T_;
     * 2. _m_ has a return type that _"produces T"_;
     * 3. _m_ has a parameter type that _"consumes T"_.
     */
    public boolean producesFormalType(String sTypeName)
        {
        for (Parameter param : getParams())
            {
            if (param.getType().consumesFormalType(sTypeName, Access.PUBLIC))
                {
                return true;
                }
            }

        for (Parameter param : getReturns())
            {
            if (param.getType().producesFormalType(sTypeName, Access.PUBLIC))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if this method consumes a formal type with the specified name.
     *
     * A method _m_ "consumes" type _T_ if any of the following holds true:
     * 1. _m_ has a parameter type declared as _T_;
     * 2. _m_ has a parameter type that _"produces T"_.
     * 3. _m_ has a return type that _"consumes T"_;
     */
    public boolean consumesFormalType(String sTypeName)
        {
        for (Parameter param : getParams())
            {
            if (param.getType().producesFormalType(sTypeName, Access.PUBLIC))
                {
                return true;
                }
            }

        for (Parameter param : getReturns())
            {
            if (param.getType().consumesFormalType(sTypeName, Access.PUBLIC))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Check if this method could be called via the specified signature.
     *
     * @param sigThat   the signature of the matching method (resolved)
     * @param resolver  the generic type resolver
     */
    public boolean isSubstitutableFor(SignatureConstant sigThat, GenericTypeResolver resolver)
        {
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

        // Note, that rule 1.2.2 does not apply in our case (duck typing)

        assert getName().equals(sigThat.getName());

        int cParams  = getParamCount();
        int cReturns = getReturnCount();

        if (cParams != sigThat.getParams().size() ||
            cReturns != sigThat.getReturns().size())
            {
            return false;
            }

        SignatureConstant sigThis = getIdentityConstant().getSignature();
        if (resolver != null)
            {
            sigThis = sigThis.resolveGenericTypes(resolver);
            }

        for (int i = 0; i < cReturns; i++)
            {
            TypeConstant typeR1 = sigThat.getRawReturns()[i];
            TypeConstant typeR2 = sigThis.getRawReturns()[i];

            if (!typeR2.isA(typeR1))
                {
                return false;
                }
            }

        for (int i = 0; i < cParams; i++)
            {
            TypeConstant typeP1 = sigThat.getRawParams()[i];
            TypeConstant typeP2 = sigThis.getRawParams()[i];

            if (!typeP1.isA(typeP2))
                {
                return false;
                }
            }
        return true;
        }


    // ----- GenericTypeResolver interface ---------------------------------------------------------

    @Override
    public TypeConstant resolveGenericType(PropertyConstant constProperty)
        {
        assert isFunction();

        String sName = constProperty.getName();
        for (int i = 0, c = getParamCount(); i < c; i++)
            {
            Parameter param = getParam(i);
            if (param.isTypeParameter() && sName.equals(param.getName()))
                {
                return param.getType();
                }
            }

        throw new IllegalArgumentException(
            "Invalid formal name: " + sName + " for " + this);
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
        // (1) if code was created for this method, then it needs to register the constants;
        // (2) otherwise, if the local constants are present (because we read them in), then make
        //     sure they're all registered;
        // (3) otherwise, assume there are no local constants
        Code code = m_code;
        if (code == null)
            {
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
        else
            {
            code.registerConstants();
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

        // write out the "local constant pool"
        Constant[] aconst  = m_aconstLocal;
        int        cConsts = aconst == null ? 0 : aconst.length;
        writePackedLong(out, cConsts);
        for (int i = 0; i < cConsts; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }

        Code code = m_code;
        if (code != null)
            {
            code.ensureAssembled();
            }

        // write out the bytes (if there are any)
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


    // ----- inner class: Code ---------------------------------------------------------------------

    /**
     * The Code class represents the op codes that make up a method's behavior.
     */
    public static class Code
        {
        // ----- constructors -----------------------------------------------------------------

        public Code()
            {
            this(null);
            }

        /**
         * Construct a Code object. This disassembles the bytes of code from the MethodStructure if
         * it was itself disassembled; otherwise this starts empty and allows ops to be added.
         */
        Code(MethodStructure method)
            {
            f_method = method;

            if (method != null)
                {
                byte[] abOps = method.m_abOps;
                if (abOps != null)
                    {
                    try
                        {
                        m_aop = abOps.length == 0
                                ? Op.NO_OPS
                                : Op.readOps(new DataInputStream(new ByteArrayInputStream(abOps)),
                                        method.getLocalConstants());
                        }
                    catch (IOException e)
                        {
                        throw new RuntimeException(e);
                        }
                    }
                }
            }

        /**
         * TODO remove when deprecated setOps() is removed
         *
         * @param aop   array of ops
         */
        Code(MethodStructure method, Op[] aop)
            {
            f_method = method;
            m_aop    = aop;
            calcVars();
            }

        Code(MethodStructure method, Code wrappee)
            {
            f_method = method;
            assert wrappee != null;
            }

        // ----- Code methods -----------------------------------------------------------------

        /**
         * Update the "current" line number from the corresponding source code.
         *
         * @param nLine  the new line number
         */
        public void updateLineNumber(int nLine)
            {
            m_nCurLine = nLine;
            }

        /**
         * Add the specified op to the end of the code.
         *
         * @param op  the Op to add
         *
         * @return this
         */
        public Code add(Op op)
            {
            ensureAppending();

            int nLineDelta = m_nCurLine - m_nPrevLine;
            if (nLineDelta != 0)
                {
                m_nPrevLine = m_nCurLine;
                add(new Nop(nLineDelta));
                }

            ArrayList<Op> listOps = m_listOps;
            if (m_fTrailingPrefix)
                {
                // get the last op and append this op to it
                ((Prefix) listOps.get(listOps.size()-1)).append(op);
                }
            else
                {
                listOps.add(op);
                }

            m_fTrailingPrefix = op instanceof Prefix;
            m_mapIndex        = null;

            return this;
            }

        /**
         * @return the register created by the last-added op
         */
        public Register lastRegister()
            {
            List<Op> list = m_listOps;
            if (!list.isEmpty())
                {
                Op op = list.get(list.size() - 1);  // TODO walk back until we find one
                if (op instanceof OpVar)
                    {
                    return ((OpVar) op).getRegister();
                    }
                // TODO else some op that could have gen'd a new register
                }
            throw new IllegalStateException();
            }

        /**
         * Find the specified op in the sequence of op codes.
         *
         * @param op  the op to find
         *
         * @return the index (aka the PC) within the method) of the op, or -1 if the op could not be
         *         found
         */
        public int addressOf(Op op)
            {
            IdentityHashMap<Op, Integer> mapIndex = m_mapIndex;
            if (mapIndex == null)
                {
                ArrayList<Op> listOps = m_listOps;
                int           cOps    = listOps.size();
                if (cOps < 50)
                    {
                    // brute force search a small list of ops
                    for (int i = 0; i < cOps; ++i)
                        {
                        if (listOps.get(i).contains(op))
                            {
                            return i;
                            }
                        }
                    return -1;
                    }

                m_mapIndex = mapIndex = new IdentityHashMap<>();
                for (int i = 0; i < cOps; ++i)
                    {
                    Op opEach = listOps.get(i);
                    do
                        {
                        mapIndex.put(opEach, i);
                        }
                    while (opEach instanceof Prefix && (opEach = ((Prefix) opEach).getNextOp()) != null);
                    }
                }

            Integer index = mapIndex.get(op);
            return index == null ? -1 : index;
            }

        /**
         * @return true iff any of the op codes refer to the "super"
         */
        public boolean usesSuper()
            {
            Op[] aop = ensureOps();
            if (aop != null)
                {
                for (Op op : aop)
                    {
                    if (op.usesSuper())
                        {
                        return true;
                        }
                    }
                }
            return false;
            }

        /**
         * @return the array of Ops that make up the Code
         */
        public Op[] getAssembledOps()
            {
            ensureAssembled();
            return m_aop;
            }

        /**
         * @return true iff there are any ops in the code
         */
        public boolean hasOps()
            {
            return m_listOps != null && !m_listOps.isEmpty()
                || f_method != null && f_method.m_abOps != null && f_method.m_abOps.length > 0;
            }


        // ----- helpers for building Ops -----------------------------------------------------

        /**
         * @return the enclosing MethodStructure
         */
        public MethodStructure getMethodStructure()
            {
            return f_method;
            }

        /**
         * @return a blackhole if the code is not reachable
         */
        public Code onlyIf(boolean fReachable)
            {
            return fReachable ? this : blackhole();
            }

        /**
         * @return a Code instance that pretends to be this but ignores any attempt to add ops
         */
        public Code blackhole()
            {
            Code hole = m_hole;
            if (hole == null)
                {
                m_hole = hole = new BlackHole(this);
                }

            return hole;
            }

        // ----- read-only wrapper ------------------------------------------------------------

        /**
         * An implementation of Code that delegates most functionality to a "real" Code object, but
         * silently ignores any attempt to actually change the code.
         */
        static class BlackHole
                extends Code
            {
            BlackHole(Code wrappee)
                {
                super(wrappee.f_method, wrappee);
                f_wrappee = wrappee;
                }

            @Override
            public Code add(Op op)
                {
                return this;
                }

            @Override
            public Register lastRegister()
                {
                return f_wrappee.lastRegister();
                }

            @Override
            public int addressOf(Op op)
                {
                return f_wrappee.addressOf(op);
                }

            @Override
            public boolean usesSuper()
                {
                return false;
                }

            @Override
            public Op[] getAssembledOps()
                {
                throw new IllegalStateException();
                }

            @Override
            public Code blackhole()
                {
                return this;
                }

            Code f_wrappee;
            }

        // ----- internal ---------------------------------------------------------------------

        protected void ensureAppending()
            {
            if (f_method != null && f_method.m_abOps != null)
                {
                throw new IllegalStateException("not appendable");
                }

            if (m_listOps == null)
                {
                m_listOps =  new ArrayList<>();
                }
            }

        protected void registerConstants()
            {
            Op[] aop = ensureOps();
            if (aop != null)
                {
                Op.ConstantRegistry registry = new Op.ConstantRegistry(f_method.getConstantPool());
                for (Op op : aop)
                    {
                    op.registerConstants(registry);
                    }
                }
            }

        protected void ensureAssembled()
            {
            if (f_method.m_abOps == null)
                {
                // build the local constant array
                Op[] aop = ensureOps();

                // assemble the ops into bytes
                Scope scope = f_method.createInitialScope();
                Op.ConstantRegistry registry = new Op.ConstantRegistry(f_method.getConstantPool());
                ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
                DataOutputStream outData = new DataOutputStream(outBytes);
                try
                    {
                    for (int i = 0, c = aop.length; i < c; ++i)
                        {
                        Op op = aop[i];

                        op.resolveAddress(this, i);
                        op.simulate(scope);
                        op.write(outData, registry);
                        }
                    }
                catch (IOException e)
                    {
                    throw new IllegalStateException(e);
                    }

                f_method.m_abOps       = outBytes.toByteArray();
                f_method.m_aconstLocal = registry.getConstantArray();
                f_method.m_cVars       = scope.getMaxVars();
                f_method.m_cScopes     = scope.getMaxDepth();
                f_method.markModified();
                }
            }

        private Op[] ensureOps()
            {
            Op[] aop = m_aop;
            if (aop == null)
                {
                if (m_listOps == null)
                    {
                    throw new UnsupportedOperationException("Method \""
                            + f_method.getIdentityConstant().getPathString()
                            + "\" is neither native nor compiled");
                    }
                m_aop = aop = m_listOps.toArray(new Op[m_listOps.size()]);
                }
            return aop;
            }

        protected void calcVars()
            {
            if (f_method.m_cScopes == 0)
                {
                Scope scope = f_method.createInitialScope();

                for (Op op : getAssembledOps())
                    {
                    op.simulate(scope);
                    }

                f_method.m_cVars   = scope.getMaxVars();
                f_method.m_cScopes = scope.getMaxDepth();
                }
            }


        // ----- fields -----------------------------------------------------------------------

        private final MethodStructure f_method;

        /**
         * List of ops being assembled.
         */
        private ArrayList<Op> m_listOps;

        /**
         * Lookup of op address by op.
         */
        private IdentityHashMap<Op, Integer> m_mapIndex;

        /**
         * True iff the last op added was a prefix.
         */
        private boolean m_fTrailingPrefix;

        /**
         * The array of ops.
         */
        private Op[] m_aop;

        /**
         * A coding black hole.
         */
        private Code m_hole;

        /**
         * Previously advanced-to line number.
         */
        private int m_nPrevLine;

        /**
         * Current line number.
         */
        private int m_nCurLine;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of Parameters.
     */
    public static final Parameter[] NO_PARAMS = Parameter.NO_PARAMS;

    /**
     * Empty array of Ops.
     */
    public static final Op[] NO_OPS = new Op[0];

    /**
     * The method annotations.
     */
    private Annotation[] m_aAnnotations;

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
    transient Constant[] m_aconstLocal;

    /**
     * True iff the method has been marked as "abstract".
     */
    private boolean m_fAbstract;

    /**
     * The yet-to-be-deserialized ops.
     */
    transient byte[] m_abOps;

    /**
     * The method's code (for assembling new code).
     */
    private transient Code m_code;

    /**
     * The max number of registers used by the method. Calculated from the ops.
     */
    transient int m_cVars;

    /**
     * The max number of scopes used by the method. Calculated from the ops.
     */
    transient int m_cScopes;

    /**
     * True iff the method has been marked as "native". This is not part of the persistent method
     * structure; it exists only to support the prototype interpreter implementation.
     */
    private transient boolean m_fNative;

    /**
     * Cached information about whether this method uses its super.
     */
    private transient Boolean m_FUsesSuper;

    /**
     * Cached method for the construct-finally that goes with this method, iff this method is a
     * construct function that has a finally.
     */
    private transient MethodStructure m_structFinally;
    }
