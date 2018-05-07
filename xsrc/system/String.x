const String
        implements Sequence<Char>
    {
    private construct() {}

    private Char[] chars;

    conditional Int indexOf(String value, Range<Int>? range = null)
        {
        TODO - native
        }

    Char get(Int index);

    @Override
    Iterator<Char> iterator()
        {
        TODO -- native
        }

    String! reify()
        {
        if (this instanceof StringSub)
            {

            }

        return this;
        }

    StringAscii to<StringAscii>()
        {
        TODO -- native
        }

    StringUtf8 to<StringUtf8>()
        {
        TODO -- native
        }
    StringUtf16 to<StringUtf16>()
        {
        TODO -- native
        }
    StringUtf32 to<StringUtf32>()
        {
        TODO -- native
        }

    @Override
    String to<String>()
        {
        return this;
        }

    // TODO Sequence<Char>

    @Override
    Int size.get()
        {
        return chars.length;
        }

    Char[] to<Char[]>()
        {
        return chars;
        }

    @Op("*") String! dup(Int n)
        {
        TODO
        }

    @Op("+") String append(Object o)
        {
        TODO
        }

    //
    const StringAscii(Byte[] bytes)
        {
        construct(Byte[] bytes)
            {
            for (Byte b : bytes)
                {
                assert:always b <= 0x7F;
                }

            this.bytes = bytes.reify();
            }

        construct(Sequence<Char> seq)
            {
            Byte[] bytes  = new Byte[seq.length];
            Int    offset = 0;
            for (Char ch : seq)
                {
                Int n = ch.codepoint;
                assert:always n >= 0 && n <= 0x7F;
                bytes[offset++] = n.to<Byte>();
                }

            this.bytes = bytes;
            }

        Char get(Int index)
            {
            return bytes[index].to<Char>();
            }
        }

    const StringUtf8(Byte[] bytes)
        {
        // TODO
        }

    const StringUtf16(UInt16[] int16s)
        {
        // TODO
        }

    const StringUtf32(UInt32[] int32s)
        {
        // TODO
        }

    const StringSub
        {
        private String source;
        private Int offset;
        private Int length;

        private construct(String source, Int offset, Int length)
            {
            assert:always offset >= 0;
            assert:always length >= 0;
            assert:always offset + length <= source.length;

            this.source = source;
            this.offset = offset;
            this.length = length;
            }
        }
    }
