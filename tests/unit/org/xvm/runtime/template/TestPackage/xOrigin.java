package org.xvm.runtime.template.TestPackage;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.op.Construct_N;
import org.xvm.asm.op.Return_0;

import org.xvm.asm.op.X_Print;
import org.xvm.runtime.Adapter;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;

/**
 * TODO:
 */
public class xOrigin extends ClassTemplate
    {
    private final Adapter adapter;

    public xOrigin(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        adapter = templates.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        MethodStructure mtConst = ensureMethodStructure("construct", VOID, VOID);
        mtConst.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# initializing Origin #")))
            .add(new Construct_N(adapter.getMethodConstId("TestApp.Point", "construct"),
                new int[]{adapter.ensureValueConstantId(0), adapter.ensureValueConstantId(0)}))
            .add(new Return_0());
        }
    }