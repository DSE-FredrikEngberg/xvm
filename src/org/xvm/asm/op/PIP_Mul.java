package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlaceAssign;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * PIP_MUL PROPERTY, rvalue-target, rvalue2 ; T *= T
 */
public class PIP_Mul
        extends OpPropInPlaceAssign
    {
    /**
     * Construct a PIP_MUL op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argValue   the value Argument
     */
    public PIP_Mul(PropertyConstant idProp, Argument argTarget, Argument argValue)
        {
        super(idProp, argTarget, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Mul(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_MUL;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
        {
        return hTarget.getTemplate().invokePropertyMul(frame, hTarget, idProp, hValue);
        }
    }