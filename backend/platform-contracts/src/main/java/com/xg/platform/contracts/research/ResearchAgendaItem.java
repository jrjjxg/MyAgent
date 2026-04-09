package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ResearchAgendaItem(
        String agendaId,
        String title,
        String objective,
        String priority,
        String coverageCriteria,
        boolean covered
) implements Serializable {
}
