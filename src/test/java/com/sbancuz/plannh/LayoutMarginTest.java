package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.gui.ArrowRouter;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.layout.Box;
import com.sbancuz.plannh.gui.layout.ElkLayoutStrategy;
import com.sbancuz.plannh.gui.layout.LayoutMachine;
import com.sbancuz.plannh.gui.layout.LayoutPlan;
import com.sbancuz.plannh.gui.layout.LayoutRelation;
import com.sbancuz.plannh.gui.layout.LayoutRequest;
import com.sbancuz.plannh.gui.layout.LayoutSettings;

/**
 * The spacing contract between the layout engine and the arrow router.
 *
 * <p>
 * <b>Not a rewrite of the previous four cases — a replacement for them.</b> They pinned the
 * {@code credited()} padding arithmetic that reserved room for the boundary chips drawn beside a machine,
 * and chips are not layout any more: they are drawn outside the machine's box and are never counted, so
 * the whole apparatus is gone. What replaces it is the one spacing number that genuinely couples the two
 * systems — the gap between columns, which <em>is</em> the router's corridor.
 */
public class LayoutMarginTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static LayoutRequest chain() {
        return new LayoutRequest(
            List.of(new LayoutMachine(A, "a", 100, 60), new LayoutMachine(B, "b", 100, 60)),
            List.of(new LayoutRelation(new UUID(0, 3), A, B)),
            List.of(),
            List.of());
    }

    private static LayoutPlan layout(final int layerSpacing) {
        return new ElkLayoutStrategy().layout(chain(), new LayoutSettings(20, layerSpacing, 7, 300, 200));
    }

    /** The clear gap between the two machines, measured at their boxes rather than at their origins. */
    private static int corridor(final LayoutPlan plan) {
        final Box a = new Box(
            plan.machines()
                .get(A)
                .x(),
            plan.machines()
                .get(A)
                .y(),
            100,
            60);
        final Box b = new Box(
            plan.machines()
                .get(B)
                .x(),
            plan.machines()
                .get(B)
                .y(),
            100,
            60);
        return Math.max(b.x() - a.right(), a.x() - b.right());
    }

    @Test
    @DisplayName("the router's corridor is its own margin, twice, plus a cell")
    public void corridorIsDerivedNotGuessed() {
        // This is the number an earlier attempt transcribed into the layout settings as 24, commented as
        // "twice the margin plus one cell" - which is 30 - and then never called from production. Asking
        // the router instead means the two cannot disagree.
        final ArrowRouter router = new ArrowRouter(CanvasWidget.ROUTE_CELL, CanvasWidget.ROUTE_MARGIN);

        assertEquals(2 * CanvasWidget.ROUTE_MARGIN + CanvasWidget.ROUTE_CELL, router.requiredCorridor());
        assertEquals(router.requiredCorridor(), CanvasWidget.requiredRouteCorridor());
    }

    @Test
    @DisplayName("the configured layer spacing is what the engine is given")
    public void configuredSpacingIsHonoured() {
        // Generous settings, generous gap: with nothing else in the way the observed corridor should track
        // the request, which is what "the layout engine is told how much room the router needs" means.
        final int asked = 200;

        assertEquals(asked, corridor(layout(asked)), "the engine ignored the spacing it was given");
    }

    @Test
    @DisplayName("a cramped spacing still leaves the router its corridor")
    public void crampedSpacingIsRaisedToTheCorridor() {
        // The engine cannot know what the router needs, so the caller clamps. Asserted here at the level
        // the clamp actually lives: Config.layoutSettings, which takes the floor as an argument rather
        // than restating it.
        final int floor = CanvasWidget.requiredRouteCorridor();
        final LayoutSettings clamped = Config.layoutSettings(floor, Group.GROUP_MIN_W, Group.GROUP_MIN_H);

        assertTrue(
            clamped.layerSpacing() >= floor,
            "a layer spacing of " + clamped.layerSpacing() + " is below the router's " + floor);
    }
}
