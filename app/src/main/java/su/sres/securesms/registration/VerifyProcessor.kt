package su.sres.securesms.registration

interface VerifyProcessor {
  fun hasResult(): Boolean
  fun isServerSentError(): Boolean
}