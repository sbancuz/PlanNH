package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Orthogonal connector router. Routes arrows between recipe-node ports using only 90-degree segments,
 * avoiding node boxes and previously routed arrows, minimizing the number of bends and keeping a
 * consistent minimum spacing between arrows and around boxes.
 * <p>
 * All coordinates are in graph/world space (un-zoomed, un-panned). Routing in world space keeps the
 * produced paths stable while the user pans (a pure translation) and lets the caller cache them.
 * <p>
 * The router lays a uniform grid over the bounding region and runs A* where the search state is
 * {@code (cell, incoming-direction)} so that turns can be penalized. Node rectangles are hard
 * obstacles; their surrounding margin ring and cells already occupied by a routed arrow carry soft
 * penalties, so arrows keep clearance where there is room but can squeeze through tight gaps.
 */
public final class ArrowRouter {

    /** Per-cell movement cost. */
    private static final int STEP = 1;
    /**
     * Extra cost for changing direction. High relative to STEP so paths never micro-dodge
     * around mild congestion - a single sideways jog costs two turns, which no small
     * occupancy saving can justify.
     */
    private static final int TURN = 24;
    /**
     * Extra cost per cell that already carries another arrow. Kept low relative to STEP so
     * that sharing even a long congested corridor stays cheaper than detouring around the
     * outside of the chart.
     */
    private static final int ARROW = 2;
    /** Smaller penalty around an occupied cell, so arrows keep at least one cell of clearance. */
    private static final int NEAR = 1;
    /**
     * Penalty per cell inside a node's margin ring. The ring is deliberately soft, not blocked:
     * nodes placed with a gap smaller than two margins would otherwise seal the gap shut and
     * force absurd whole-chart detours. Arrows avoid hugging nodes when there is room, but can
     * squeeze through tight gaps when there is not.
     */
    private static final int MARGIN_COST = 8;
    /**
     * Penalty for routing through another edge's port anchor (the straight approach run just
     * right of an output pin / just left of an input pin). Anchors stay visually clear so every
     * arrow's first and last segment reads as belonging to its port.
     */
    private static final int ANCHOR_COST = 30;

    /** Hard cap on grid cells; the cell size is grown if a region would exceed it. */
    private static final int MAX_CELLS = 200_000;
    /** Padding added around the bounding region so arrows can route around outer nodes. */
    private static final int PAD = 48;

    // (+x, -x, +y, -y); reverse of d is (d ^ 1).
    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    /** A rectangular obstacle (a recipe node) in world space. */
    public record Rect(int x, int y, int w, int h) {}

    /** A single arrow to route, from a source output port to a target input port. */
    public record Request(UUID key, int sx, int sy, int dx, int dy) {}

    private final int baseCell;
    private final int margin;

    /**
     * @param cell   nominal grid cell size in world units (also the minimum spacing granularity)
     * @param margin clearance kept around node boxes in world units
     */
    public ArrowRouter(final int cell, final int margin) {
        this.baseCell = Math.max(1, cell);
        this.margin = Math.max(0, margin);
    }

    /**
     * Routes every request. Requests are routed in order; each routed arrow makes the cells it uses
     * less attractive to subsequent arrows, which spreads parallel connections apart.
     *
     * @return a map from {@link Request#key()} to a list of {@code {x, y}} world-space waypoints
     */
    public Map<UUID, List<int[]>> route(final List<Rect> obstacles, final List<Request> requests) {
        final Map<UUID, List<int[]>> result = new HashMap<>();
        if (requests.isEmpty()) return result;

        // ── Bounding region ──
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (final Rect r : obstacles) {
            minX = Math.min(minX, r.x - margin);
            minY = Math.min(minY, r.y - margin);
            maxX = Math.max(maxX, r.x + r.w + margin);
            maxY = Math.max(maxY, r.y + r.h + margin);
        }
        final int stub = margin + baseCell;
        for (final Request q : requests) {
            minX = Math.min(minX, Math.min(q.sx, q.dx) - stub);
            minY = Math.min(minY, Math.min(q.sy, q.dy));
            maxX = Math.max(maxX, Math.max(q.sx, q.dx) + stub);
            maxY = Math.max(maxY, Math.max(q.sy, q.dy));
        }
        minX -= PAD;
        minY -= PAD;
        maxX += PAD;
        maxY += PAD;

        // Grow the cell if the region would need too many cells.
        int cell = baseCell;
        int cols, rows;
        while (true) {
            cols = (maxX - minX) / cell + 1;
            rows = (maxY - minY) / cell + 1;
            if ((long) cols * rows <= MAX_CELLS || cell > 4096) break;
            cell *= 2;
        }

        final Grid grid = new Grid(minX, minY, cols, rows, cell, stub);
        grid.blockObstacles(obstacles, margin);
        grid.reserveAnchors(requests);

        for (int i = 0; i < requests.size(); i++) {
            final Request q = requests.get(i);
            // Ports closer than two anchor stubs cannot satisfy the leave-right/arrive-right
            // state machine without looping around themselves; draw the canonical Z directly.
            if (q.dx - q.sx < 2 * stub && Math.abs(q.dy - q.sy) < 10 * baseCell) {
                result.put(q.key, fallback(q, stub));
                continue;
            }
            final List<int[]> cellPath = grid.search(q, i);
            final List<int[]> path;
            if (cellPath == null) {
                path = fallback(q, stub);
            } else {
                path = grid.toWorld(cellPath, q);
                grid.occupy(cellPath);
            }
            result.put(q.key, path);
        }
        return result;
    }

    /** Simple direct route used when A* finds no path (degenerate layouts). */
    private static List<int[]> fallback(final Request q, final int stub) {
        final int midX = (q.sx + stub + q.dx - stub) / 2;
        final List<int[]> p = new ArrayList<>(4);
        p.add(new int[] { q.sx, q.sy });
        p.add(new int[] { midX, q.sy });
        p.add(new int[] { midX, q.dy });
        p.add(new int[] { q.dx, q.dy });
        return p;
    }

    private static final class Grid {

        final int originX, originY, cols, rows, cell, stub;
        final boolean[] blocked;
        final int[] occupancy;
        final int[] anchorOwner;
        final int[] gScore;
        final int[] cameFrom;
        final int[] scoreRun;
        int runId = 1;

        Grid(final int originX, final int originY, final int cols, final int rows, final int cell, final int stub) {
            this.originX = originX;
            this.originY = originY;
            this.cols = cols;
            this.rows = rows;
            this.cell = cell;
            this.stub = stub;
            this.blocked = new boolean[cols * rows];
            this.occupancy = new int[cols * rows];
            this.anchorOwner = new int[cols * rows];
            Arrays.fill(anchorOwner, -1);
            final int states = cols * rows * 4;
            this.gScore = new int[states];
            this.cameFrom = new int[states];
            this.scoreRun = new int[states];
        }

        int gx(final int wx) {
            return Math.clamp((wx - originX) / cell, 0, cols - 1);
        }

        int gy(final int wy) {
            return Math.clamp((wy - originY) / cell, 0, rows - 1);
        }

        int centerX(final int gx) {
            return originX + gx * cell + cell / 2;
        }

        int centerY(final int gy) {
            return originY + gy * cell + cell / 2;
        }

        void blockObstacles(final List<Rect> obstacles, final int margin) {
            for (final Rect r : obstacles) {
                final int x0 = gx(r.x - margin), x1 = gx(r.x + r.w + margin);
                final int y0 = gy(r.y - margin), y1 = gy(r.y + r.h + margin);
                final int bx0 = gx(r.x), bx1 = gx(r.x + r.w);
                final int by0 = gy(r.y), by1 = gy(r.y + r.h);
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        if (x >= bx0 && x <= bx1 && y >= by0 && y <= by1) {
                            blocked[y * cols + x] = true;
                        } else {
                            occupancy[y * cols + x] += MARGIN_COST;
                        }
                    }
                }
            }
        }

        /** Marks every request's port-approach runs so other arrows keep out of them. */
        void reserveAnchors(final List<Request> requests) {
            for (int i = 0; i < requests.size(); i++) {
                final Request q = requests.get(i);
                markAnchor(gx(q.sx), gx(q.sx + stub), gy(q.sy), i);
                markAnchor(gx(q.dx - stub), gx(q.dx), gy(q.dy), i);
            }
        }

        private void markAnchor(final int x0, final int x1, final int y, final int owner) {
            for (int x = x0; x <= x1; x++) {
                final int idx = y * cols + x;
                if (anchorOwner[idx] == -1) anchorOwner[idx] = owner;
            }
        }

        void occupy(final List<int[]> cellPath) {
            for (final int[] c : cellPath) {
                final int gx = c[0], gy = c[1];
                occupancy[gy * cols + gx] += ARROW;
                for (int d = 0; d < 4; d++) {
                    final int nx = gx + DX[d], ny = gy + DY[d];
                    if (nx >= 0 && nx < cols && ny >= 0 && ny < rows) {
                        occupancy[ny * cols + nx] += NEAR;
                    }
                }
            }
        }

        /** A* over (cell, direction); returns the list of {@code {gx, gy}} cells or null. */
        List<int[]> search(final Request q, final int requestIndex) {
            final int sgx = gx(q.sx + stub), sgy = gy(q.sy);
            final int ggx = gx(q.dx - stub), ggy = gy(q.dy);
            final int run = nextRun();

            // Start heading +x (direction 0) so the arrow leaves the output port to the right.
            final int startState = ((sgy * cols + sgx) * 4);
            setScore(startState, 0, -1, run);

            final PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt((int[] a) -> a[0]));
            open.add(new int[] { heuristic(sgx, sgy, ggx, ggy), startState, 0 });

            while (!open.isEmpty()) {
                final int[] top = open.poll();
                final int state = top[1];
                final int g = top[2];
                if (g > score(state, run)) continue;

                final int idx = state >> 2;
                final int dir = state & 3;
                final int cx = idx % cols;
                final int cy = idx / cols;

                if (cx == ggx && cy == ggy && dir == 0) {
                    return reconstruct(cameFrom, state);
                }

                for (int nd = 0; nd < 4; nd++) {
                    if (nd == (dir ^ 1)) continue; // no immediate U-turn
                    final int nx = cx + DX[nd];
                    final int ny = cy + DY[nd];
                    if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue;
                    final int nIdx = ny * cols + nx;
                    if (blocked[nIdx]) continue;

                    final int foreignAnchor = anchorOwner[nIdx] != -1 && anchorOwner[nIdx] != requestIndex ? ANCHOR_COST
                        : 0;
                    final int cost = STEP + (nd != dir ? TURN : 0) + occupancy[nIdx] + foreignAnchor;
                    final int ng = g + cost;
                    final int nState = nIdx * 4 + nd;
                    if (ng < score(nState, run)) {
                        setScore(nState, ng, state, run);
                        open.add(new int[] { ng + heuristic(nx, ny, ggx, ggy), nState, ng });
                    }
                }
            }
            return null;
        }

        /** Manhattan lower bound used by A* to prefer cells closer to the target. */
        private static int heuristic(final int x, final int y, final int gx, final int gy) {
            return (Math.abs(x - gx) + Math.abs(y - gy)) * STEP;
        }

        private int nextRun() {
            if (runId == Integer.MAX_VALUE) {
                Arrays.fill(scoreRun, 0);
                runId = 1;
            }
            return runId++;
        }

        private int score(final int state, final int run) {
            return scoreRun[state] == run ? gScore[state] : Integer.MAX_VALUE;
        }

        private void setScore(final int state, final int score, final int previousState, final int run) {
            scoreRun[state] = run;
            gScore[state] = score;
            cameFrom[state] = previousState;
        }

        private List<int[]> reconstruct(final int[] cameFrom, final int goalState) {
            final List<int[]> cells = new ArrayList<>();
            int s = goalState;
            while (s != -1) {
                final int idx = s >> 2;
                cells.add(new int[] { idx % cols, idx / cols });
                s = cameFrom[s];
            }
            java.util.Collections.reverse(cells);
            return cells;
        }

        /** Converts a cell path to world waypoints, snapping the stub segments to the exact ports. */
        List<int[]> toWorld(final List<int[]> cells, final Request q) {
            // Keep only corners (cells where the direction changes), plus the two ends.
            final List<int[]> corners = new ArrayList<>();
            corners.add(cells.getFirst());
            for (int i = 1; i < cells.size() - 1; i++) {
                final int[] a = cells.get(i - 1), b = cells.get(i), c = cells.get(i + 1);
                final int d1x = Integer.signum(b[0] - a[0]), d1y = Integer.signum(b[1] - a[1]);
                final int d2x = Integer.signum(c[0] - b[0]), d2y = Integer.signum(c[1] - b[1]);
                if (d1x != d2x || d1y != d2y) corners.add(b);
            }
            if (cells.size() > 1) corners.add(cells.getLast());

            final int m = corners.size();
            final int[] xs = new int[m];
            final int[] ys = new int[m];
            for (int i = 0; i < m; i++) {
                xs[i] = centerX(corners.get(i)[0]);
                ys[i] = centerY(corners.get(i)[1]);
            }
            // Snap the source stub run to the port's exact Y (removes the half-cell jog at the port).
            ys[0] = q.sy;
            if (m >= 2 && corners.get(0)[1] == corners.get(1)[1]) ys[1] = q.sy;
            // Snap the target stub run to the port's exact Y.
            ys[m - 1] = q.dy;
            if (m >= 2 && corners.get(m - 1)[1] == corners.get(m - 2)[1]) ys[m - 2] = q.dy;

            final List<int[]> pts = new ArrayList<>(m + 2);
            pts.add(new int[] { q.sx, q.sy });
            for (int i = 0; i < m; i++) pts.add(new int[] { xs[i], ys[i] });
            pts.add(new int[] { q.dx, q.dy });

            return simplify(orthogonalize(pts));
        }
    }

    /** Inserts an L-bend for any accidental diagonal so the polyline stays strictly orthogonal. */
    private static List<int[]> orthogonalize(final List<int[]> pts) {
        final List<int[]> out = new ArrayList<>(pts.size() + 4);
        out.add(pts.getFirst());
        for (int i = 1; i < pts.size(); i++) {
            final int[] p = out.getLast();
            final int[] c = pts.get(i);
            if (p[0] != c[0] && p[1] != c[1]) {
                out.add(new int[] { c[0], p[1] });
            }
            out.add(c);
        }
        return out;
    }

    /** Drops duplicate points and merges collinear runs so only real corners remain. */
    private static List<int[]> simplify(final List<int[]> pts) {
        final List<int[]> out = new ArrayList<>(pts.size());
        for (final int[] p : pts) {
            if (!out.isEmpty()) {
                final int[] last = out.getLast();
                if (last[0] == p[0] && last[1] == p[1]) continue;
            }
            out.add(p);
        }
        for (int i = 1; i < out.size() - 1;) {
            final int[] a = out.get(i - 1), b = out.get(i), c = out.get(i + 1);
            final boolean collinear = (a[0] == b[0] && b[0] == c[0]) || (a[1] == b[1] && b[1] == c[1]);
            if (collinear) out.remove(i);
            else i++;
        }
        return out;
    }
}
