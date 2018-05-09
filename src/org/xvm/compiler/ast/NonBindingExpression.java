package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as a lambda parameter when nobody cares what the parameter is
 * and they just want it to go away quietly.
 */
public class NonBindingExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NonBindingExpression(long lStartPos, long lEndPos, TypeExpression type)
        {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        this.type      = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the type expression of the unbound argument, iff one was specified; otherwise null
     */
    public TypeExpression getArgType()
        {
        return type;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return type == null
                ? null
                : type.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        TypeFit      fit      = TypeFit.Fit;
        TypeConstant type     = null;
        Constant     constant = null;

        if (this.type != null)
            {
            TypeExpression exprNew = (TypeExpression) this.type.validate(ctx, typeRequired, pref, errs);
            if (exprNew == null)
                {
                fit  = TypeFit.NoFit;
                type = typeRequired;
                }
            else
                {
                fit  = TypeFit.Fit;
                type = exprNew.getType();
                if (exprNew.isConstant())
                    {
                    constant = exprNew.toConstant();
                    }
                }
            }
        return finishValidation(fit, type, constant);
        }

    @Override
    public boolean isNonBinding()
        {
        return true;
        }

    @Override
    public Argument generateArgument(Code code, boolean fPack, boolean fLocalPropOk,
            boolean fUsedOnce,
            ErrorListener errs)
        {
        throw new IllegalStateException("NonBindingExpression cannot generate an argument;"
                + ": that's why they're called non-binding! (" + this + ')');
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return type == null
                ? "?"
                : "<" + type + ">?";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected long           lStartPos;
    protected long           lEndPos;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NonBindingExpression.class, "type");
    }