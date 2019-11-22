package org.xvm.runtime.template.collections;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native ListMap support.
 */
public class xListMap
        extends ClassTemplate
    {
    public static xListMap INSTANCE;

    public xListMap(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        CONSTRUCTOR = f_struct.findMethod("construct", 2);
        }


    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MapConstant)
            {
            TypeConstant            typeMap   = constant.getType();
            Map<Constant, Constant> mapValues = ((MapConstant) constant).getValue();
            int                     cEntries  = mapValues.size();

            ObjectHandle[] ahKey        = new ObjectHandle[cEntries];
            ObjectHandle[] ahVal        = new ObjectHandle[cEntries];
            boolean        fDeferredKey = false;
            boolean        fDeferredVal = false;
            int            ix           = 0;
            for (Map.Entry<Constant, Constant> entry : mapValues.entrySet())
                {
                ObjectHandle hKey = frame.getConstHandle(entry.getKey());
                ObjectHandle hVal = frame.getConstHandle(entry.getValue());

                fDeferredKey |= hKey instanceof DeferredCallHandle;
                fDeferredVal |= hVal instanceof DeferredCallHandle;

                ahKey[ix] = hKey;
                ahVal[ix] = hVal;
                ix++;
                }

            ConstantPool pool         = typeMap.getConstantPool();
            TypeConstant typeKey      = typeMap.resolveGenericType("Key");
            TypeConstant typeVal      = typeMap.resolveGenericType("Value");
            TypeConstant typeKeyArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeKey);
            TypeConstant typeValArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeVal);

            ClassComposition clzKeyArray = f_templates.resolveClass(typeKeyArray);
            ClassComposition clzValArray = f_templates.resolveClass(typeValArray);
            ClassComposition clzMap      = ensureClass(
                pool.ensureParameterizedTypeConstant(f_struct.getIdentityConstant().getType(), typeKey, typeVal));

            ObjectHandle     haKeys = fDeferredKey
                    ? new DeferredArrayHandle(clzKeyArray, ahKey)
                    : ((xArray) clzKeyArray.getTemplate()).createArrayHandle(clzKeyArray, ahKey);
            ObjectHandle     haVals = fDeferredVal
                    ? new DeferredArrayHandle(clzValArray, ahVal)
                    : ((xArray) clzValArray.getTemplate()).createArrayHandle(clzValArray, ahVal);

            ObjectHandle[] ahArg = new ObjectHandle[CONSTRUCTOR.getMaxVars()];
            ahArg[0] = haKeys;
            ahArg[1] = haVals;
            return construct(frame, CONSTRUCTOR, clzMap, null, ahArg, Op.A_STACK);
            }
        return super.createConstHandle(frame, constant);
        }

    private static MethodStructure CONSTRUCTOR;
    }