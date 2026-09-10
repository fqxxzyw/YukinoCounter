package com.yukino.counter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KmCounterImporterTest {
    @Test void mapsNormalAndExtendedScanCodes() {
        assertEquals('A', KmCounterImporter.scanCodeToVirtualKey(30));
        assertEquals(KeyNames.NUMPAD_ENTER, KmCounterImporter.scanCodeToVirtualKey(284));
        assertEquals(38, KmCounterImporter.scanCodeToVirtualKey(328));
        assertEquals(161, KmCounterImporter.scanCodeToVirtualKey(310));
        assertEquals(127, KmCounterImporter.scanCodeToVirtualKey(103));
    }
}
