module TestDefAsn.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        testDefAssignment();
        testShort();
        testNameHiding();
        testWhileLoop(0);
        testForLoop(0);
        }

    Boolean gimmeTrue()
        {
        return True;
        }
    String? maybeNull()
        {
        return null;
        }

    void testDefAssignment()
        {
        console.println("\n** testDefAssignment()");

        Int i;
        Boolean f1 = gimmeTrue();
        Boolean f2 = gimmeTrue();

        // vary this test as necessary (do vs. while; break vs. continue; && vs. ||, etc.)
        L1: do
            {
            if (f1 && {i=3; return true;})
                {
                //i = 3;
                break L1;
                }

            i = 1;
            continue L1;
            }
        while (f2);

        console.println("i=" + i);
        }

    void testShort()
        {
        console.println("\n** testShort()");

        Int i;
        Boolean f1 = gimmeTrue();
        Boolean f2 = gimmeTrue();
        String? s  = maybeNull();

        if (s?.size > 1 && {i=3; return true;})
            {
            //i = 3;
            }
        else
            {
            i = 4;
            }

        console.println("i=" + i);
        }

    void testNameHiding()
        {
        static conditional (String, Int) name()
            {
            return True, "hello", 5;
            }

        if ((String name, Int count) := name())
            {
            assert name.size == count;
            }
        }

    void testWhileLoop(Int? in)
        {
        Int i;
        while (true)
            {
            if (in.is(Int))
                {
                i = in;
                break;
                }
            else
                {
                throw new IllegalState();
                }
            }

        assert i.is(Int);
        }

    void testForLoop(Int? in)
        {
        Int i;
        for (;;)
            {
            if (in.is(Int))
                {
                i = in;
                break;
                }
            else
                {
                return;
                }
            }

        assert i.is(Int);
        }
    }