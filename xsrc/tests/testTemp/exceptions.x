module TestTry.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Exception tests:");

        testBasic();
        }

    void testThrow()
        {
        console.println("\n** testThrow()");
        throw new X.IllegalStateException("test");
        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        try
            {
            testThrow();
            console.println("DIDN'T THROW!");
            }
        catch (Exception e)
            {
            console.println("caught: " + e);
            }

        console.println("done: " + e);
        }
    }