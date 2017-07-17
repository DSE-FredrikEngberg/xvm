package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xTuple.TupleHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * RETURN_T rvalue-tuple ; return (a tuple of return values)
 *
 * (generated by the compiler when the current function has a multi-return, but the
 *  specified register is a tuple)
 *
 * @author gg 2017.03.08
 */
public class Return_T extends Op
    {
    private final int f_nArgValue;

    public Return_T(int nValue)
        {
        f_nArgValue = nValue;
        }

    public Return_T(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_RETURN_T);
        out.writeInt(f_nArgValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iRet = frame.f_iReturn;
        if (iRet >= 0 || iRet == Frame.RET_LOCAL)
            {
            throw new IllegalStateException(); // assertion
            }

        switch (iRet)
            {
            case Frame.RET_UNUSED:
                break;

            case Frame.RET_MULTI:
                TupleHandle hTuple;
                try
                    {
                    hTuple = (TupleHandle) frame.getArgument(f_nArgValue);
                    if (hTuple == null)
                        {
                        return R_REPEAT;
                        }
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    frame.m_hException = e.getExceptionHandle();
                    return R_EXCEPTION;
                    }

                int[] aiRet = frame.f_aiReturn;
                ObjectHandle[] ahValue = hTuple.m_ahValue;

                // it's possible that the caller doesn't care about some of the return values
                for (int i = 0, c = aiRet.length; i < c; i++)
                    {
                    int iResult = frame.f_framePrev.assignValue(aiRet[i], ahValue[i]);
                    switch (iResult)
                        {
                        case Op.R_EXCEPTION:
                            return Op.R_RETURN_EXCEPTION;

                        case Op.R_BLOCK:
                            // tuple's value cannot be a synthetic future
                            throw new IllegalStateException();
                        }
                    }
                break;

            default:
                // pass the tuple "as is"
                return frame.returnValue(-iRet - 1, f_nArgValue);
            }
        return R_RETURN;
        }
    }
