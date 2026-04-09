package com.xg.platform.agent.core.research.execution;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.CompressedFinding;
import com.xg.platform.agent.core.ResearchPlan;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.agent.core.ResearchUnitResult;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.research.ReportCitation;

import java.util.List;

public interface ResearchExecutionSupport {

    List<DocumentRecord> prepareResearchExecution(AgentExecutionRequest request, AgentOutputEmitter outputEmitter);

    ResearchPlan createResearchPlan(ApprovedResearchPlan approvedPlan, List<DocumentRecord> documents);

    ResearchUnitResult executeResearchUnit(String providerId,
                                           AgentExecutionRequest request,
                                           String researchBrief,
                                           List<String> refinementNotes,
                                           List<DocumentRecord> documents,
                                           ResearchUnit unit,
                                           AgentOutputEmitter outputEmitter,
                                           int stepIndex,
                                           int totalSteps);

    List<CompressedFinding> compressFindings(String providerId,
                                            AgentExecutionRequest request,
                                            String researchBrief,
                                            ResearchPlan researchPlan,
                                            List<ResearchUnitResult> unitResults);

    String generateFinalReport(String providerId,
                               AgentExecutionRequest request,
                               String researchBrief,
                               ResearchPlan researchPlan,
                               List<CompressedFinding> findings,
                               List<ReportCitation> citations,
                               List<String> refinementNotes);
}
