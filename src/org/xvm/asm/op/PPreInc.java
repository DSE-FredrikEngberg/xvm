package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * P_PREINC rvalue-target, CONST_PROPERTY, lvalue ; same as PREINC for a register
 */
public class PPreInc
        extends OpProperty
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public PPreInc(int nTarget, int nArg, int nRet)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nArg;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PPreInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTarget = readPackedInt(in);
        f_nPropConstId = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_P_PREINC);
        writePackedLong(out, f_nTarget);
        writePackedLong(out, f_nPropConstId);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_P_PREINC;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(f_nPropConstId);

            return hTarget.f_clazz.f_template.invokePreInc(
                    frame, hTarget, constProperty.getName(), f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }