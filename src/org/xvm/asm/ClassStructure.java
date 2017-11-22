package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.ListMap;


/**
 * An XVM Structure that represents an entire Class. This is also the base class for module and
 * package structures.
 */
public class ClassStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module, Package, or Class
     * @param condition  the optional condition for this ClassStructure
     */
    protected ClassStructure(XvmStructure xsParent, int nFlags, IdentityConstant constId,
                             ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this class is a singleton
     */
    public boolean isSingleton()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case ENUMVALUE:
                // these types are always singletons
                return true;

            case INTERFACE:
            case CLASS:
            case ENUM:
            case MIXIN:
            case TRAIT:
                // these types are never singletons
                return false;

            case CONST:
            case SERVICE:
                // these COULD be singletons (if they are static and NOT an inner class)
                if (isStatic())
                    {
                    Format format = getParent().getFormat();
                    return format == Format.MODULE || format == Format.PACKAGE;
                    }
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Obtain the type parameters for the class as an ordered read-only map, keyed by name and with
     * a corresponding value of the type constraint for the parameter.
     *
     * @return a read-only map of type parameter name to type
     */
    public Map<StringConstant, TypeConstant> getTypeParams()
        {
        Map<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            return Collections.EMPTY_MAP;
            }
        assert (map = Collections.unmodifiableMap(map)) != null;
        return map;
        }

    /**
     * Obtain the type parameters for the class as a list of map entries from name to type.
     *
     * @return a read-only list of map entries from type parameter name to type
     */
    public List<Map.Entry<StringConstant, TypeConstant>> getTypeParamsAsList()
        {
        final ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.isEmpty())
            {
            return Collections.EMPTY_LIST;
            }

        List<Map.Entry<StringConstant, TypeConstant>> list = map.asList();
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    /**
     * Add a type parameter.
     *
     * @param sName  the type parameter name
     * @param clz    the type parameter type
     */
    public void addTypeParam(String sName, TypeConstant clz)
        {
        ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        map.put(getConstantPool().ensureStringConstant(sName), clz);
        markModified();
        }

    /**
     * Obtain all of the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param access   the minimum accessibility that matching methods must have
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> getOpMethods(Access access, String sName, String sOp, int cParams)
        {
        assert access != null && access != Access.STRUCT;
        assert sName != null && sName.length() > 0;
        assert sOp != null && sOp.length() > 0;
        assert cParams >= 0;

        Set<MethodConstant> setOps = null;
        for (MethodConstant method : getMethods(access))
            {
            // method must be decorated with @Op
            // method name must match, OR the @Op parameter must match sOp
            // method must take the specified number of parameters
            if (method.get
            //method.getRawParams().length == cParams
            //method.getName().equals(sName))
            }
        new HashSet<>(7);
        collectOpMethods(setOps, access, sName, sOp, cParams);
        return setOps;
        }

    /**
     * For this class, find all of the ops that match the name and/or the operator string that take
     * the specified number of params.
     *
     * @param setOps   the set to contribute to
     * @param access   the minimum accessibility that matching methods must have
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     */
    protected void collectOpMethods(Set<MethodConstant> setOps, Access access, String sName, String sOp, int cParams)
        {
        getUnderlyingType().collectOpMethods(setOps, access, sName, sOp, cParams);
        }

    /**
     * Obtain all of the auto conversion methods found on this class.
     *
     * @param access   the minimum accessibility that matching methods must have
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> getAutoMethods(Access access)
        {
        assert access != null && access != Access.STRUCT;

        Set<MethodConstant> setAuto = new HashSet<>(7);
        collectAutoMethods(setAuto, access);
        return setAuto;
        }

    /**
     * For this class, find all of the auto conversion methods.
     *
     * @param setAuto  the set to contribute to
     * @param access   the minimum accessibility that matching methods must have
     */
    protected void collectAutoMethods(Set<MethodConstant> setAuto, Access access)
        {
        getUnderlyingType().collectAutoMethods(setAuto, access);
        }

    public Set<MethodConstant> getMethods(Access access)
        {

        }


    // ----- component methods ---------------------------------------------------------------------

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


    // ----- XvmStructure methods ------------------------------------------------------------------

    /**
     * Test for sub-classing.
     *
     * @param constClass  the class to test if this type represents an extension of
     *
     * @return true if this type represents a sub-classing of the specified class
     */
    public boolean extendsClass(IdentityConstant constClass)
        {
        if (constClass.equals(getConstantPool().clzObject()))
            {
            // everything is considered to extend Object (even interfaces)
            return true;
            }

        if (getFormat() == Format.INTERFACE)
            {
            // interfaces do not extend; they implement
            return false;
            }

        if (constClass.equals(getIdentityConstant()))
            {
            // while a class cannot technically extend itself, this does satisfy the "is-a" test
            return true;
            }

        ClassStructure structCur = this;
        NextSuper: while (true)
            {
            for (Contribution contrib : structCur.getContributionsAsList())
                {
                if (contrib.getComposition() == Composition.Extends)
                    {
                    // even though this class may be id'd using a ModuleConstant or PackageConstant,
                    // the super will always be a class (because a Module and a Package cannot be
                    // extended)
                    ClassConstant constSuper = (ClassConstant) contrib.getTypeConstant().getSingleUnderlyingClass();
                    if (constClass.equals(constSuper))
                        {
                        return true;
                        }

                    structCur = (ClassStructure) constSuper.getComponent();
                    continue NextSuper;
                    }
                }

            return false;
            }
        }

// TODO need a more generic test that checks class assignability in general
//            switch (contrib.getComposition())
//                {
//                case Extends:
//                    // even though this class may be id'd using a ModuleConstant or PackageConstant, the
//                    // super will always be a class (because a Module and a Package cannot be extended)
//                    ClassConstant constSuper = (ClassConstant) contrib.getTypeConstant().getSingleUnderlyingClass();
//                    if (constClass.equals(constSuper))
//
//                case Enumerates:
//
//                case Annotation:
//                case Incorporates:
//
//                case Into:
//                    // this is a mixin; if the type that this mixin mixes into is a class, then any
//                    // instance of this mixin will extend the specified class
//                }

    /**
     * Test for fake sub-classing (impersonation).
     *
     * @param constClass  the class to test if this type represents an impersonation of
     *
     * @return true if this type represents a fake sub-classing of the specified class
     */
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        // TODO - in progress
        return false;
        }

    /**
     * Test for real (extends) or fake (impersonation) sub-classing.
     *
     * @param constClass  the class to test if this type represents a sub-class of
     *
     * @return true if this type represents either real or fake sub-classing of the specified class
     */
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        // TODO - in progress
        return false;
        }

    /**
     * Recursively find a contribution by the specified id.
     *
     * @param constId  the identity to look for
     * @return  the resulting contribution or null if none found
     */
    public Contribution findContribution(IdentityConstant constId)
        {
        if (constId.equals(getConstantPool().clzObject()))
            {
            // everything is considered to extend Object (even interfaces)
            return new Contribution(Composition.Extends, getConstantPool().typeObject());
            }

        if (constId.equals(getIdentityConstant()))
            {
            return new Contribution(Composition.Equal, (TypeConstant) null);
            }

        ClassStructure structCur = this;

        for (Contribution contrib : structCur.getContributionsAsList())
            {
            Constant constContrib = contrib.getTypeConstant().getDefiningConstant();
            if (constContrib.equals(constId))
                {
                return contrib;
                }

            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                case Into:
                    // TODO:
                    break;

                case Incorporates:
                case Implements:
                case Extends:
                    // even though this class may be a ModuleConstant or PackageConstant,
                    // the super will always be a class (because a Module and a Package cannot be
                    // extended)
                    ClassConstant constSuper = (ClassConstant) constContrib;
                    contrib = ((ClassStructure) constSuper.getComponent()).findContribution(constId);
                    if (contrib != null)
                        {
                        // return as soon as a match is found
                        // TODO: wrap it into a "DeepContribution" of this IdentityConstant,
                        return contrib;
                        }
                    break;

                case Enumerates:
                case Impersonates:
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        return null;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // read in the type parameters
        m_mapParams = disassembleTypeParams(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // register the type parameters
        m_mapParams = registerTypeParams(m_mapParams);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // write out the type parameters
        assembleTypeParams(m_mapParams, out);
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<StringConstant, TypeConstant> entry : map.entrySet())
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(entry.getKey().getValue());

                TypeConstant constType = entry.getValue();
                if (!constType.isEcstasy("Object"))
                    {
                    sb.append(" extends ")
                      .append(constType);
                    }
                }
            sb.append('>');
            }

        return sb.toString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure) || !super.equals(obj))
            {
            return false;
            }

        ClassStructure that = (ClassStructure) obj;

        // type parameters
        final Map mapThisParams = this.m_mapParams;
        final Map mapThatParams = that.m_mapParams;
        final int cThisParams = mapThisParams == null ? 0 : mapThisParams.size();
        final int cThatParams = mapThatParams == null ? 0 : mapThatParams.size();
        if (cThisParams != cThatParams || (cThisParams > 0 && !mapThisParams.equals(mapThatParams)))
            {
            return  false;
            }

        return true;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<StringConstant, TypeConstant> m_mapParams;
    }
