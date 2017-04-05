const Nibble
    {
    construct Bit(Bit[] bits)
        {
        assert:always bits.length == 4;
        this.bits = bits;
        }

    construct Bit(Int n)
        {
        assert:always n >= 0 && n <= 0xF;
        // TODO this. = ;
        }

    private Bit[] bits;

    static Nibble defaultValue = 0;

    Char to<Char>()
        {
        Byte b = to<Byte>();
        if (b >= 0 && b <= 9)
            {
            b = '0'.to<Byte>() + b;
            }
        else
            {
            b = 'A'.to<Byte>() + b - 0xA;
            }
        return new Char(b);
        }

    @auto Byte to<Byte>()
        {
        return    (bits[0] == 0 ? 0 : 1)
                + (bits[1] == 0 ? 0 : 2)
                + (bits[2] == 0 ? 0 : 4)
                + (bits[3] == 0 ? 0 : 8);
        }

    @auto Int to<Int>()
        {
        return to<Byte>().to<Int>();
        }

    @auto UInt to<UInt>()
        {
        return to<Byte>().to<UInt>();
        }
    }
