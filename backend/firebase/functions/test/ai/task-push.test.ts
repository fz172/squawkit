import { describe, expect, it } from "vitest";

import { AiJobStatus } from "../../src/generated/proto/rpc/ai_job/ai_job.js";
import { suggestionsBodyKey, suggestionsPushData, toDataMap } from "../../src/notifications/pushMessages.js";

// T26: the R20 push when a suggestion run ends.

describe("suggestionsPushData", () => {
  it("names the Thing, the outcome's string, and taps through to its suggestions", () => {
    const data = suggestionsPushData({
      thingId: "thing-1",
      tailNumber: "Model Y",
      status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED,
    });

    expect(toDataMap(data, "uid-1")).toEqual({
      class: "collaboration",
      channel: "COLLABORATION",
      notificationId: "suggestions:thing-1",
      highPriority: "false",
      aircraftId: "thing-1",
      recordType: "aircraft",
      tapTarget: "suggestions:thing-1",
      titleKey: "notification_suggestions_title",
      bodyKey: "notification_suggestions_body_ready",
      tailNumber: "Model Y",
      actorName: "",
      recipientUid: "uid-1",
    });
  });

  it("collapses to one per Thing, so a newer run replaces an older one in the tray", () => {
    const first = suggestionsPushData({ thingId: "t", tailNumber: "", status: AiJobStatus.AI_JOB_STATUS_FAILED });
    const second = suggestionsPushData({ thingId: "t", tailNumber: "", status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED });

    expect(first.notificationId).toBe(second.notificationId);
  });
});

describe("suggestionsBodyKey", () => {
  it.each([
    [AiJobStatus.AI_JOB_STATUS_SUCCEEDED, "notification_suggestions_body_ready"],
    [AiJobStatus.AI_JOB_STATUS_EMPTY, "notification_suggestions_body_empty"],
    [AiJobStatus.AI_JOB_STATUS_FAILED, "notification_suggestions_body_failed"],
  ] as const)("names a string for status %s", (status, key) => {
    expect(suggestionsBodyKey(status)).toBe(key);
  });
});
