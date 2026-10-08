package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.gui.ArrowRouter;
import com.sbancuz.plannh.gui.PortGeometry;

/**
 * Boundary chips sit in the corridor edges run down, so the router is given them as obstacles.
 * That is only an improvement if the routes still exist afterwards: a chip parked in front of a
 * pin can seal the only approach to it, and a request the router cannot serve falls back to a
 * straight line that ignores every obstacle - the exact overlap the chips were made obstacles to
 * prevent, but worse, because now it is silent.
 */
class ChipRoutingTest {

    private static final int NODE_W = 160;
    private static final int NODE_H = 120;
    private static final int CHIP_H = 11;
    private static final int CHIP_GAP = 8;

    private static ArrowRouter.Rect node(final int x, final int y) {
        return new ArrowRouter.Rect(x, y, NODE_W, NODE_H);
    }

    /** A supply chip hanging off input {@code port} of a node at {@code (x, y)}. */
    private static ArrowRouter.Rect chip(final int x, final int y, final int port, final int width) {
        return new ArrowRouter.Rect(x - CHIP_GAP - width, y + PortGeometry.portY(port) - CHIP_H / 2, width, CHIP_H);
    }

    private static boolean inside(final int[] p, final ArrowRouter.Rect r) {
        return p[0] > r.x() && p[0] < r.x() + r.w() && p[1] > r.y() && p[1] < r.y() + r.h();
    }

    /** Corners are the waypoints between the first and last: those are the port anchors. */
    private static boolean turnsInside(final List<int[]> route, final ArrowRouter.Rect r) {
        for (int i = 1; i < route.size() - 1; i++) {
            if (inside(route.get(i), r)) return true;
        }
        return false;
    }

    @Test
    void anEdgePastAChipNeverTurnsUnderIt() {
        // Image-2 geometry: a left node feeding a right node whose first input carries a supply
        // chip, plus through-traffic to the right node's second input.
        final UUID through = UUID.nameUUIDFromBytes("through".getBytes());
        final int rightX = 700;
        final ArrowRouter.Rect supplyChip = chip(rightX, 0, 0, 120);

        final List<ArrowRouter.Rect> obstacles = new ArrayList<>(List.of(node(0, 0), node(rightX, 0)));
        final List<ArrowRouter.Request> requests = List
            .of(new ArrowRouter.Request(through, NODE_W, PortGeometry.portY(0), rightX, PortGeometry.portY(1)));

        final Map<UUID, List<int[]>> routes = new ArrowRouter(6, 12).route(obstacles, List.of(supplyChip), requests);
        final List<int[]> route = routes.get(through);

        assertNotNull(route, "the router must still find a way in");
        assertTrue(route.size() >= 2, "and it must be a real path, not an empty fallback");
        assertFalse(turnsInside(route, supplyChip), "and it must not corner under the label");
    }

    @Test
    void everyPinStaysReachableWithAChipOnEveryInput() {
        // The pathological case the obstacles create: every input of the target node has a chip in
        // front of it, so every approach corridor is partly sealed. If any request comes back
        // unroutable the canvas silently draws a straight line through everything.
        final int rightX = 700;
        final int ports = 4;
        final List<ArrowRouter.Rect> obstacles = new ArrayList<>(List.of(node(0, 0), node(rightX, 0)));
        final List<ArrowRouter.Rect> chips = new ArrayList<>();
        final List<ArrowRouter.Request> requests = new ArrayList<>();
        for (int i = 0; i < ports; i++) {
            chips.add(chip(rightX, 0, i, 120));
            requests.add(
                new ArrowRouter.Request(
                    UUID.nameUUIDFromBytes(("e" + i).getBytes()),
                    NODE_W,
                    PortGeometry.portY(i),
                    rightX,
                    PortGeometry.portY(i)));
        }

        final Map<UUID, List<int[]>> routes = new ArrowRouter(6, 12).route(obstacles, chips, requests);
        for (final ArrowRouter.Request request : requests) {
            final List<int[]> route = routes.get(request.key());
            assertNotNull(route, () -> "no route to pin " + request.dy() + "; the canvas would draw straight through");
            assertTrue(route.size() >= 2, () -> "empty route to pin " + request.dy());
            for (final ArrowRouter.Rect chip : chips) {
                assertFalse(turnsInside(route, chip), () -> "cornered under a label reaching pin " + request.dy());
            }
        }
    }

    @Test
    void aCornerNeverLandsFlushAgainstALabelsEdge() {
        // A turn sitting exactly on the chip's boundary still reads as a turn in the chip. The zone
        // is padded so the corner has to clear it, which is only meaningful if the padding is at
        // least the grid's own resolution - below that it rounds away on some edges and not others.
        final int rightX = 700;
        final UUID key = UUID.nameUUIDFromBytes("flush".getBytes());
        final ArrowRouter.Rect chipRect = chip(rightX, 0, 0, 120);
        final List<ArrowRouter.Request> requests = List
            .of(new ArrowRouter.Request(key, NODE_W, PortGeometry.portY(2), rightX, PortGeometry.portY(0)));

        final List<int[]> route = new ArrowRouter(6, 12)
            .route(List.of(node(0, 0), node(rightX, 0)), List.of(chipRect), requests)
            .get(key);

        final ArrowRouter.Rect grown = new ArrowRouter.Rect(
            chipRect.x() - 1,
            chipRect.y() - 1,
            chipRect.w() + 2,
            chipRect.h() + 2);
        assertFalse(turnsInside(route, grown), "a corner must clear the label, not graze it");
    }

    @Test
    void aRequestThatFallsBackSaysSo() {
        // The fallback is the only route that can ignore an obstacle, so it has to be reportable;
        // otherwise a bad-looking arrow and a gave-up arrow are indistinguishable after the fact.
        final int rightX = 700;
        final UUID key = UUID.nameUUIDFromBytes("sealed".getBytes());
        final ArrowRouter.Rect chipRect = chip(rightX, 0, 0, 120);
        final List<ArrowRouter.Request> requests = List
            .of(new ArrowRouter.Request(key, NODE_W, PortGeometry.portY(0), rightX, PortGeometry.portY(0)));

        final Set<UUID> fellBack = new HashSet<>();
        new ArrowRouter(6, 12).route(List.of(node(0, 0), node(rightX, 0), chipRect), List.of(), requests, fellBack);
        assertTrue(fellBack.contains(key), "a sealed approach must be reported, not just drawn");

        final Set<UUID> clean = new HashSet<>();
        new ArrowRouter(6, 12).route(List.of(node(0, 0), node(rightX, 0)), List.of(chipRect), requests, clean);
        assertTrue(clean.isEmpty(), "and a no-turn zone must not provoke one");
    }

    @Test
    void aChipInFrontOfAPinDoesNotSealIt() {
        // The reason chips are no-turn zones and not obstacles. A chip hangs directly on its own
        // port's approach, so blocked outright there is no way in at all; A* returns nothing and
        // the canvas falls back to a straight Z that honours no obstacle whatsoever. With a node
        // sitting in the corridor, that fallback drives straight through it - which is a worse
        // picture than the overlap the blocking was meant to prevent, and silent besides.
        final int rightX = 700;
        final UUID key = UUID.nameUUIDFromBytes("blocked".getBytes());
        final ArrowRouter.Rect chip = chip(rightX, 0, 0, 120);
        final ArrowRouter.Rect inTheWay = node(300, -20);
        final List<ArrowRouter.Rect> nodes = List.of(node(0, 0), node(rightX, 0), inTheWay);
        final List<ArrowRouter.Request> requests = List
            .of(new ArrowRouter.Request(key, NODE_W, PortGeometry.portY(0), rightX, PortGeometry.portY(0)));

        final List<int[]> sealed = new ArrowRouter(6, 12)
            .route(new ArrayList<>(List.of(node(0, 0), node(rightX, 0), inTheWay, chip)), requests)
            .get(key);
        final List<int[]> passable = new ArrowRouter(6, 12).route(nodes, List.of(chip), requests)
            .get(key);

        assertTrue(crossesRect(sealed, inTheWay), "sealing the pin forces the fallback through the node");
        assertFalse(crossesRect(passable, inTheWay), "leaving it passable lets the router go around");
        assertFalse(turnsInside(passable, chip), "and it still does not corner under the label");
    }

    /** Whether any segment of the route enters the rectangle. */
    private static boolean crossesRect(final List<int[]> route, final ArrowRouter.Rect r) {
        for (int i = 1; i < route.size(); i++) {
            final int[] a = route.get(i - 1);
            final int[] b = route.get(i);
            for (int t = 0; t <= 200; t++) {
                final int px = a[0] + (b[0] - a[0]) * t / 200;
                final int py = a[1] + (b[1] - a[1]) * t / 200;
                if (px > r.x() && px < r.x() + r.w() && py > r.y() && py < r.y() + r.h()) return true;
            }
        }
        return false;
    }
}
