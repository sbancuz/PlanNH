package com.sbancuz.plannh.data.flowchart.balancer.alternatives;

import java.util.List;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.Note;

/**
 * Every answer worth showing for one chart, default first. {@code complete} is false when the
 * search stopped on its budget or its swap cap rather than on exhaustion, so the UI can say
 * "at least these" instead of claiming a list it never proved.
 */
public record Alternatives(@Nullable ChoiceKey chosen, List<Alternative> options, boolean complete, List<Note> notes) {}
