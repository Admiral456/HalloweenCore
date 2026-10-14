package cz.halloween.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HalloweenStructureGeometryTest {
    @Test
    void rotatesLocalOffsetsExactlyForAllQuarterTurns() {
        int dx = 3;
        int dz = 5;

        assertEquals(3, HalloweenStructureGeometry.rotateX(dx, dz, 0));
        assertEquals(5, HalloweenStructureGeometry.rotateZ(dx, dz, 0));

        assertEquals(-5, HalloweenStructureGeometry.rotateX(dx, dz, 1));
        assertEquals(3, HalloweenStructureGeometry.rotateZ(dx, dz, 1));

        assertEquals(-3, HalloweenStructureGeometry.rotateX(dx, dz, 2));
        assertEquals(-5, HalloweenStructureGeometry.rotateZ(dx, dz, 2));

        assertEquals(5, HalloweenStructureGeometry.rotateX(dx, dz, 3));
        assertEquals(-3, HalloweenStructureGeometry.rotateZ(dx, dz, 3));
    }

    @Test
    void circularFootprintIncludesBoundaryButRejectsOutsideCells() {
        assertTrue(HalloweenStructureGeometry.withinCircle(3, 4, 5));
        assertTrue(HalloweenStructureGeometry.withinCircle(-5, 0, 5));
        assertFalse(HalloweenStructureGeometry.withinCircle(4, 4, 5));
    }

    @Test
    void arenaRadiiMatchConfiguredFootprints() {
        assertEquals(10, HalloweenStructureGeometry.radius("cemetery"));
        assertEquals(17, HalloweenStructureGeometry.radius("village"));
        assertEquals(20, HalloweenStructureGeometry.radius("arena"));
        assertEquals(22, HalloweenStructureGeometry.radius("goblinarena"));
        assertEquals(48, HalloweenStructureGeometry.radius("vampirearena"));
        assertEquals(97, HalloweenStructureGeometry.radius("vampirearena") * 2 + 1);
        assertEquals(15, HalloweenStructureGeometry.clearance("vampirearena"));
        assertThrows(IllegalArgumentException.class, () -> HalloweenStructureGeometry.radius("unknown"));
    }

    @Test
    void structureCommandAliasesNormalizeWithoutCoordinatesOrConfirmation() {
        assertEquals("cemetery", HalloweenStructureGeometry.normalizeType("hřbitov"));
        assertEquals("cemetery", HalloweenStructureGeometry.normalizeType("hrbitov"));
        assertEquals("village", HalloweenStructureGeometry.normalizeType("VESNICE"));
        assertEquals("arena", HalloweenStructureGeometry.normalizeType("aréna"));
        assertEquals("vampirearena", HalloweenStructureGeometry.normalizeType("vampire"));
        assertEquals("goblinarena", HalloweenStructureGeometry.normalizeType("goblin-arena"));
        assertNull(HalloweenStructureGeometry.normalizeType("confirm"));
        assertNull(HalloweenStructureGeometry.normalizeType(null));
    }

    @Test
    void variantSelectionIsDeterministicAndBounded() {
        int first = HalloweenStructureGeometry.variant(1234L, -30, 910);
        assertEquals(first, HalloweenStructureGeometry.variant(1234L, -30, 910));
        for (int x = -20; x < 20; x++) {
            int variant = HalloweenStructureGeometry.variant(1234L, x, 42);
            assertTrue(variant >= 0 && variant < 3);
        }
    }
}
