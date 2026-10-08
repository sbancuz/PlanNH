package com.sbancuz.plannh.data.flowchart.balancer.alternatives;

import java.util.List;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.External;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.PortRef;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;

/**
 * One answer the user may pick. A chart with several open gates poses several independent
 * questions and an option answers exactly one: {@code replaces} names the decision it belongs to,
 * {@code opens} the gate it would use instead. Grouping by {@code replaces} is what keeps two
 * questions with three and four answers from reading as one seven-row soup.
 *
 * @param key       the WHOLE support this option implies - what gets stored when it is picked.
 * @param externals the flows at {@code opens} only, i.e. what actually differs.
 * @param rank      why this is not the default answer to its question.
 */
public record Alternative(ChoiceKey key, PortRef replaces, PortRef opens, List<External> externals, Rank rank) {

    /** True when this option keeps the answer that is already on screen for its decision. */
    public boolean isCurrent() {
        return rank == Rank.DEFAULT;
    }

    public @Nullable Note toNote() {
        if (this.rank() != Rank.VOIDS_MORE) return this.rank()
            .toNote();
        boolean voids = false;
        boolean imports = false;
        for (final External e : this.externals()) {
            if (e.port()
                .input()) {
                imports = true;
            } else {
                voids = true;
            }
        }
        if (voids && imports) return SolverMessage.REASON_CROSSES_MORE.toNote();
        return imports ? SolverMessage.REASON_IMPORTS_MORE.toNote() : SolverMessage.REASON_LEAVES_EXCESS.toNote();
    }
}
