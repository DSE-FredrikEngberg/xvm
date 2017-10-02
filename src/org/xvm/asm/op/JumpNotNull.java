package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xNullable;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_NULL rvalue, rel-addr ; jump if value is null
 */
public class JumpNotNull
        extends Op
    {
    /**
     * Construct a JMP_NNULL op.
     *
     * @param nValue    the Nullable value to test
     * @param nRelAddr  the relative address to jump to.
     */
    public JumpNotNull(int nValue, int nRelAddr)
        {
        f_nValue   = nValue;
        f_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNotNull(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue   = readPackedInt(in);
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_JMP_NNULL);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NNULL;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest = frame.getArgument(f_nValue);

            return hTest == xNullable.NULL ? iPC + 1 : iPC + f_nRelAddr;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nValue;
    private final int f_nRelAddr;
    }