package com.shortlink.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Base62Test {
    @Test
    void encode_should_return_expected(){
        assertEquals("1",Base62.encode(1));
        assertEquals("9",Base62.encode(9));
        assertEquals("a",Base62.encode(10));
        assertEquals("z",Base62.encode(35));
        assertEquals("A",Base62.encode(36));
        assertEquals("Z",Base62.encode(61));
        assertEquals("10",Base62.encode(62));
        assertEquals("ZZ",Base62.encode(3843));
    }

    @Test
    void encode_zero_should_return_empty(){
        assertEquals("",Base62.encode(0));
    }

}
