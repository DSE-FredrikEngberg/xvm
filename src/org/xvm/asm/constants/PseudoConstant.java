package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant whose purpose is to represent a level of indirection.
 */
public abstract class PseudoConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool the ConstantPool that will contain this Constant
     */
    protected PseudoConstant(ConstantPool pool)
        {
        super(pool);
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    protected Object getLocator()
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        return super.getLocator();
        }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        super.assemble(out);
        }
    }
