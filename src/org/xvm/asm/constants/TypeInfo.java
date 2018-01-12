package org.xvm.asm.constants;


import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.asm.constants.ParamInfo.TypeResolver;

import org.xvm.util.ListMap;


/**
 * Represents the "flattened" information about the type.
 */
public class TypeInfo
    {
    /**
     * Construct a TypeInfo.
     *
     * @param type                 the type that the TypeInfo represents
     * @param format               the Format of the type; Interface is the "type" catch-all
     * @param mapTypeParams        the collected type parameters for the type
     * @param typeExtends          the type that is extended
     * @param typeRebases          the type that is rebased onto
     * @param typeInto             for mixins, the type that is mixed into; for interfaces, Object
     * @param listmapClassChain    the potential call chain of classes
     * @param listmapDefaultChain  the potential call chain of default implementations
     * @param mapProperties        the properties of the type
     * @param mapMethods           the methods of the type
     */
    public TypeInfo(TypeConstant type, Format format, Map<String, ParamInfo> mapTypeParams,
            TypeConstant typeExtends, TypeConstant typeRebases, TypeConstant typeInto,
            ListMap<IdentityConstant, Boolean> listmapClassChain,
            ListMap<IdentityConstant, Boolean> listmapDefaultChain,
            Map<String, PropertyInfo> mapProperties, Map<SignatureConstant, MethodInfo> mapMethods)
        {
        assert type != null;
        assert mapTypeParams != null;
        assert listmapClassChain != null;
        assert listmapDefaultChain != null;
        assert mapProperties != null;
        assert mapMethods != null;

        m_type                  = type;
        m_format                = format;
        m_mapTypeParams         = mapTypeParams;
        m_typeExtends           = typeExtends;
        m_typeRebases           = typeRebases;
        m_typeInto              = typeInto;
        m_listmapClassChain     = listmapClassChain;
        m_listmapDefaultChain   = listmapDefaultChain;
        m_mapProperties         = mapProperties;
        m_mapMethods            = mapMethods;
        }

    /**
     * Obtain a type resolver that uses the information from this type's type parameters.
     *
     * @param errs  the error list to log any errors to
     *
     * @return a GenericTypeResolver
     */
    public GenericTypeResolver ensureTypeResolver(ErrorListener errs)
        {
        assert errs != null;

        TypeResolver resolver = m_resolver;
        if (resolver == null || resolver.errs != errs)
            {
            m_resolver = resolver = new TypeResolver(m_mapTypeParams, errs);
            }
        return resolver;
        }

    /**
     * Contribute this TypeInfo's knowledge of potential call chain information to another deriving
     * type's TypeInfo information.
     *
     * @param listmapClassChain    the class chain being collected for the derivative type
     * @param listmapDefaultChain  the default chain being collected for the derivative type
     * @param fAnnotation          true iff this type is being used as an annotation in the derived
     *                             type
     */
    public void contributeChains(
            ListMap<IdentityConstant, Boolean> listmapClassChain,
            ListMap<IdentityConstant, Boolean> listmapDefaultChain,
            boolean fAnnotation)
        {
        for (Entry<IdentityConstant, Boolean> entry : m_listmapClassChain.entrySet())
            {
            IdentityConstant constId = entry.getKey();
            boolean          fYank   = entry.getValue();

            Boolean BAnchored = listmapClassChain.get(constId);
            if (BAnchored == null)
                {
                // the identity does not already appear in the chain, so add it to the chain
                listmapClassChain.put(constId, fAnnotation & fYank);
                }
            else if (!BAnchored)
                {
                // the identity in the chain is owned by this type, so remove it from its old
                // location in the chain, and add it to the end
                listmapClassChain.remove(constId);
                listmapClassChain.put(constId, fAnnotation & fYank);
                }
            // else ... the identity in the chain was "yanked" from us, so we can't claim it;
            // just leave it where it is in the chain
            }

        // append our defaults to the default chain (just the ones that are absent from the chain)
        for (IdentityConstant constId : m_listmapDefaultChain.keySet())
            {
            listmapDefaultChain.putIfAbsent(constId, true);
            }
        }

    /**
     * @return the type that the TypeInfo represents
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to, or
     *         {@code INTERFACE} for any non-class / non-mixin type (such as a difference type)
     */
    public Format getFormat()
        {
        return m_format;
        }

    /**
     * @return the complete set of type parameters declared within the type
     */
    public Map<String, ParamInfo> getTypeParams()
        {
        return m_mapTypeParams;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getRebases()
        {
        return m_typeRebases;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getExtends()
        {
        return m_typeExtends;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getInto()
        {
        return m_typeInto;
        }

    /**
     * @return all of the properties for this type
     */
    public Map<String, PropertyInfo> getProperties()
        {
        return m_mapProperties;
        }

    /**
     * @return all of the methods for this type
     */
    public Map<SignatureConstant, MethodInfo> getMethods()
        {
        return m_mapMethods;
        }

    /**
     * Obtain all of the methods that are annotated with "@Op".
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getOpMethodInfos()
        {
        Set<MethodInfo> setOps = m_setOps;
        if (setOps == null)
            {
            for (MethodInfo info : m_mapMethods.values())
                {
                if (info.isOp())
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info);
                    }
                }

            // cache the result
            m_setOps = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Given the specified method signature, find the most appropriate method that matches that
     * signature, and return that method. If there is no matching method, then return null. If
     * there are multiple methods that match, but it is ambiguous which method is "the best"
     * match, then log an error to the error list, and return null.
     *
     * @param constSig  the method signature to search for
     * @param errs      the error list to log errors to
     *
     * @return the MethodInfo for the method that is the "best match" for the signature, or null
     *         if no method is a best match (including the case in which more than one method
     *         matches, but no one of those methods is a provable unambiguous "best match")
     */
    public MethodInfo findMethod(SignatureConstant constSig, ErrorListener errs)
        {
        // TODO
        return null;
        }

    /**
     * Obtain all of the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
        {
        Set<MethodConstant> setOps = null;

        String sKey = sName + sOp + cParams;
        if (m_sOp != null && sKey.equals(m_sOp))
            {
            setOps = m_setOp;
            }
        else
            {
            for (MethodInfo info : getOpMethodInfos())
                {
                if (info.isOp(sName, sOp, cParams))
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info.getMethodConstant());
                    }
                }

            // cache the result
            m_sOp   = sKey;
            m_setOp = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getAutoMethodInfos()
        {
        Set<MethodInfo> setAuto = m_setAuto;
        if (setAuto == null)
            {
            for (MethodInfo info : m_mapMethods.values())
                {
                if (info.isAuto())
                    {
                    if (setAuto == null)
                        {
                        setAuto = new HashSet<>(7);
                        }
                    setAuto.add(info);
                    }
                }

            // cache the result
            m_setAuto = setAuto = (setAuto == null ? Collections.EMPTY_SET : setAuto);
            }

        return setAuto;
        }

    /**
     * Find a method on this type that converts an object of this type to a desired type.
     *
     * @param typeDesired  the type desired to convert to, or that the conversion result would be
     *                     assignable to ("isA" would be true)
     *
     * @return a MethodConstant representing an {@code @Auto} conversion method resulting in an
     *         object whose type is compatible with the specified (desired) type, or null if either
     *         no method matches, or more than one method matches (ambiguous)
     */
    public MethodConstant findConversion(TypeConstant typeDesired)
        {
        MethodConstant methodMatch = null;

        // check the cached result
        if (m_typeAuto != null && typeDesired.equals(m_typeAuto))
            {
            methodMatch = m_methodAuto;
            }
        else
            {
            for (MethodInfo info : getAutoMethodInfos())
                {
                MethodConstant method     = info.getMethodConstant();
                TypeConstant   typeResult = method.getRawReturns()[0];
                if (typeResult.equals(typeDesired))
                    {
                    // exact match -- it's not going to get any better than this
                    return method;
                    }

                if (typeResult.isA(typeDesired))
                    {
                    if (methodMatch == null)
                        {
                        methodMatch = method;
                        }
                    else
                        {
                        TypeConstant typeResultMatch = methodMatch.getRawReturns()[0];
                        boolean fSub = typeResult.isA(typeResultMatch);
                        boolean fSup = typeResultMatch.isA(typeResult);
                        if (fSub ^ fSup)
                            {
                            // use the obviously-more-specific type conversion
                            methodMatch = fSub ? method : methodMatch;
                            }
                        else
                            {
                            // ambiguous - there are at least two methods that match
                            methodMatch = null;
                            break;
                            }
                        }
                    }
                }

            // cache the result
            m_typeAuto   = typeDesired;
            m_methodAuto = methodMatch;
            }

        return methodMatch;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("TypeInfo: ")
          .append(m_type.getValueString());

        sb.append("\n- Parameters (")
                .append(m_mapTypeParams.size())
                .append(')');
        int i = 0;
        for (Entry<String, ParamInfo> entry : m_mapTypeParams.entrySet())
            {
            sb.append("\n  [")
              .append(i++)
              .append("] ")
              .append(entry.getKey())
              .append("=")
              .append(entry.getValue());
            }

        sb.append("\n- Class Chain (")
                .append(m_listmapClassChain.size())
                .append(')');
        i = 0;
        for (Entry<IdentityConstant, Boolean> entry : m_listmapClassChain.entrySet())
            {
            sb.append("\n  [")
              .append(i++)
              .append("] ")
              .append(entry.getKey().getValueString());

            if (entry.getValue())
                {
                sb.append(" (Anchored)");
                }
            }

        sb.append("\n- Default Chain (")
                .append(m_listmapDefaultChain.size())
                .append(')');
        i = 0;
        for (IdentityConstant constId : m_listmapDefaultChain.keySet())
            {
            sb.append("\n  [")
              .append(i++)
              .append("] ")
              .append(constId.getValueString());
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The data type that this TypeInfo represents.
     */
    private final TypeConstant m_type;

    /**
     * The Format of the type. In some cases, such as a difference type, or a "private interface",
     * the format is considered to be an interface, because it is not (and can not be) a class.
     */
    private final Format m_format;

    /**
     * The type parameters for this TypeInfo.
     */
    private final Map<String, ParamInfo> m_mapTypeParams;

    /**
     * The type that is extended. The term "extends" has slightly different meanings for mixins and
     * other classes.
     */
    private final TypeConstant m_typeExtends;

    /**
     * The type that is rebased onto.
     */
    private final TypeConstant m_typeRebases;

    /**
     * For mixins, the type that is mixed into. For interfaces, this is always Object.
     */
    private final TypeConstant m_typeInto;

    /**
     * The potential call chain of classes.
     */
    private final ListMap<IdentityConstant, Boolean> m_listmapClassChain;

    /**
     * The potential default call chain of interfaces.
     */
    private final ListMap<IdentityConstant, Boolean> m_listmapDefaultChain;

    /**
     * The properties of the type.
     */
    private final Map<String, PropertyInfo> m_mapProperties;

    /**
     * The methods of the type.
     */
    private final Map<SignatureConstant, MethodInfo> m_mapMethods;

    /**
     * A cached type resolver.
     */
    private transient TypeResolver m_resolver;

    // cached query results
    private transient Set<MethodInfo>     m_setAuto;
    private transient Set<MethodInfo>     m_setOps;
    private transient String              m_sOp;
    private transient Set<MethodConstant> m_setOp;
    private transient TypeConstant        m_typeAuto;
    private transient MethodConstant      m_methodAuto;
    }
