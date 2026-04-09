import { useState, useCallback } from "react";
import {
  getTaskReport,
  getTaskPlan,
  getTaskIterations,
  getTaskFindings,
  getTaskSources,
  getTaskCitations
} from "../api";
import type {
  ResearchReportView,
  ResearchReportSection,
  ResearchIterationRecord,
  ResearchFindingRecord,
  ResearchSourceRecord,
  ReportCitation
} from "../types";

export function useResearchTaskData(apiBase: string, userId: string) {
  const [researchReportByTask, setResearchReportByTask] = useState<Record<string, ResearchReportView | null>>({});
  const [researchPlanByTask, setResearchPlanByTask] = useState<Record<string, ResearchReportSection[]>>({});
  const [researchIterationsByTask, setResearchIterationsByTask] = useState<Record<string, ResearchIterationRecord[]>>({});
  const [researchFindingsByTask, setResearchFindingsByTask] = useState<Record<string, ResearchFindingRecord[]>>({});
  const [researchSourcesByTask, setResearchSourcesByTask] = useState<Record<string, ResearchSourceRecord[]>>({});
  const [researchCitationsByTask, setResearchCitationsByTask] = useState<Record<string, ReportCitation[]>>({});

  const reloadResearchTaskData = useCallback(async (threadId: string, taskId: string, isCompleted: boolean) => {
    const [report, plan, iterations, findings, sources, citations] = await Promise.allSettled([
      getTaskReport(apiBase, userId, threadId, taskId),
      getTaskPlan(apiBase, userId, threadId, taskId),
      getTaskIterations(apiBase, userId, threadId, taskId),
      getTaskFindings(apiBase, userId, threadId, taskId),
      getTaskSources(apiBase, userId, threadId, taskId),
      getTaskCitations(apiBase, userId, threadId, taskId)
    ]);

    let errorMessage: string | null = null;

    if (report.status === "fulfilled") {
      setResearchReportByTask((current) => ({ ...current, [taskId]: report.value }));
    } else if (isCompleted) {
      errorMessage = report.reason instanceof Error ? report.reason.message : String(report.reason);
    }
    if (plan.status === "fulfilled") {
      setResearchPlanByTask((current) => ({ ...current, [taskId]: plan.value }));
    }
    if (iterations.status === "fulfilled") {
      setResearchIterationsByTask((current) => ({ ...current, [taskId]: iterations.value }));
    }
    if (findings.status === "fulfilled") {
      setResearchFindingsByTask((current) => ({ ...current, [taskId]: findings.value }));
    }
    if (sources.status === "fulfilled") {
      setResearchSourcesByTask((current) => ({ ...current, [taskId]: sources.value }));
    }
    if (citations.status === "fulfilled") {
      setResearchCitationsByTask((current) => ({ ...current, [taskId]: citations.value }));
    }

    return errorMessage;
  }, [apiBase, userId]);

  return {
    researchReportByTask,
    researchPlanByTask,
    researchIterationsByTask,
    researchFindingsByTask,
    researchSourcesByTask,
    researchCitationsByTask,
    reloadResearchTaskData
  };
}
