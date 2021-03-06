import collections.Collection;
import collections.ListMap;
import collections.Set;

import reflect.Access;
import reflect.Annotation;
import reflect.Class;
import reflect.InvalidType;
import reflect.Method;
import reflect.MultiMethod;
import reflect.Parameter;
import reflect.Property;
import reflect.TypeTemplate;

/**
 * The Type interface represents an Ecstasy data type. There is nothing more central to a language
 * than its _type system_, and there are few words used more often -- and more haphazardly -- in
 * programming than the word "type". As a result, in the mind of the programmer, the term carries
 * many meanings and connotations, and thus it is essential that the term's definition in the
 * Ecstasy language be clear, complete, and concise.
 *
 * In Ecstasy, a type is the representation of the _capabilities_ of an object reference. An Ecstasy
 * object can only be invoked, accessed, and manipulated via a reference, and each reference is
 * composed of two pieces of information: (i) a type, and (ii) an identity. The type answers the
 * questions of: "What information can be accessed? What information can be modified? What actions
 * can be performed?" The identity answers the question of: "To what thing are those questions being
 * directed?"
 *
 * (To explain these two terms in a manner that a C++ programmer can understand, an Ecstasy
 * reference is _conceptually_ composed of (i) a v-table pointer **and** (ii) a `struct` pointer. An
 * Ecstasy type is therefore analogous to a v-table, but one that is fully self-describing as if it
 * included its own RTTI. Furthermore, because the v-table pointer is part of the reference, and not
 * part of the struct as it is in C++, it means that a single struct can expose different types at
 * runtime, such as separate `public`, `protected`, and `private` types, as well as any number of
 * pure virtual class or other super-types. Since the object is represented by the reference, and
 * not directly by the `struct`, it is even possible that a single `struct` can be used to represent
 * more than one object; for example, the same structure -- the same location in memory -- that
 * represents an "Int" value could potentially be used with the type "public Int", the type "Int[]",
 * and the type "Tuple<Int>", such that the same structural value can represent itself, an array of
 * itself, and a tuple of itself.)
 *
 * An Ecstasy type thus exposes the following information:
 *
 * * A set of _properties_ that expose object state and generic type information;
 * * The permission for (or the prevention of) object state mutation via those properties;
 * * A set of virtual _methods_;
 * * A set of _funky_ interface functions; and
 * * The form of the type, and information related to that form.
 *
 * The form of the type provides information related to the origin of how the type came to be, such
 * as (i) from a class, (ii) as a union of two types, (iii) as an explicitly immutable form of
 * another type, and so so. In each case, the form implies additional information, such as (i) what
 * class the type represents, (ii) what the two types were that formed the union, and (iii) what the
 * other type was for which the explicitly-immutable form of the type was created.
 *
 * Additionally, the type may provide _constants_ (represented as properties) and functions that
 * are associated with (visible on) the type at compile-time, even though for purposes of type
 * compatibility, these are not defining members of a type.
 *
 * @param DataType   this type
 * @param OuterType  the type of the enclosing class, if this is a virtual child type
 */
interface Type<DataType, OuterType>
        extends Const
    {
    // ----- inner classes -------------------------------------------------------------------------

    typedef Function<<>, <DataType>> Constructor;

    typedef TypeTemplate.Form Form;


    // ----- state representation ------------------------------------------------------------------

    /**
     * The form of the type.
     */
    @RO Form form;

    /**
     * The type or types that are under this type. A modifying type will have a single underlying
     * type; a relational type will have two underlying types; and a Sequence type will have zero
     * or more underlying types.
     */
    @RO Type![] underlyingTypes;

    /**
     * Obtain the raw set of all properties on the type.
     */
    @RO Property<DataType>[] properties;

    /**
     * Obtain the raw set of all constants associated with the type. Constants are technically not
     * part of the type definition, but are provided here for convenience, because otherwise it
     * would be relatively difficult to gather this information.
     */
    @RO Property[] constants;

    /**
     * Obtain the methods and functions of the type, collected by name, and represented by
     * multimethods.
     */
    @RO Map<String, MultiMethod<DataType>> multimethods.get()
        {
        ListMap<String, MultiMethod<DataType>> map = new ListMap();
        for (Method<DataType> m : methods)
            {
            String name = m.name;
            MultiMethod<DataType> mm = map.getOrCompute(name, () -> new MultiMethod<DataType>(name, []));
            map.put(name, mm + m);
            }
        for (Function f : functions)
            {
            String name = f.name;
            MultiMethod<DataType> mm = map.getOrCompute(name, () -> new MultiMethod<DataType>(name, []));
            map.put(name, mm + f);
            }
        return map;
        }

    /**
     * Obtain the raw set of all methods on the type.
     */
    @RO Method<DataType>[] methods;

    /**
     * Obtain the raw set of all functions associated with the type. Functions are technically not
     * part of the type definition, but are provided here for convenience, because otherwise it
     * would be relatively difficult to gather this information.
     */
    @RO Function[] functions;

    /**
     * The constructors for the type. Constructors are technically not part of the type definition,
     * but are provided here for convenience, because otherwise it would be relatively difficult to
     * gather this information. In addition to the explicitly declared constructors, an instantiable
     * class always has an implicit constructor that takes a parameter of type `StructType`. All
     * constructors for a virtual child class require the parent (i.e. outer) reference to be
     * provided as the first parameter of construction.
     */
    @RO Constructor[] constructors;

    /**
     * If this type is a class type, and the class has child classes, then this provides the types
     * of those child classes. Child classes are technically not part of the type definition, but
     * are provided here for convenience, because otherwise it would be relatively difficult to
     * gather this information.
     */
    @RO Map<String, Type!<>> childTypes;

    /**
     * Determine if the type non-ambiguously represents a class, and if so, obtain the class. The
     * class can be ambiguous, for example, if the type is a intersection type and the two
     * intersected types do not each represent the same class.
     *
     * A type of form `Class` always represents a class.
     *
     * @return True iff this type represents a class
     * @return (conditional) the class
     */
    conditional Class fromClass();

    /**
     * Determine if the type non-ambiguously represents a property. The property can be ambiguous,
     * for example, if the type is a intersection type and the two intersected types do not each
     * represent the same property.
     *
     * A type of form `Property` always represents a property.
     *
     * @return True iff this type represents a property
     * @return (conditional) the property
     */
    conditional Property fromProperty();

    /**
     * Determine if this type modifies an underlying type, and if it does, obtain that underlying
     * type.
     *
     * A type whose form has `modifying==True` always represents a modification to an underlying
     * type.
     *
     * @return True iff this type delegates in some manner to an underlying type
     * @return (conditional) the underlying type
     */
    conditional Type!<> modifying();

    /**
     * Determine if this type is a relational type, and if it is, obtain the two types that it is
     * a relation of.
     *
     * A type whose form has `relational==True` always represents an relational type.
     *
     * @return True iff the type is relational
     * @return (conditional) the first of the two types of the relation
     * @return (conditional) the second of the two types of the relation
     */
    conditional (Type!<>, Type!<>) relational();

    /**
     * Determine if the type has a name, and if so, obtain that name.
     *
     * Typedefs, child classes, properties, and formal types always have a name. Other types _may_
     * provide a name.
     *
     * @return True iff the type has a name
     * @return (conditional) the name of the type
     */
    conditional String named();

    /**
     * Determine if the type is contextually contained within another type, from which it may draw
     * type information.
     *
     * A type whose form is Child or FormalChild will always have a parent.
     *
     * @return True iff this type is contextually contained within another type
     * @return (conditional) the parent that contains this type
     */
    conditional Type!<> contained();

    /**
     * Determine if the type has a non-conflicting specified access control, and if so, obtain the
     * access control. A conflicting access control can occur with a relational type that combines
     * two types, each with a different access control.
     *
     * A type whose form is `Access` will always have an access control specified.
     *
     * @return True iff this type has an unambiguous specified access control
     * @return (conditional)
     */
    conditional Access accessSpecified();

    /**
     * Determine if the type has a non-conflicting annotation, and if so, obtain the first one. A
     * conflicting annotation can occur, for example, in an intersection of two types that do not
     * each have the same annotation.
     *
     * A type whose form is `Annotated` will always have a non-conflicting annotation.
     *
     * @return True iff there is an unambiguous annotation
     * @return (conditional) the first annotation
     */
    conditional Annotation annotated();

    /**
     * Determine if the type has non-conflicting type parameter information, and if so, obtain the
     * type for each specified parameter. (Conflicting type parameter information can result in a
     * relational type, since it is composed of multiple types.)
     *
     * A type whose form is `Parameterized` will always have type parameters, although the number of
     * type parameters may be zero (i.e. resulting in an empty array).
     *
     * @return True iff the type is parameterized
     * @return (conditional) an array of the type parameters
     */
    conditional Type!<>[] parameterized();

    /**
     * Determine if the type is recursive. Certain types may recursively refer to themselves, either
     * directly or indirectly; consider the example:
     *
     *     typedef (Nullable | Boolean | Number | String | JsonDoc[] | Map<String, JsonDoc>) JsonDoc
     */
    @RO Boolean recursive;

    /**
     * A type can be explicitly immutable. An object can only be assigned to an explicitly immutable
     * type if the object is immutable.
     */
    @RO Boolean explicitlyImmutable;

    /**
     * Obtain the `Pure` form of this type.
     */
    Type!<> purify();

    /**
     * Obtain a TypeTemplate that represents this type.
     */
    @RO TypeTemplate template;


    // ----- type operations -----------------------------------------------------------------------

    /**
     * Test whether this type is type-compatible with the specified second type, such that any
     * object of `this` type would also be an object of `that` type.
     *
     * @param that  a second type
     *
     * @return True iff all objects of `this` type are also objects of `that` type
     */
    Boolean isA(Type!<> that)
        {
        return this.is(&that.actualType);
        }

    /**
     * Determine if references of this type would be _assignable to_ references of the specified
     * type, using the rules of duck-typing.
     *
     * let _T1_ and _T2_ be two types  (T2 == this, T1 == that)
     * * let _M1_ be the set of all methods in _T1_ (including those representing properties)
     * * let _M2_ be the set of all methods in _T2_ (including those representing properties)
     * * let _T2_ be a "derivative type" of _T1_ iff
     *   1. _T1_ originates from a Class _C1_
     *   2. _T2_ originates from a Class _C2_
     *   3. _C2_ is a derivative Class of _C1_
     * * if _T1_ and _T2_ are both parameterized types, let "same type parameter" be a type
     *   parameter of _T1_ that also is a type parameter of _T2_ because _T2_ is a derivative type
     *   of _T1_, or _T1_ is a derivative type of _T1_, or both _T1_ and _T2_ are derivative types
     *   of some _T3_.
     *
     * Type _T2_ is assignable to a Type _T1_ iff both of the following hold true:
     * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
     *    true:
     *    1. _m1_ and _m2_ have the same name
     *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
     *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
     *       1. _p1_ is assignable to _p2_
     *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
     *          the following hold true:
     *          1. _p2_ is assignable to _p1_
     *          2. _T1_ produces _p1_
     *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
     *       _m1_ and _r2_ of _m2_, the following holds true:
     *      1. _r2_ is assignable to _r1_
     * 2. if _T1_ is explicitly immutable, then _T2_ must also be explicitly immutable.
     */
    Boolean duckTypeableTo(Type!<> that)
        {
        if (this.as(Object) == that.as(Object))
            {
            return true;
            }

        if (that.explicitlyImmutable && !this.explicitlyImmutable)
            {
            return false;
            }

        if (this.isA(that))
            {
            return true;
            }

        // this type must have a matching method for each method of that type
        nextMethod: for (Method m1 : that.methods)
            {
            // find the corresponding method on this type
            for (val m2 : this.multimethods[m1.name]?.methods)
                {
                if (m2.isSubstitutableFor(m1))
                    {
                    continue nextMethod;
                    }
                }

            // no such matching method
            return false;
            }

        return true;
        }

    /**
     * Determine if this type _consumes_ a formal type with the specified name.
     *
     * @see Method.consumesFormalType
     */
    Boolean consumesFormalType(String typeName)
        {
        return methods.iterator().untilAny(method -> method.consumesFormalType(typeName));
        }

    /**
     * Determine if this type _produces_ a formal type with the specified name.
     *
     * @see Method.producesFormalType
     */
    Boolean producesFormalType(String typeName)
        {
        return methods.iterator().untilAny(method -> method.producesFormalType(typeName));
        }

    /**
     * Test whether the specified object is an instance of this type.
     */
    Boolean isInstance(Object o)
        {
        return o.is(this);
        }

    /**
     * Cast the specified object to this type.
     */
    DataType cast(Object o)
        {
        return isInstance(o)
                ? o.as(DataType)
                : throw new InvalidType($"Type mismatch (actualType={&o.actualType}, this={this}");
        }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Create a type that is the union of this type and another type.
     *
     * @param that  a type
     *
     * @return a relational type that is the union of `this` type and `that` type
     *
     * @throws InvalidType  if the union would violate the rules of the type system
     */
    @Op("+")
    Type!<> add(Type!<> that);

    /**
     * Create a type that is the result of adding methods to this type.
     *
     * @param method  the methods to add to this type
     *
     * @return a type that contains the contents of this type and the specified methods
     *
     * @throws InvalidType  if adding the method to this type would violate the rules of the
     *                      type system
     */
    @Op("+")
    Type!<> add(Method... methods);

    /**
     * Create a type that is the result of adding properties to this type.
     *
     * @param properties  the properties to add to this type
     *
     * @return a type that contains the contents of this type and the specified properties
     *
     * @throws InvalidType  if adding the method to this type would violate the rules of the
     *                      type system
     */
    @Op("+")
    Type!<> add(Property... properties);

    /**
     * Create a type that is the intersection of this type and another type. Note that the bitwise
     * "or" symbol is used to indicate that the result will be a relational type representing "this"
     * type **or** "that" type; however the contents of the type -- the methods and properties --
     * are identical to the result of the "and" operator.
     *
     * @param that  a type
     *
     * @return a relational type that is the intersection of `this` type and `that` type
     *
     * @throws InvalidType  if the intersection would violate the rules of the type system
     */
    @Op("|")
    Type!<> or(Type!<> that);

    /**
     * Create a type that is the intersection of this type and another type. Note that the bitwise
     * "and" symbol is used to indicate that the result will be a simple type representing only the
     * common methods of the two types, and not a relational intersection type result.
     *
     * @param that  a type
     *
     * @return a type that contains only the common properties and methods of `this` type and `that`
     *         type
     *
     * @throws InvalidType  if the intersection would violate the rules of the type system
     */
    @Op("&")
    Type!<> and(Type!<> that);

    /**
     * Create a type that is the difference of this type and another type.
     *
     * @param that  a type
     *
     * @return a relational type that is the difference of `this` type and `that` type
     *
     * @throws InvalidType  if the difference would violate the rules of the type system
     */
    @Op("-")
    Type!<> sub(Type!<> that);

    /**
     * Create a type that is the result of removing a method from this type.
     *
     * @param methods  the methods to remove from this type
     *
     * @return a type that contains the contents of this type minus the specified methods
     *
     * @throws InvalidType  if removing the method from this type would violate the rules of the
     *                      type system
     */
    @Op("-")
    Type!<> sub(Method... methods);

    /**
     * Create a type that is the result of removing a property from this type.
     *
     * @param properties  the properties to remove from this type
     *
     * @return a type that contains the contents of this type minus the specified properties
     *
     * @throws InvalidType  if removing the property from this type would violate the rules of the
     *                      type system
     */
    @Op("-")
    Type!<> sub(Property... properties);


    // ----- constructor helpers -------------------------------------------------------------------

    /**
     * Helper method to locate the default (no-parameter) constructor.
     *
     * @return True iff the Class has a default constructor
     * @return (conditional) the default constructor
     */
    conditional function DataType() defaultConstructor(OuterType? outer = Null)
        {
        Constructor[] constructors = this.constructors;
        if (constructors.size == 0)
            {
            return False;
            }

        if (Class clz := fromClass(), clz.virtualChild)
            {
            assert:arg outer != Null;
            for (val fn : constructors)
                {
                if (fn.ParamTypes.size == 1)
                    {
                    assert fn.ParamTypes[0] == OuterType;
                    return True, fn.as(function DataType(OuterType)).
                        bind(fn.params[0].as(Parameter<OuterType>), outer)
                            .as(function DataType());
                    }
                }
            }
        else
            {
            for (val fn : constructors)
                {
                if (fn.ParamTypes.size == 0)
                    {
                    return True, fn.as(function DataType());
                    }
                }
            }

        return False;
        }

    /**
     * Helper method to locate the structure-based constructor.
     *
     * @return True iff the Class has a default constructor
     * @return (conditional) the default constructor
     */
    conditional function DataType(Struct) structConstructor(OuterType? outer = Null)
        {
        if (Class clz := fromClass(), clz.virtualChild)
            {
            assert:arg outer != Null;
            for (val fn : constructors)
                {
                if (fn.ParamTypes.size == 2 && fn.ParamTypes[1].is(Type!<Struct>))
                    {
                    assert fn.ParamTypes[0] == OuterType;
                    return True, structure -> fn.invoke((outer, structure))[0];
                    }
                }
            }
        else
            {
            for (val fn : constructors)
                {
                if (fn.ParamTypes.size == 1 && fn.ParamTypes[0].is(Type!<Struct>))
                    {
                    return True, structure -> fn.invoke(Tuple:(structure))[0];
                    }
                }
            }

        return False;
        }


    // ----- Comparable, Hashable, and Orderable ---------------------------------------------------

    static <CompileType extends Type> Int hashCode(CompileType value)
        {
        return value.methods   .hashCode()
             ^ value.properties.hashCode();
        }

    static <CompileType extends Type> Boolean equals(CompileType value1, CompileType value2)
        {
        // the definition for type equality is fairly simple: each of the two types must be
        // type-compatible with the other
        return value1.isA(value2) && value2.isA(value1);
        }

    static <CompileType extends Type> Ordered compare(CompileType value1, CompileType value2)
        {
        if (value1 == value2)
            {
            return Equal;
            }

        TODO <=>
        }
    }


