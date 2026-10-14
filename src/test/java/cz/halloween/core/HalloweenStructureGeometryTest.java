package cz.halloween.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HalloweenStructureGeometryTest {
    @Test
    void automaticFootprintFitsOnlyWhenEntirelyInsideAnchorChunk() {
        assertTrue(HalloweenStructureGeometry.footprintFitsChunk(0, 0, 8, 8, 7));
        assertTrue(HalloweenStructureGeometry.footprintFitsChunk(-1, -1, -8, -8, 7));
        assertFalse(HalloweenStructureGeometry.footprintFitsChunk(0, 0, 8, 8, 8));
        assertFalse(HalloweenStructureGeometry.footprintFitsChunk(0, 0, 8, 8, -1));
    }

    @Test
    void rotationsPreserveFootprintAndReturnToOriginAfterFourTurns() {
        int x = 2;
        int z = -5;
        for (int rotation = 0; rotation < 4; rotation++) {
            HalloweenStructureGeometry.Offset offset = HalloweenStructureGeometry.rotate(rotation, x, z);
            assertEquals(x * x + z * z, offset.x() * offset.x() + offset.z() * offset.z());
        }
        HalloweenStructureGeometry.Offset turn1 = HalloweenStructureGeometry.rotate(1, x, z);
        assertEquals(5, turn1.x());
        assertEquals(2, turn1.z());
        HalloweenStructureGeometry.Offset turn4 = HalloweenStructureGeometry.rotate(4, x, z);
        assertEquals(x, turn4.x());
        assertEquals(z, turn4.z());
    }

    @Test
    void circularAndSquareFootprintsUseTheirIntendedBounds() {
        assertTrue(HalloweenStructureGeometry.insideFootprint(3, 4, 5, true));
        assertFalse(HalloweenStructureGeometry.insideFootprint(4, 4, 5, true));
        assertTrue(HalloweenStructureGeometry.insideFootprint(5, 5, 5, false));
        assertFalse(HalloweenStructureGeometry.insideFootprint(6, 0, 5, false));
    }
}
