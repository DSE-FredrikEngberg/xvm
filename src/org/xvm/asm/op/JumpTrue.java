package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.Utils;
import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * JMP_TRUE rvalue, addr ; jump if value is Boolean.True
 */
public class JumpTrue
        extends OpCondJump
    {
    /**
     * Construct a JMP_TRUE op.
     *
     * @param nValue    the Boolean value to test
     * @param nRelAddr  the relative address to jump to
     *
     * @deprecated
     */
    public JumpTrue(int nValue, int nRelAddr)
        {
        super((Argument) null, null);

        m_nArg  = nValue;
        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP_TRUE op.
     *
     * @param arg  the argument to test
     * @param op   the op to conditionally jump to
     */
    public JumpTrue(Argument arg, Op op)
        {
        super(arg, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpTrue(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_TRUE;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArg);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hArg))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                Frame.Continuation stepNext = frameCaller ->
                    ((BooleanHandle) ahArg[0]).get() ? iPC + m_ofJmp : iPC + 1;

                return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                }

            return ((BooleanHandle) hArg).get() ? iPC + m_ofJmp : iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
