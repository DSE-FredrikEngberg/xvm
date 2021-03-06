/**
 * A Property represents a property of a particular implementation or type. A property has a type, a name,
 * and a value. At runtime, a property is itself of type `Ref`.
 */
interface Property<Target, Referent, Implementation extends Ref<Referent>>
        extends immutable Const
    {
    /**
     * The name of the property.
     */
    @RO String name;

    /**
     * Determine if the property represents a constant (a `static` property), and if it does, obtain
     * the constant value.
     *
     * @return True iff the property represents a constant
     * @return (conditional) the constant value
     */
    conditional Referent isConstant();

    /**
     * True iff this property does not expose a `set` method.
     */
    @RO Boolean readOnly;

    /**
    * True iff this represents a property that is read/write (a `Var`) at some level, but not at
    * this level.
    */
    @RO Boolean hasUnreachableSetter;

    /**
    * True iff this property represents a formal type for the class of the object containing this
    * property.
    */
    @RO Boolean formal;

    /**
    * True iff this property has storage allocated in the underlying structure of the object
    * containing this property.
    */
    @RO Boolean hasField;

    /**
    * True iff this property is injected via the `@Inject` annotation.
    */
    @RO Boolean injected;

    /**
    * True iff this property is lazily computed.
    */
    @RO Boolean lazy;

    /**
    * True iff this property is an atomic value.
    */
    @RO Boolean atomic;

    /**
    * True iff this property is abstract.
    */
    @RO Boolean abstract;


    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * Given an object reference of a type that contains this method, obtain the invocable function
     * that corresponds to this method on that object.
     */
    Implementation of(Target target);

    /**
     * Given an object reference of a type that contains this property, obtain the value of the
     * property.
     */
    Referent get(Target target)
        {
        return this.of(target).get();
        }

    /**
     * Given an object reference of a type that contains this property, modify the value of the
     * property.
     */
    void set(Target target, Referent value)
        {
        if (readOnly)
            {
            throw new Exception($"Property {name} is read-only");
            }

        this.of(target).as(Var<Referent>).set(value);
        }
    }
