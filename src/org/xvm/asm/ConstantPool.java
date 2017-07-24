package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;

import org.xvm.asm.Constant.Format;

import org.xvm.asm.constants.*;

import org.xvm.util.PackedInteger;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A shared pool of all Constant objects used in a particular FileStructure.
 *
 * @author cp  2015.12.04
 */
public class ConstantPool
        extends XvmStructure
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ConstantPool.
     *
     * @param fileStructure  the FileStructure that contains this ConstantPool
     */
    public ConstantPool(FileStructure fileStructure)
        {
        super(fileStructure);
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * Obtain the Constant that is currently stored at the specified index. A runtime exception will
     * occur if the index is invalid.
     *
     * @param i  the index, for example obtained during the disassembly process
     *
     * @return the Constant at that index
     */
    public Constant getConstant(int i)
        {
        return i == -1 ? null : m_listConst.get(i);
        }

    /**
     * Register a Constant. This is used when a new Constant is created by the ConstantPool, but it
     * can also be used directly by a consumer, and it's used during the bulk (re-)registration of
     * Constants by the {@link XvmStructure#registerConstants} method of all of the various parts
     * of the FileStructure.
     * <p/>
     * The caller should use the returned constant in lieu of the constant that the caller passed
     * in.
     *
     * @param constant  the Constant to register
     *
     * @return if the passed Constant was not previously registered, then it is returned; otherwise,
     *         the previously registered Constant (which should be used in lieu of the passed
     *         Constant) is returned
     */
    public Constant register(Constant constant)
        {
        // to allow this method to be used blindly, i.e. for constants that may be optional within a
        // given structure, simply pass back null refs
        if (constant == null)
            {
            return null;
            }

        // if the constant is resolvable, and has been resolved, then use the resolved constant
        if (constant instanceof ResolvableConstant)
            {
            Constant resolved = ((ResolvableConstant) constant).getResolvedConstant();
            if (resolved == null)
                {
                // resolvable constants are not themselves registered
                return null;
                }
            else
                {
                constant = resolved;
                }
            }

        // check if the Constant is already registered
        final HashMap<Constant, Constant> mapConstants = ensureConstantLookup(constant.getFormat());
        final Constant constantOld = mapConstants.get(constant);
        if (constantOld == null)
            {
            if (constant.getContaining() != this)
                {
                throw new UnsupportedOperationException("need to clone constant into this pool?");
                }

            // add the Constant
            constant.setPosition(m_listConst.size());
            m_listConst.add(constant);
            mapConstants.put(constant, constant);

            // also allow the constant to be looked up by a locator
            Object oLocator = constant.getLocator();
            if (oLocator != null)
                {
                ensureLocatorLookup(constant.getFormat()).put(oLocator, constant);
                }

            // make sure that the recursively referenced constants are all
            // registered (and that they are aware of their being referenced)
            constant.registerConstants(this);
            }
        else
            {
            constant = constantOld;
            }

        if (m_fRecurseReg)
            {
            final boolean fDidHaveRefs = constant.hasRefs();
            constant.addRef();
            if (!fDidHaveRefs)
                {
                // first time to register this constant; recursively register
                // any constants that it refers to
                constant.registerConstants(this);
                }
            }

        return constant;
        }

    /**
     * Given the specified byte value, obtain a ByteConstant that represents it.
     *
     * @param b  the byte value
     *
     * @return a ByteConstant for the passed byte value
     */
    public ByteConstant ensureByteConstant(int b)
        {
        // check the pre-existing constants first
        ByteConstant constant = (ByteConstant) ensureLocatorLookup(Format.Byte).get(Byte.valueOf((byte) b));
        if (constant == null)
            {
            constant = (ByteConstant) register(new ByteConstant(this, b));
            }
        return constant;
        }

    /**
     * Given the specified byte array value, obtain a ByteStringConstant that represents it.
     *
     * @param ab  the byte array value
     *
     * @return a ByteStringConstant for the passed byte array value
     */
    public ByteStringConstant ensureByteStringConstant(byte[] ab)
        {
        ByteStringConstant constant = new ByteStringConstant(this, ab.clone());
        return (ByteStringConstant) register(constant);
        }

    /**
     * Given the specified character value, obtain a CharConstant that represents it.
     *
     * @param ch  the character value
     *
     * @return a CharConstant for the passed character value
     */
    public CharConstant ensureCharConstant(int ch)
        {
        // check the cache
        if (ch <= 0x7F)
            {
            CharConstant constant = (CharConstant) ensureLocatorLookup(Format.Char).get(Character.valueOf((char) ch));
            if (constant != null)
                {
                return constant;
                }
            }

        return (CharConstant) register(new CharConstant(this, ch));
        }

    /**
     * Given the specified String value, obtain a CharStringConstant that represents it.
     *
     * @param s  the String value
     *
     * @return a CharStringConstant for the passed String value
     */
    public CharStringConstant ensureCharStringConstant(String s)
        {
        // check the pre-existing constants first
        CharStringConstant constant = (CharStringConstant) ensureLocatorLookup(Format.CharString).get(s);
        if (constant == null)
            {
            constant = (CharStringConstant) register(new CharStringConstant(this, s));
            }
        return constant;
        }

    /**
     * Given the specified {@code long} value, obtain a IntConstant that represents it.
     *
     * @param n  the {@code long} value of the integer
     *
     * @return an IntConstant for the passed {@code long} value
     */
    public IntConstant ensureIntConstant(long n)
        {
        return ensureIntConstant(PackedInteger.valueOf(n));
        }

    /**
     * Given the specified PackedInteger value, obtain a IntConstant that represents it.
     *
     * @param pint  the PackedInteger value
     *
     * @return an IntConstant for the passed PackedInteger value
     */
    public IntConstant ensureIntConstant(PackedInteger pint)
        {
        // check the pre-existing constants first
        IntConstant constant = (IntConstant) ensureLocatorLookup(Format.Int).get(pint);
        if (constant == null)
            {
            constant = (IntConstant) register(new IntConstant(this, pint));
            }
        return constant;
        }

    /**
     * Given the specified Version value, obtain a VersionConstant that represents it.
     *
     * @param ver  a version
     *
     * @return a VersionConstant for the passed version value
     */
    public VersionConstant ensureVersionConstant(Version ver)
        {
        VersionConstant constant = (VersionConstant) ensureLocatorLookup(Format.Version).get(ver.toString());
        if (constant == null)
            {
            constant = new VersionConstant(this, ver);
            }
        return constant;
        }

    /**
     * Given an array of constants, obtain a TupleConstant that represents the corresponding tuple.
     *
     * @param aconst  an array of conditions
     *
     * @return a TupleConstant
     */
    public TupleConstant ensureTupleConstant(Constant[] aconst)
        {
        checkElementsNonNull(aconst);

        return (TupleConstant) register(new TupleConstant(this, aconst.clone()));
        }

    /**
     * Given the specified name, obtain a NamedCondition that represents a test for the name being
     * specified.
     *
     * @param sName  a name
     *
     * @return a NamedCondition
     */
    public NamedCondition ensureNamedCondition(String sName)
        {
        NamedCondition cond = (NamedCondition) ensureLocatorLookup(Format.ConditionNamed).get(sName);
        if (cond == null)
            {
            cond = new NamedCondition(this, ensureCharStringConstant(sName));
            }
        return cond;
        }

    /**
     * Given a constant for a particular XVM structure, obtain a PresentCondition that represents a
     * test for the structure's existence.
     *
     * @param constId   a constant specifying a particular XVM structure
     *
     * @return a PresentCondition
     */
    public PresentCondition ensurePresentCondition(IdentityConstant constId)
        {
        PresentCondition cond = (PresentCondition) ensureLocatorLookup(Format.ConditionPresent).get(constId);
        if (cond == null)
            {
            cond = new PresentCondition(this, constId);
            }
        return cond;
        }

    /**
     * Given a constant for a particular XVM structure and version, obtain a PresentCondition that
     * represents a test for the structure's existence of the specified version.
     *
     * @param constModule  the constant specifying the module
     * @param constVer     the version of that module to test for
     *
     * @return a VersionMatchesCondition
     */
    public VersionMatchesCondition ensureImportVersionCondition(ModuleConstant constModule, VersionConstant constVer)
        {
        return new VersionMatchesCondition(this, constModule, constVer);
        }

    /**
     * Given a version , obtain a VersionedCondition that represents a test for that version
     * of this module.
     *
     * @param ver  the version of this module to test for
     *
     * @return a VersionedCondition
     */
    public VersionedCondition ensureVersionedCondition(Version ver)
        {
        return ensureVersionedCondition(ensureVersionConstant(ver));
        }

    /**
     * Given a version constant, obtain a VersionedCondition that represents a test for that version
     * of this module.
     *
     * @param constVer  the version of this module to test for
     *
     * @return a VersionedCondition
     */
    public VersionedCondition ensureVersionedCondition(VersionConstant constVer)
        {
        VersionedCondition cond = (VersionedCondition) ensureLocatorLookup(Format.ConditionVersioned).get(constVer);
        if (cond == null)
            {
            cond = new VersionedCondition(this, constVer);
            }
        return cond;
        }

    /**
     * Given a condition, obtain the inverse of that condition.
     *
     * @param cond   a condition
     *
     * @return a Condition representing the inverse
     */
    public ConditionalConstant ensureNotCondition(ConditionalConstant cond)
        {
        // avoid double-negatives
        if (cond instanceof NotCondition)
            {
            return ((NotCondition) cond).getUnderlyingCondition();
            }

        NotCondition condNot = (NotCondition) ensureLocatorLookup(Format.ConditionNot).get(cond);
        if (condNot == null)
            {
            condNot = new NotCondition(this, cond);
            }
        return condNot;
        }


    /**
     * Given the multiple conditions, obtain an AnyCondition that represents them.
     *
     * @param aCondition  an array of conditions
     *
     * @return an AnyCondition
     */
    public AnyCondition ensureAnyCondition(ConditionalConstant... aCondition)
        {
        checkElementsNonNull(aCondition);
        if (aCondition.length < 2)
            {
            throw new IllegalArgumentException("at least 2 conditions required");
            }

        return (AnyCondition) register(new AnyCondition(this, aCondition));
        }

    /**
     * Given the multiple conditions, obtain an AllCondition that represents them.
     *
     * @param aCondition  an array of conditions
     *
     * @return an AllCondition
     */
    public AllCondition ensureAllCondition(ConditionalConstant... aCondition)
        {
        checkElementsNonNull(aCondition);
        if (aCondition.length < 2)
            {
            throw new IllegalArgumentException("at least 2 conditions required");
            }

        return (AllCondition) register(new AllCondition(this, aCondition));
        }

    /**
     * Obtain a Constant that represents the specified module.
     *
     * @param sName  a fully qualified module name
     *
     * @return the ModuleConstant for the specified qualified module name
     */
    public ModuleConstant ensureModuleConstant(String sName)
        {
        if (!isValidQualifiedModule(sName))
            {
            throw new IllegalArgumentException("illegal qualified module name: " + quotedString(sName));
            }

        ModuleConstant constant = (ModuleConstant) ensureLocatorLookup(Format.Module).get(sName);
        if (constant == null)
            {
            constant = (ModuleConstant) register(new ModuleConstant(this, sName));
            }
        return constant;
        }

    /**
     * Given the specified package name and the context (module or package) within which it exists,
     * obtain a PackageConstant that represents it.
     *
     * @param constParent  the ModuleConstant or PackageConstant that contains the specified package
     * @param sPackage     the unqualified name of the package
     *
     * @return the specified PackageConstant
     */
    public PackageConstant ensurePackageConstant(Constant constParent, String sPackage)
        {
        if (constParent == null)
            {
            throw new IllegalArgumentException("ModuleConstant or PackageConstant required");
            }

        // validate the package name
        if (!isValidIdentifier(sPackage))
            {
            throw new IllegalArgumentException("illegal package name: " + sPackage);
            }

        switch (constParent.getFormat())
            {
            case Module:
            case Package:
                return (PackageConstant) register(new PackageConstant(this, constParent, sPackage));

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module or Package");
            }
        }

    /**
     * Given the specified class name and the context (module, package, class, method) within which
     * it exists, obtain a ClassConstant that represents it.
     *
     * @param constParent
     * @param sClass
     *
     * @return
     */
    public ClassConstant ensureClassConstant(Constant constParent, String sClass)
        {
        switch (constParent.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Method:
                return (ClassConstant) register(new ClassConstant(this, constParent, sClass));

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, or Method");
            }
        }

    /**
     * Helper to get a ClassConstant for a class in the Ecstasy core module.
     *
     * @param sClass  the qualified class name, dot-delimited
     *
     * @return the specified ClassConstant referring to a class from the Ecstasy core module
     */
    public ClassConstant ensureEcstasyClassConstant(String sClass)
        {
        Constant constParent = ensureModuleConstant(ECSTASY_MODULE);
        int      ofStart     = 0;
        int      ofEnd       = sClass.indexOf('.');
        while (ofEnd >= 0)
            {
            String sName = sClass.substring(ofStart, ofEnd);
            constParent = constParent instanceof ClassConstant || sName.charAt(0) <= 'Z'
                    ? ensureClassConstant(constParent, sName)
                    : ensurePackageConstant(constParent, sName);
            ofStart = ofEnd + 1;
            ofEnd   = sClass.indexOf('.', ofStart);
            }
        return ensureClassConstant(constParent, sClass.substring(ofStart));
        }

    /**
     * This is the implementation of the "automatically imported" names that constitute the default
     * set of known names in the language. This implementation should correspond to the source file
     * "implicit.x".
     *
     * @param sName  the unqualified name to look up
     *
     * @return an IdentityConstant for, or null
     */
    public IdentityConstant ensureImplicitlyImportedIdentityConstant(String sName)
        {
        String sPkg = null;
        String sClz = null;
        String sSub = null;

        switch (sName)
            {
            case "Ecstasy":
            case "X":
                break;

            case "Boolean":
            case "Char":
            case "Class":
            case "Object":
            case "String":
            case "Type":
            case "Void":
                sClz = sName;
                break;

            case "Int":
                sClz = "Int64";
                break;

            case "Tuple":
                sPkg = "collections";
                sClz = sName;
                break;

            case "null":
                sClz = "Nullable";
                sSub = "Null";
                break;

            case "true":
                sClz = "Boolean";
                sSub = "True";
                break;

            case "false":
                sClz = "Boolean";
                sSub = "False";
                break;

            default:
                return null;
            }

        IdentityConstant constId = ensureModuleConstant(ECSTASY_MODULE);
        if (sPkg != null)
            {
            constId = ensurePackageConstant(constId, sPkg);
            }
        if (sClz != null)
            {
            constId = ensureClassConstant(constId, sClz);
            if (sSub != null)
                {
                constId = ensureClassConstant(constId, sSub);
                }
            }
        return constId;
        }

    /**
     * Given the specified property name and the context (module, package, class, method) within
     * which it exists, obtain a PropertyConstant that represents it.
     *
     * @param constParent  the constant representing the container of the property, for example a
     *                     ClassConstant
     * @param sName        the name of the property
     *
     * @return the specified PropertyConstant
     */
    public PropertyConstant ensurePropertyConstant(Constant constParent, String sName)
        {
        return (PropertyConstant) register(new PropertyConstant(this, constParent, sName));
        }

    /**
     * Given the specified method name and the context (module, package, class, property, method)
     * within which it exists, obtain a MultiMethodConstant that represents it.
     *
     * @param constParent  the constant representing the container of the multi-method, for example
     *                     a ClassConstant
     * @param sName        the name of the multi-method
     *
     * @return the specified MultiMethodConstant
     */
    public MultiMethodConstant ensureMultiMethodConstant(Constant constParent, String sName)
        {
        return (MultiMethodConstant) register(new MultiMethodConstant(this, constParent, sName));
        }

    /**
     * Obtain a Constant that represents the specified method.
     *
     * @param constParent    specifies the module, package, class, multi-method, method, or property
     *                       that contains the method
     * @param sName          the method name
     * @param access         the method accessibility
     * @param aconstReturns  the return values from the method
     * @param aconstParams   the invocation parameters for the method
     *
     * @return the MethodConstant for the specified method name of the specified container with the
     *         specified parameters and return values
     */
    public MethodConstant ensureMethodConstant(Constant constParent, String sName, Access access,
            TypeConstant[] aconstReturns, TypeConstant[] aconstParams)
        {
        assert constParent != null;

        MultiMethodConstant constMultiMethod;
        switch (constParent.getFormat())
            {
            case MultiMethod:
                constMultiMethod = (MultiMethodConstant) constParent;
                break;

            case Module:
            case Package:
            case Class:
            case Method:
            case Property:
                constMultiMethod = ensureMultiMethodConstant(constParent, sName);
                break;

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, Method, or Property");
            }

        return (MethodConstant) register(new MethodConstant(this, constMultiMethod, access,
                aconstReturns, aconstParams));
        }

    /**
     * Given the specified class, access, and optional type parameters, obtain a ClassTypeConstant
     * that represents that combination.
     *
     * @param constClass  a ModuleConstant, PackageConstant, or ClassConstant
     * @param access      the access level
     * @param constTypes  the optional type parameters
     *
     * @return a ClassTypeConstant
     */
    public ClassTypeConstant ensureClassTypeConstant(IdentityConstant constClass,
                                                     Access access, TypeConstant... constTypes)
        {
        if (constClass instanceof ModuleConstant
                || constClass instanceof PackageConstant
                || constClass instanceof ClassConstant)
            {
            ClassTypeConstant constant = null;
            if (constClass.getFormat() == Format.Class && access == Access.PUBLIC && constTypes == null)
                {
                constant = (ClassTypeConstant) ensureLocatorLookup(Format.ClassType).get(constClass);
                }
            if (constant == null)
                {
                constant = (ClassTypeConstant) register(new ClassTypeConstant(this, constClass, access, constTypes));
                }
            return constant;
            }

        throw new IllegalArgumentException("constant " + constClass.getFormat()
                + " is not a Module, Package, or Class");
        }

    /**
     * TODO
     *
     * @param constType
     *
     * @return
     */
    public ImmutableTypeConstant ensureImmutableTypeConstant(TypeConstant constType)
        {
        ImmutableTypeConstant constant;
        if (constType instanceof ImmutableTypeConstant)
            {
            constant = (ImmutableTypeConstant) constType;
            }
        else
            {
            constant = (ImmutableTypeConstant) ensureLocatorLookup(Format.ImmutableType).get(constType);
            if (constant != null)
                {
                return constant;
                }

            constant = new ImmutableTypeConstant(this, constType);
            }

        return (ImmutableTypeConstant) register(constant);
        }

    /**
     * TODO
     *
     * @param constType1
     * @param constType2
     *
     * @return
     */
    public IntersectionTypeConstant ensureIntersectionTypeConstant(TypeConstant constType1, TypeConstant constType2)
        {
        return (IntersectionTypeConstant) register(new IntersectionTypeConstant(this, constType1, constType2));
        }

    /**
     * TODO
     *
     * @param constType1
     * @param constType2
     *
     * @return
     */
    public UnionTypeConstant ensureUnionTypeConstant(TypeConstant constType1, TypeConstant constType2)
        {
        return (UnionTypeConstant) register(new UnionTypeConstant(this, constType1, constType2));
        }

    /**
     * Create a new TypeConstant whose exact type will eventually be resolved.
     *
     * @param oType  some opaque representation of the type
     *
     * @return a new TypeConstant
     */
    public UnresolvedTypeConstant createUnresolvedTypeConstant(Object oType)
        {
        return new UnresolvedTypeConstant(this, oType);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public ConstantPool getConstantPool()
        {
        return this;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return m_listConst.iterator();
        }

    @Override
    public boolean isModified()
        {
        // changes to the constant pool only modify the resulting file if there are changes to other
        // structures that reference the changes in the constant pool; the constants themselves are
        // constant
        return false;
        }

    @Override
    protected void markModified()
        {
        }

    @Override
    protected void resetModified()
        {
        }

    @Override
    public boolean isConditional()
        {
        return false;
        }

    @Override
    public void purgeCondition(ConditionalConstant condition)
        {
        }

    @Override
    public boolean isPresent(LinkerContext ctx)
        {
        return true;
        }

    @Override
    public boolean isResolved()
        {
        return true;
        }

    @Override
    public void resolve(LinkerContext ctx)
        {
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_listConst.clear();
        m_mapConstants.clear();
        m_mapLocators.clear();

        // read the number of constants in the pool
        int cConst = readMagnitude(in);
        m_listConst.ensureCapacity(cConst);

        // load the constant pool from the stream
        for (int i = 0; i < cConst; ++i)
            {
            Constant constant;
            int      nFmt   = in.readUnsignedByte();
            Format   format = Constant.Format.valueOf(nFmt);
            switch (format)
                {
                case Byte:
                    constant = new ByteConstant(this, format, in);
                    break;

                case ByteString:
                    constant = new ByteStringConstant(this, format, in);
                    break;

                case Char:
                    constant = new CharConstant(this, format, in);
                    break;

                case CharString:
                    constant = new CharStringConstant(this, format, in);
                    break;

                case Int:
                    constant = new IntConstant(this, format, in);
                    break;

                case Tuple:
                    constant = new TupleConstant(this, format, in);
                    break;

                case Module:
                    constant = new ModuleConstant(this, format, in);
                    break;

                case Package:
                    constant = new PackageConstant(this, format, in);
                    break;

                case Class:
                    constant = new ClassConstant(this, format, in);
                    break;

                case Property:
                    constant = new PropertyConstant(this, format, in);
                    break;

                case MultiMethod:
                    constant = new MultiMethodConstant(this, format, in);
                    break;

                case Method:
                    constant = new MethodConstant(this, format, in);
                    break;

                case Version:
                    constant = new VersionConstant(this, format, in);
                    break;

                case ConditionNot:
                    constant = new NotCondition(this, format, in);
                    break;

                case ConditionAll:
                    constant = new AllCondition(this, format, in);
                    break;

                case ConditionAny:
                    constant = new AnyCondition(this, format, in);
                    break;

                case ConditionNamed:
                    constant = new NamedCondition(this, format, in);
                    break;

                case ConditionPresent:
                    constant = new PresentCondition(this, format, in);
                    break;

                case ConditionVersionMatches:
                    constant = new VersionMatchesCondition(this, format, in);
                    break;

                case ConditionVersioned:
                    constant = new VersionedCondition(this, format, in);
                    break;

                case ClassType:
                    constant = new ClassTypeConstant(this, format, in);
                    break;

                case ImmutableType:
                    constant = new ImmutableTypeConstant(this, format, in);
                    break;

                case UnionType:
                    constant = new UnionTypeConstant(this, format, in);
                    break;

                case IntersectionType:
                    constant = new IntersectionTypeConstant(this, format, in);
                    break;

                case AnnotatedType:
                    constant = new AnnotatedTypeConstant(this, format, in);
                    break;

                case Unresolved:
                default:
                    throw new IOException("Unsupported constant format: " + nFmt);
                }

            constant.setPosition(i);
            m_listConst.add(constant);
            }

        // convert indexes into constant references
        for (Constant constant : m_listConst)
            {
            constant.disassemble(null);
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        // the ConstantPool does contain constants, but it does not itself reference any constants,
        // so it has nothing to register itself. furthermore, this must be over-ridden here to avoid
        // the super implementation calling to each of the contained Constants (some of which may no
        // longer be referenced by any XVM Structure) and having them accidentally register
        // everything that they in turn depend upon
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_listConst.size());
        for (Constant constant : m_listConst)
            {
            constant.assemble(out);
            }
        }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "size=" + m_listConst.size() + ", recurse-reg=" + m_fRecurseReg;
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        dumpStructureCollection(out, sIndent, "Constants", m_listConst);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ConstantPool))
            {
            return false;
            }

        ConstantPool that = (ConstantPool) obj;

        // compare each constant in the pool for equality
        return this.m_listConst.equals(that.m_listConst);
        }


    // ----- methods exposed to FileStructure ------------------------------------------------------

    /**
     * Before the registration of constants begins as part of assembling the FileStructure, the
     * ConstantPool is notified of the impending assembly process so that it can determine which
     * constants are actually used, and how many times each is used. This is important because the
     * unused constants can be discarded, and the most frequently used constants can be written out
     * first in the ConstantPool, allowing their ordinal position to be addressed using a smaller
     * number of bytes throughout the FileStructure.
     */
    protected void preRegisterAll()
        {
        assert !m_fRecurseReg;
        m_fRecurseReg = true;

        for (Constant constant : m_listConst)
            {
            constant.resetRefs();
            }
        }

    /**
     * Called after all of the Constants have been registered by the bulk registration process.
     *
     * @param fOptimize pass true to optimize the order of the constants, or false to maintain the
     *                  present order
     */
    protected void postRegisterAll(final boolean fOptimize)
        {
        assert m_fRecurseReg;
        m_fRecurseReg = false;

        if (fOptimize)
            {
            optimize();
            }
        }

    /**
     * Discard unused Constants and order the remaining constants so that the most-referred-to
     * Constants occur before the less used constants.
     */
    private void optimize()
        {
        // sort the Constants by how often they are referred to within the FileStructure, with the
        // most frequently referred-to Constants appearing first
        m_listConst.sort(Constant.MFU_ORDER);

        // go through and mark each constant with its new position; the iteration is backwards to
        // support the efficient removal of all of the unused Constants from the end of the list
        for (int i = m_listConst.size() - 1; i >= 0; --i)
            {
            Constant constant = m_listConst.get(i);
            if (constant.hasRefs())
                {
                if (i != constant.getPosition())
                    {
                    constant.setPosition(i);
                    }
                }
            else
                {
                constant.setPosition(-1);
                m_listConst.remove(i);
                }
            }

        // discard any previous lookup structures, since contents may have changed
        m_mapConstants.clear();
        m_mapLocators.clear();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using Constants as the
     * keys of the lookup table.
     * <p/>
     * Constants are natural identities, so they act as the keys in this lookup structure. This data
     * structure allows there to be exactly one instance of each Constant identity held by the
     * ConstantPool, similar to how String objects are "interned" in Java.
     *
     * @param format  the Constant Type
     *
     * @return the map from Constant to Constant
     */
    private HashMap<Constant, Constant> ensureConstantLookup(Format format)
        {
        ensureLookup();
        return m_mapConstants.get(format);
        }

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using locators as the
     * keys of the lookup table.
     * <p/>
     * Locators are optional identities that are specific to each different Type of Constant:
     * <ul>
     * <li>A Constant Type may not support locators at all;</li>
     * <li>A Constant Type may support locators, but only for some of the
     * Constant values of that Type;</li>
     * <li>A Constant Type may support locators for all of the Constant values
     * of that Type.</li>
     * </ul>
     *
     * @param format  the Constant Type
     *
     * @return the map from locator to Constant
     */
    private HashMap<Object, Constant> ensureLocatorLookup(Format format)
        {
        final EnumMap<Format, HashMap<Object, Constant>> mapLocatorMaps = m_mapLocators;

        HashMap<Object, Constant> mapLocators = mapLocatorMaps.get(format);
        if (mapLocators == null)
            {
            // lazily instantiate the locator map for the specified type
            mapLocators = new HashMap<>();
            mapLocatorMaps.put(format, mapLocators);
            }

        return mapLocators;
        }

    /**
     * Create the necessary structures for looking up Constant objects quickly, and populate those
     * structures with the set of existing Constant objects.
     */
    private void ensureLookup()
        {
        if (m_mapConstants.isEmpty())
            {
            for (Format format : Format.values())
                {
                m_mapConstants.put(format, new HashMap<>());
                }

            for (Constant constant : m_listConst)
                {
                Constant constantOld = m_mapConstants.get(constant.getFormat()).put(constant, constant);
                assert constantOld == null;

                Object oLocator = constant.getLocator();
                if (oLocator != null)
                    {
                    constantOld = ensureLocatorLookup(constant.getFormat()).put(oLocator, constant);
                    assert constantOld == null;
                    }
                }
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of types.
     */
    public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

    /**
     * Storage of Constant objects by index.
     */
    private final ArrayList<Constant> m_listConst = new ArrayList<>();

    /**
     * Reverse lookup structure to find a particular constant by constant.
     */
    private final EnumMap<Format, HashMap<Constant, Constant>> m_mapConstants = new EnumMap<>(Format.class);

    /**
     * Reverse lookup structure to find a particular constant by locator.
     */
    private final EnumMap<Format, HashMap<Object, Constant>> m_mapLocators = new EnumMap<>(Format.class);

    /**
     * Tracks whether the ConstantPool should recursively register constants.
     */
    private transient boolean m_fRecurseReg;
    }
