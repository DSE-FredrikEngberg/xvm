package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * A template for the base of all Enum classes
 */
public class xEnum
        extends xConst
    {
    public static xEnum INSTANCE;

    public xEnum(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return true;
        }

    @Override
    public void initDeclared()
        {
        if (this == INSTANCE)
            {
            // all the methods are marked as native due to a "rebase"
            }
        else if (f_struct.getFormat() == Format.ENUM)
            {
            Collection<? extends Component> listAll = f_struct.children();
            List<String>     listNames = new ArrayList<>(listAll.size());
            List<EnumHandle> listHandles = new ArrayList<>(listAll.size());

            ConstantPool pool     = pool();
            int          iOrdinal = 0;
            for (Component child : listAll)
                {
                if (child.getFormat() == Format.ENUMVALUE)
                    {
                    TypeConstant type   = ((ClassStructure) child).getCanonicalType();
                    EnumHandle   hValue = makeEnumHandle(ensureClass(type, type), iOrdinal++);

                    listNames.add(child.getName());
                    listHandles.add(hValue);

                    // native enums don't require any initialization
                    if (!hValue.isStruct())
                        {
                        pool.ensureSingletonConstConstant(child.getIdentityConstant()).
                                setHandle(hValue);
                        }
                    }
                }
            m_listNames   = listNames;
            m_listHandles = listHandles;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof SingletonConstant)
            {
            SingletonConstant constValue = (SingletonConstant) constant;
            EnumHandle hValue = (EnumHandle) constValue.getHandle();

            if (hValue == null)
                {
                assert f_struct.getFormat() == Format.ENUMVALUE;

                xEnum templateEnum = (xEnum) getSuper();

                hValue = templateEnum.getEnumByConstant(constValue.getValue());
                constValue.setHandle(hValue);

                if (hValue.isStruct())
                    {
                    MethodStructure ctor  = f_struct.findConstructor(TypeConstant.NO_TYPES);
                    ObjectHandle[]  ahVar = Utils.ensureSize(Utils.OBJECTS_NONE, ctor.getMaxVars());

                    return proceedConstruction(frame, ctor, true, hValue, ahVar, Op.A_STACK);
                    }
                }

            frame.pushStack(hValue);
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        switch (sPropName)
            {
            case "name":
                return frame.assignValue(iReturn,
                        xString.makeHandle(m_listNames.get(hEnum.getOrdinal())));

            case "ordinal":
                return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getOrdinal()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        EnumHandle hEnum1 = (EnumHandle) hValue1;
        EnumHandle hEnum2 = (EnumHandle) hValue2;
        return frame.assignValue(iReturn, xBoolean.makeHandle(hEnum1.getOrdinal() == hEnum2.getOrdinal()));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        EnumHandle hEnum1 = (EnumHandle) hValue1;
        EnumHandle hEnum2 = (EnumHandle) hValue2;
        return frame.assignValue(iReturn, xOrdered.makeHandle(hEnum1.getOrdinal() - hEnum2.getOrdinal()));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((EnumHandle) hValue1).getOrdinal() == ((EnumHandle) hValue2).getOrdinal();
        }

    @Override
    public int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getOrdinal()));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn,
                xString.makeHandle(m_listNames.get(hEnum.getOrdinal())));
        }


    // ----- helper method -----

    /**
     * Create an EnumHandle for the specified ordinal value.
     *
     * Note: this method is overridden by native enums.
     *
     * @param clz       the enum's class
     * @param iOrdinal  the ordinal value
     *
     * @return the corresponding EnumHandle
     */
    protected EnumHandle makeEnumHandle(ClassComposition clz, int iOrdinal)
        {
        // create an un-initialized struct, which will be properly initialized
        // by createConstHandle() below; overridden by native enums
        return new EnumHandle(clz.ensureAccess(Access.STRUCT), iOrdinal);
        }

    public EnumHandle getEnumByName(String sName)
        {
        int ix = m_listNames.indexOf(sName);
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    public EnumHandle getEnumByOrdinal(int ix)
        {
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    /**
     * @return an EnumHandle for the specified id
     */
    public EnumHandle getEnumByConstant(IdentityConstant id)
        {
        ClassStructure clzThis = f_struct;

        assert clzThis.getFormat() == Format.ENUM;

        // need an ordinal value for the enum that this represents
        int i = 0;
        for (Component child : clzThis.children())
            {
            if (child.getIdentityConstant().equals(id))
                {
                return getEnumByOrdinal(i);
                }
            ++i;
            }
        return null;
        }

    /**
     * @return an Enum value name for the specified ordinal
     */
    public String getNameByOrdinal(int ix)
        {
        return m_listNames.get(ix);
        }


    // ----- ObjectHandle -----

    public static class EnumHandle
                extends GenericHandle
        {
        EnumHandle(TypeComposition clz, int index)
            {
            super(clz);

            m_index    = index;
            m_fMutable = false;
            }

        public int getOrdinal()
            {
            return m_index;
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return getOrdinal() - ((EnumHandle) that).getOrdinal();
            }

        @Override
        public int hashCode()
            {
            return m_index;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj instanceof EnumHandle)
                {
                EnumHandle that = (EnumHandle) obj;
                return m_clazz == that.m_clazz && m_index == that.m_index;
                }
            return false;
            }

        @Override
        public String toString()
            {
            return ((xEnum) getTemplate()).getNameByOrdinal(m_index);
            }

        protected int m_index;
        }


    // ----- fields -----

    protected List<String>     m_listNames;
    protected List<EnumHandle> m_listHandles;
    }
