package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.dataWithContentsOfFile
import platform.MessageUI.MFMailComposeResult
import platform.MessageUI.MFMailComposeViewController
import platform.MessageUI.MFMailComposeViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject

@Composable
actual fun rememberExportFileSharer(): ExportFileSharer = remember {
  IosExportFileSharer()
}

private class IosExportFileSharer : ExportFileSharer {
  private val delegate: MFMailComposeViewControllerDelegateProtocol = MailComposeDelegate()

  override fun share(filePath: String, chooserTitle: String, subject: String, body: String): Boolean {
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
      ?: return false
    if (!MFMailComposeViewController.canSendMail()) return false
    val attachmentData = NSData.dataWithContentsOfFile(filePath) ?: return false
    val mailController = MFMailComposeViewController().apply {
      setMailComposeDelegate(this@IosExportFileSharer.delegate)
      setSubject(subject)
      setMessageBody(body, isHTML = false)
      addAttachmentData(
        attachment = attachmentData,
        mimeType = "application/zip",
        fileName = filePath.substringAfterLast('/').ifBlank { "SquawkIt_Logs.zip" },
      )
    }
    rootViewController.presentViewController(
      viewControllerToPresent = mailController,
      animated = true,
      completion = null,
    )
    return true
  }
}

private class MailComposeDelegate : NSObject(), MFMailComposeViewControllerDelegateProtocol {
  override fun mailComposeController(
    controller: MFMailComposeViewController,
    didFinishWithResult: MFMailComposeResult,
    error: NSError?,
  ) {
    controller.dismissViewControllerAnimated(true, completion = null)
  }
}
