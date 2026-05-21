import { HttpsError } from "firebase-functions/v2/https";

import { requireExportDeliveryConfig } from "../config/env.js";

type ExportMailerInput = {
  destinationEmail: string;
  fileName: string;
  downloadUrl: string;
};

export class ExportMailer {
  async send(input: ExportMailerInput): Promise<void> {
    const config = requireExportDeliveryConfig();
    switch (config.provider) {
      case "resend":
        await sendWithResend(config.apiKey, config.fromEmail, input);
        return;
      default:
        throw new HttpsError(
          "failed-precondition",
          `Unsupported export delivery provider ${config.provider}.`,
        );
    }
  }
}

async function sendWithResend(
  apiKey: string,
  fromEmail: string,
  input: ExportMailerInput,
): Promise<void> {
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: fromEmail,
      to: [input.destinationEmail],
      subject: `Your Hopply logbook export is ready: ${input.fileName}`,
      text: [
        "Your Hopply logbook export is ready.",
        "",
        `Download: ${input.downloadUrl}`,
        "",
        "This secure link may expire.",
      ].join("\n"),
    }),
  });

  if (response.ok) return;

  const body = await response.text();
  throw new Error(`Resend API error ${response.status}: ${body}`);
}
