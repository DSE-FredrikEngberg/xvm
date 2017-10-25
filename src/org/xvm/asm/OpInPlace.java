package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for IP_ (in-place) op codes.
 *
 * Note: "in-place assign" ops derive from OpInPlaceAssign
 */
public abstract class OpInPlace
        extends Op
    {
    /**
     * Construct an "in-place" op for the passed target.
     *
     * @param argTarget  the target Argument
     */
    protected OpInPlace(Argument argTarget)
        {
        assert(!isAssignOp());

        m_argTarget = argTarget;
        }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpInPlace(Argument argTarget, Argument argReturn)
        {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpInPlace(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget = readPackedInt(in);
        if (isAssignOp())
            {
            m_nRetValue = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            if (isAssignOp())
                {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
                }
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nTarget);
        if (isAssignOp())
            {
            writePackedLong(out, m_nRetValue);
            }
        }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp()
        {
        // majority of the ops are assigning; let's default to that
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (m_nTarget >= 0)
                {
                // operation on a register
                ObjectHandle hTarget = frame.getArgument(m_nTarget);
                if (hTarget == null)
                    {
                    return R_REPEAT;
                    }

                if (isAssignOp() && frame.isNextRegister(m_nRetValue))
                    {
                    frame.introduceVarCopy(m_nTarget);
                    }

                return completeWithRegister(frame, hTarget);
                }
            else
                {
                // operation on a local property
                if (isAssignOp() && frame.isNextRegister(m_nRetValue))
                    {
                    frame.introduceVarCopy(m_nTarget);
                    }

                PropertyConstant constProperty = (PropertyConstant)
                    frame.getConstant(m_nTarget);

                return completeWithProperty(frame, constProperty.getName());
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        throw new UnsupportedOperationException();
        }

    protected int completeWithProperty(Frame frame, String sProperty)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isAssignOp() && scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argTarget, registry);
        if (isAssignOp())
            {
            registerArgument(m_argReturn, registry);
            }
        }

    protected int m_nTarget;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
    }