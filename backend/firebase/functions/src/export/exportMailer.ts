import { HttpsError } from "firebase-functions/v2/https";

import { requireExportDeliveryConfig } from "../config/env.js";

type ExportMailerInput = {
  destinationEmail: string;
  fileName: string;
  sizeBytes: number;
  generatedAtEpochMillis: number;
  downloadUrl: string;
  linkExpiresAtEpochMillis: number;
};

const BRAND_BLUE = "#1A5FAE";
const BRAND_DEEP_NAVY = "#001849";
const BRAND_PAGE = "#F2F4F8";
const BRAND_CARD = "#FFFFFF";
const BRAND_TEXT = "#0E1C2B";
const BRAND_MUTED = "#525E72";
const BRAND_SUBTLE = "#8A95A8";
const BRAND_SURFACE = "#F7F9FC";
const BRAND_BORDER = "#E1E7F0";
const BRAND_AMBER_BG = "#FFDFA6";
const BRAND_AMBER_TEXT = "#7A5200";

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
      subject: `Your SquawkIt logbook export is ready`,
      html: buildExportReadyHtml(input),
      text: buildExportReadyText(input),
    }),
  });

  if (response.ok) return;

  const body = await response.text();
  throw new Error(`Resend API error ${response.status}: ${body}`);
}

function buildExportReadyHtml(input: ExportMailerInput): string {
  const escapedUrl = escapeHtml(input.downloadUrl);
  const escapedFileName = escapeHtml(input.fileName);
  const fileSize = escapeHtml(formatBytes(input.sizeBytes));
  const generatedAt = escapeHtml(formatDateTime(input.generatedAtEpochMillis));
  const expiresAt = escapeHtml(formatDateTime(input.linkExpiresAtEpochMillis));
  const preheader =
    "Your logbook export is ready. The secure download link expires in 24 hours.";

  return `<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <meta name="supported-color-schemes" content="light dark">
  <title>Your SquawkIt log export is ready</title>
  <style type="text/css">
    body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
    table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
    img { -ms-interpolation-mode: bicubic; border: 0; outline: none; text-decoration: none; display: block; }
    body { margin: 0 !important; padding: 0 !important; width: 100% !important; }
    table { border-collapse: collapse !important; }
    a { color: ${BRAND_BLUE}; }
    @media screen and (max-width: 600px) {
      .container { width: 100% !important; max-width: 100% !important; }
      .px-32 { padding-left: 24px !important; padding-right: 24px !important; }
      .h1 { font-size: 26px !important; line-height: 32px !important; }
      .cta-link { width: 100% !important; box-sizing: border-box !important; }
    }
  </style>
</head>
<body style="margin:0; padding:0; background-color:${BRAND_PAGE}; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <div style="display:none; max-height:0; overflow:hidden; mso-hide:all; font-size:1px; line-height:1px; color:${BRAND_PAGE};">
    ${escapeHtml(preheader)}
  </div>
  <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color:${BRAND_PAGE};">
    <tr>
      <td align="center" style="padding:32px 16px;">
        <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="600" class="container" style="width:600px; max-width:600px;">
          <tr>
            <td align="left" class="px-32" style="padding:8px 32px 20px 32px;">
              <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                  <td align="left" valign="middle">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0">
                      <tr>
                        <td valign="middle" style="padding-right:10px;">
                          ${headerIcon()}
                        </td>
                        <td valign="middle" style="font-family:'Space Grotesk', 'Avenir Next', 'Segoe UI', Arial, sans-serif; font-size:20px; line-height:32px; color:${BRAND_TEXT}; letter-spacing:-0.2px; font-weight:600;">
                          SquawkIt
                        </td>
                      </tr>
                    </table>
                  </td>
                  <td align="right" valign="middle" style="font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:11px; line-height:32px; color:${BRAND_MUTED}; letter-spacing:1.4px; text-transform:uppercase;">
                    Log Export
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td>
              <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color:${BRAND_CARD}; border-radius:16px; border:1px solid ${BRAND_BORDER};" bgcolor="${BRAND_CARD}">
                <tr>
                  <td height="4" bgcolor="${BRAND_BLUE}" style="background-color:${BRAND_BLUE}; height:4px; line-height:4px; font-size:0; border-radius:16px 16px 0 0;">&nbsp;</td>
                </tr>
                <tr>
                  <td align="left" class="px-32" style="padding:36px 40px 8px 40px;">
                    <p style="margin:0 0 12px 0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:12px; line-height:16px; color:${BRAND_MUTED}; letter-spacing:1.6px; text-transform:uppercase; font-weight:600;">
                      Export complete
                    </p>
                    <h1 class="h1" style="margin:0; font-family:'Space Grotesk', 'Avenir Next', 'Segoe UI', Arial, sans-serif; font-size:30px; line-height:38px; color:${BRAND_TEXT}; font-weight:700; letter-spacing:-0.5px;">
                      Your logs are ready to download.
                    </h1>
                  </td>
                </tr>
                <tr>
                  <td align="left" class="px-32" style="padding:16px 40px 24px 40px;">
                    <p style="margin:0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:16px; line-height:24px; color:#3A4557;">
                      We packaged your SquawkIt log data into a downloadable export file. Use the button below to save it directly to your device.
                    </p>
                  </td>
                </tr>
                <tr>
                  <td align="center" class="px-32" style="padding:4px 40px 8px 40px;">
                    <a href="${escapedUrl}" target="_blank" class="cta-link" style="display:inline-block; box-sizing:border-box; background-color:${BRAND_BLUE}; background-image:linear-gradient(180deg, #1F6BC1 0%, ${BRAND_BLUE} 100%); color:#FFFFFF; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:16px; font-weight:600; line-height:20px; letter-spacing:0.3px; text-decoration:none; padding:16px 28px; border-radius:10px; min-width:280px; text-align:center; box-shadow:0 1px 0 rgba(255,255,255,0.15) inset, 0 6px 16px rgba(26,95,174,0.25);">
                      Download log file&nbsp;&rarr;
                    </a>
                  </td>
                </tr>
                <tr>
                  <td class="px-32" style="padding:22px 40px 8px 40px;">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color:${BRAND_AMBER_BG}; border-radius:10px;" bgcolor="${BRAND_AMBER_BG}">
                      <tr>
                        <td style="padding:12px 14px;">
                          <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%">
                            <tr>
                              <td valign="top" width="20" style="padding-right:10px; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:13px; line-height:18px; color:${BRAND_AMBER_TEXT}; font-weight:700;">
                                !
                              </td>
                              <td valign="top" style="font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:13px; line-height:18px; color:${BRAND_AMBER_TEXT};">
                                <strong style="font-weight:700;">This link expires in 24 hours.</strong>
                                <span style="color:${BRAND_AMBER_TEXT};"> It will stop working after ${expiresAt}.</span>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                <tr>
                  <td class="px-32" style="padding:24px 40px 8px 40px;">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color:${BRAND_SURFACE}; border-radius:12px;" bgcolor="${BRAND_SURFACE}">
                      <tr>
                        <td style="padding:4px 18px;">
                          <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%">
                            ${metaRow("File", escapedFileName, true)}
                            ${metaRow("Size", fileSize, true)}
                            ${metaRow("Generated", generatedAt, false)}
                          </table>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                <tr>
                  <td class="px-32" style="padding:28px 40px 36px 40px;">
                    <p style="margin:0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:12px; line-height:18px; color:${BRAND_SUBTLE};">
                      For your security, this download link expires automatically and is intended only for the export you requested.</a>.
                    </p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="px-32" style="padding:28px 32px 8px 32px;">
              <p style="margin:0 0 6px 0; font-family:'Space Grotesk', 'Avenir Next', 'Segoe UI', Arial, sans-serif; font-size:13px; line-height:18px; color:${BRAND_MUTED}; letter-spacing:0.2px;">
                SquawkIt
              </p>
              <p style="margin:0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:11px; line-height:16px; color:${BRAND_SUBTLE};">
                You’re receiving this because you requested a log export from the SquawkIt app.
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

function buildExportReadyText(input: ExportMailerInput): string {
  return [
    "Your SquawkIt logbook export is ready.",
    "",
    `File: ${input.fileName}`,
    `Size: ${formatBytes(input.sizeBytes)}`,
    `Generated: ${formatDateTime(input.generatedAtEpochMillis)}`,
    "",
    "Open the download button from the HTML version of this email.",
    `This secure download link expires at ${formatDateTime(input.linkExpiresAtEpochMillis)}.`,
  ].join("\n");
}

function metaRow(label: string, value: string, bordered: boolean): string {
  const borderStyle = bordered ? `border-bottom:1px solid ${BRAND_BORDER};` : "";
  return `<tr>
    <td style="${borderStyle} padding:14px 0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:12px; line-height:16px; color:${BRAND_MUTED}; letter-spacing:1.2px; text-transform:uppercase; font-weight:600; width:40%;">
      ${escapeHtml(label)}
    </td>
    <td align="right" style="${borderStyle} padding:14px 0; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size:14px; line-height:18px; color:${BRAND_TEXT};">
      ${value}
    </td>
  </tr>`;
}

function headerIcon(): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" role="img" aria-label="SquawkIt icon">
    <rect width="32" height="32" rx="8" fill="${BRAND_BLUE}"/>
    <g transform="translate(4.4 5.2) scale(0.0217)">
      <path fill="${BRAND_AMBER_BG}" d="M635.3,642.7C616.3,605.9 597.6,569.5 578.7,533.1C573.5,523.2 573.4,523.4 563.5,528.5C543.5,538.9 523.3,548.7 502.2,556.6C495.7,559.1 495.6,559.3 497.7,566.1C505.3,590.2 512.9,614.3 520.4,638.5C524.6,652 522.5,664.3 511.9,674.2C496.5,688.6 472.8,687 459.1,670.2C444,651.6 429.6,632.4 414.8,613.4C413.5,611.7 412.2,610 410.9,608.3C405.1,600.6 399.2,594.4 388.6,591.8C370.2,587.3 360.9,569.7 364.4,551C365,547.7 364,545.4 361.7,543C346.8,527.8 332,512.5 317.2,497.1C312.9,492.6 308.7,488.1 304.9,483.2C292,466.6 297.9,435 325,429.2C331,427.9 337.3,427.9 343.3,430.1C362.3,437.2 380.5,446.4 399.3,454C403.5,455.7 407.6,457.5 411.7,459.3C414.4,460.5 416,460 417.8,457.3C433.7,433.1 450.5,409.5 469.7,387.7C473.1,383.8 472.6,382.1 467.5,379.4C445.7,367.8 423.9,356.2 402.2,344.6C375.4,330.3 348.7,316 322,301.6C307.6,293.8 297.2,282.7 295.3,265.9C292.8,245.3 303.5,222.6 331.5,219.7C337.5,219.1 342.9,221.5 348.3,223.7C385.3,239 422.2,254.2 459.1,269.5C488.6,281.8 518.1,294.2 547.5,306.8C552.2,308.8 554.7,308 557.9,304.1C580.2,277.2 604.2,252 631.7,230.4C650.7,215.5 671.8,204.8 696,201.1C748.6,193.1 801,223.4 813.7,276.2C821.9,310.3 812.7,341.1 791.3,368.4C775.4,388.6 754.7,403.3 733.5,417.3C713.3,430.8 691.9,442.4 670.4,453.8C665.7,456.2 664.9,458.8 666.4,463.6C682.7,514.3 698.8,565.1 714.9,615.8C719.8,631.2 723.9,646.9 729.4,662C735.4,678.6 724.5,699.3 705.6,705.2C687.2,711 668.6,704.4 658.7,687.3C650.3,672.9 643.2,657.8 635.3,642.7M749.7,254.8C737.8,248.7 725.3,246.1 712,247.3C700.2,248.4 693.2,256 693.3,267.6C693.5,276.6 696.9,284.5 701.2,292.1C712.1,311.1 727.4,324.8 749.1,330C759.1,332.5 766.8,328.4 771.1,319.1C773,315 773.6,310.5 774.1,306C776.6,286.3 769.8,265.2 749.7,254.8z"/>
    </g>
  </svg>`;
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 KB";
  if (bytes < 1_000_000) return `${Math.ceil(bytes / 1_000)} KB`;
  return `${(bytes / 1_000_000).toFixed(1)} MB`;
}

function formatDateTime(epochMillis: number): string {
  if (!Number.isFinite(epochMillis) || epochMillis <= 0) return "Unknown";
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(new Date(epochMillis)) + " UTC";
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("\"", "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}
