package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL rvalue, #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 */
public class JumpVal
        extends Op
    {
    /**
     * Construct a JMP_VAL op.
     *
     * @param arg        a value Argument
     * @param aArgCase   an array of "case" Arguments
     * @param aOpCase    an array of Ops to jump to
     * @param opDefault  an Op to jump to in the "default" case
     */
    public JumpVal(Argument arg, Argument[] aArgCase, Op[] aOpCase, Op opDefault)
        {
        assert aOpCase != null;

        m_argVal = arg;
        m_aArgCase = aArgCase;
        m_aOpCase = aOpCase;
        m_opDefault = opDefault;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpVal(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg = readPackedInt(in);

        int cCases = readMagnitude(in);

        int[] anArgCase = new int[cCases];
        int[] aofCase = new int[cCases];
        for (int i = 0; i < cCases; ++i)
            {
            anArgCase[i] = readPackedInt(in);
            aofCase[i] = readPackedInt(in);
            }
        m_anArgCase = anArgCase;
        m_aofCase = aofCase;

        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argVal != null)
            {
            m_nArg = encodeArgument(m_argVal, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nArg);

        int[] anArgCase = m_anArgCase;
        int[] aofCase = m_aofCase;
        int c = anArgCase.length;

        writePackedLong(out, c);
        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, anArgCase[i]);
            writePackedLong(out, aofCase[i]);
            }

        writePackedLong(out, m_ofDefault);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_VAL;
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_aOpCase != null && m_aofCase == null)
            {
            int c = m_aOpCase.length;
            m_aofCase = new int[c];
            for (int i = 0; i < c; i++)
                {
                m_aofCase[i] = resolveAddress(code, iPC, m_aOpCase[i]);
                }
            m_ofDefault = resolveAddress(code, iPC, m_opDefault);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nArg);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frame, iPC, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return complete(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        Integer Index = ensureJumpMap(frame).get(hValue);

        return Index == null
            ? iPC + m_ofDefault
            : iPC + m_aofCase[Index.intValue()];
        }

    private Map<ObjectHandle, Integer> ensureJumpMap(Frame frame)
        {
        Map<ObjectHandle, Integer> mapJump = m_mapJump;
        if (mapJump == null)
            {
            int[] anArgCase = m_anArgCase;
            int[] aofCase   = m_aofCase;
            int   cCases    = anArgCase.length;

            mapJump = new HashMap<>(cCases);

            for (int i = 0, c = anArgCase.length; i < c; i++ )
                {
                ObjectHandle hCase = frame.getConstHandle(anArgCase[i]);

                assert !hCase.isMutable();

                mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                }

            m_mapJump = mapJump;
            }
        return mapJump;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argVal = registerArgument(m_argVal, registry);

        for (int i = 0, c = m_aArgCase.length; i < c; i++)
            {
            m_aArgCase[i] = registerArgument(m_aArgCase[i], registry);
            }
        }

    protected int   m_nArg;
    protected int[] m_anArgCase;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument m_argVal;
    private Argument[] m_aArgCase;
    private Op[] m_aOpCase;
    private Op m_opDefault;

    // cached jump map
    private Map<ObjectHandle, Integer> m_mapJump;
    }
