package org.xvm.proto.template.collections;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import org.xvm.proto.Utils;
import org.xvm.proto.template.IndexSupport;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xString;
import org.xvm.proto.template.xString.StringHandle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xTuple
        extends ClassTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;

    public xTuple(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: remove
        f_types.f_adapter.addMethod(f_struct, "construct", new String[]{"collections.Sequence<Object>"}, VOID);

        markNativeMethod("construct", new String[]{"collections.Sequence<Object>"}, VOID);
        }

    @Override
    protected TypeComposition createCanonicalClass()
        {
        // this is Void.class
        return new TypeComposition(this, Collections.EMPTY_MAP, true);
        }

    @Override
    public TypeComposition resolveClass(TypeConstant constClassType, Map<String, Type> mapActual)
        {
        List<TypeConstant> listParams = constClassType.getParamTypes();

        int cParams = listParams.size();
        if (cParams == 0)
            {
            return f_clazzCanonical;
            }

        Map<String, Type> mapParams = new HashMap<>();
        for (int i = 0, c = listParams.size(); i < c; i++)
            {
            mapParams.put("ElementTypes[" + i + ']',
                    f_types.resolveType(listParams.get(i), mapActual));
            }
        return ensureClass(mapParams);
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constTuple = (ArrayConstant) constant;

        ObjectHeap heap = f_types.f_container.f_heapGlobal;

        TypeConstant constType = constTuple.getType();
        List<TypeConstant> listElemTypes = constType.getParamTypes();
        Map<String, Type> mapActual = frame.getActualTypes();

        Constant[] aconst = constTuple.getValue();
        int c = aconst.length;

        ObjectHandle[] ahValue = new ObjectHandle[c];
        Type[] aType = new Type[c];
        for (int i = 0; i < c; i++)
            {
            Constant constValue = aconst[i];

            TypeConstant constElemType = listElemTypes.get(i);

            ahValue[i] = heap.ensureConstHandle(frame, constValue.getPosition());
            aType[i] = f_types.resolveClass(constElemType, mapActual).ensurePublicType();
            }

        TupleHandle hTuple = makeHandle(aType, ahValue);
        hTuple.makeImmutable();
        return hTuple;
        }

    @Override
    public boolean isConstantCacheable(Constant constant)
        {
        ArrayConstant constTuple = (ArrayConstant) constant;
        TypeConstant constType = constTuple.getType();

        ObjectHeap heap = f_types.f_container.f_heapGlobal;

        for (TypeConstant constElemType : constType.getParamTypes())
            {
            ClassTemplate template = heap.getConstTemplate(constElemType);
            if (!template.isConstantCacheable(constElemType))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hSequence = ahVar[0];
        IndexSupport support = (IndexSupport) hSequence.f_clazz.f_template;

        ObjectHandle[] ahValue;

        try
            {
            ahValue = support.toArray(hSequence);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }

        TupleHandle hTuple = new TupleHandle(clazz, ahValue);

        return frame.assignValue(iReturn, hTuple);
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            return IndexSupport.outOfRange(lIndex, cElements);
            }

        if (!hTuple.isMutable())
            {
            return xException.makeHandle("Immutable object");
            }

        hTuple.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public Type getElementType(ObjectHandle hTarget, long lIndex)
                throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_aType[(int) lIndex];
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        return hTuple.m_ahValue.length;
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hTuple.f_clazz.toString())
          .append('(');

        ObjectHandle[] ahValue = hTuple.m_ahValue;
        Iterator<ObjectHandle> iterValues = new Iterator<ObjectHandle>()
            {
            private int i = 0;
            public boolean hasNext()
                {
                return i < ahValue.length;
                }
            public ObjectHandle next()
                {
                return ahValue[i++];
                }
            };

        return new ToString(sb, iterValues, iReturn).doNext(frame);
        }

    /**
     * Helper class for buildStringValue() implementation.
     */
    protected static class ToString
            implements Frame.Continuation
        {
        final private StringBuilder sb;
        final private Iterator<ObjectHandle> iterValues;
        final private int iReturn;

        public ToString(StringBuilder sb, Iterator iterValues, int iReturn)
            {
            this.sb = sb;
            this.iterValues = iterValues;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            sb.append(((StringHandle) frameCaller.getFrameLocal()).getValue())
              .append(", ");
            }

        protected int doNext(Frame frameCaller)
            {
            while (iterValues.hasNext())
                {
                switch (Utils.callToString(frameCaller, iterValues.next()))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            sb.setLength(sb.length() - 2); // remove the trailing ", "
            sb.append(')');

            return frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }

    // ----- ObjectHandle helpers -----

    public static TupleHandle makeHandle(Type[] aType, ObjectHandle[] ahValue)
        {
        Map<String, Type> mapParams;
        if (aType.length == 0)
            {
            mapParams = Collections.EMPTY_MAP;
            }
        else
            {
            mapParams = new HashMap<>();
            for (int i = 0, c = aType.length; i < c; i++)
                {
                // TODO: how to name them?
                mapParams.put("ElementTypes[" + i + ']', aType[i]);
                }
            }
        return new TupleHandle(INSTANCE.ensureClass(mapParams), ahValue);
        }

    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle[] ahValue)
        {
        return new TupleHandle(clazz, ahValue);
        }

    public static class TupleHandle
            extends ObjectHandle
        {
        public Type[] m_aType;
        public ObjectHandle[] m_ahValue;
        public boolean m_fFixedSize;
        public boolean m_fPersistent;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue)
            {
            super(clazz);

            m_fMutable = true;
            m_ahValue = ahValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + Arrays.toString(m_ahValue);
            }
        }
    }
