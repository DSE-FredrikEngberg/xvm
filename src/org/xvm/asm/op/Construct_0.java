package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;


/**
 * CONSTR_0 CONSTRUCT
 */
public class Construct_0
        extends OpCallable
    {
    /**
     * Construct a CONSTR_0 op.
     *
     * @param nConstructorId  identifies the construct function
     *
     * @deprecated
     */
    public Construct_0(int nConstructorId, int nArg)
        {
        super(null);

        m_nFunctionId = nConstructorId;
        }

    /**
     * Construct a CONSTR_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     */
    public Construct_0(MethodConstant constMethod)
        {
        super(constMethod);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Construct_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_CONSTR_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame);
        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];

        ObjectHandle hStruct = frame.getThis();

        frame.chainFinalizer(Utils.makeFinalizer(constructor, hStruct, ahVar));

        return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
        }
    }