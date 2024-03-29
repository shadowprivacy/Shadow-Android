package su.sres.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.ClientConfig;
import com.mobilecoin.lib.exceptions.AttestationException;

import su.sres.securesms.R;
import su.sres.securesms.util.Base64;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class MobileCoinTestNetConfig extends MobileCoinConfig {

  private final SignalServiceAccountManager signalServiceAccountManager;

  public MobileCoinTestNetConfig(@NonNull SignalServiceAccountManager signalServiceAccountManager) {
    this.signalServiceAccountManager = signalServiceAccountManager;
  }

  @Override
  @NonNull
  List<Uri> getConsensusUris() {
    return Arrays.asList(
            Uri.parse("mc://node1.consensus.mob.staging.namda.net"),
            Uri.parse("mc://node2.consensus.mob.staging.namda.net")
    );
  }

  @Override
  @NonNull Uri getFogUri() {
    return Uri.parse("fog://service.fog.mob.staging.namda.net");
  }

  @Override
  @NonNull Uri getFogReportUri() {
    return Uri.parse("fog://fog-rpt-stg.namda.net");
  }

  @Override
  @NonNull byte[] getFogAuthoritySpki() {
    return Base64.decodeOrThrow("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoCMq8nnjTq5EEQ4EI7yrABL9P4y4h1P/h0DepWgXx+w/fywcfRSZINxbaMpvcV3uSJayExrpV1KmaS2wfASeYhSj+rEzAm0XUOw3Q94NOx5A/dOQag/d1SS6/QpF3PQYZTULnRFetmM4yzEnXsXcWtzEu0hh02wYJbLeAq4CCcPTPe2qckrbUP9sD18/KOzzNeypF4p5dQ2m/ezfxtgaLvdUMVDVIAs2v9a5iu6ce4bIcwTIUXgX0w3+UKRx8zqowc3HIqo9yeaGn4ZOwQHvAJZecPmb2pH1nK+BtDUvHpvf+Y3/NJxwh+IPp6Ef8aoUxs2g5oIBZ3Q31fjS2Bh2gmwoVooyytEysPAHvRPVBxXxLi36WpKfk1Vq8K7cgYh3IraOkH2/l2Pyi8EYYFkWsLYofYogaiPzVoq2ZdcizfoJWIYei5mgq+8m0ZKZYLebK1i2GdseBJNIbSt3wCNXZxyN6uqFHOCB29gmA5cbKvs/j9mDz64PJe9LCanqcDQV1U5l9dt9UdmUt7Ab1PjBtoIFaP+u473Z0hmZdCgAivuiBMMYMqt2V2EIw4IXLASE3roLOYp0p7h0IQHb+lVIuEl0ZmwAI30ZmzgcWc7RBeWD1/zNt55zzhfPRLx/DfDY5Kdp6oFHWMvI2r1/oZkdhjFp7pV6qrl7vOyR5QqmuRkCAwEAAQ==");
  }

  @Override
  @NonNull AuthCredentials getAuth() throws IOException {
    return signalServiceAccountManager.getPaymentsAuthorization();
  }

  @Override
  @NonNull ClientConfig getConfig() {
    try {
      Set<X509Certificate> trustRoots = getTrustRoots(R.raw.signal_mobilecoin_authority);
      ClientConfig         config     = new ClientConfig();
      VerifierFactory verifierFactory = new VerifierFactory(// ~January 27, 2023
              new ServiceConfig(
                      "4f3879bfffb7b9f86a33086202b6120a32da0ca159615fbbd6fbac6aa37bbf02",
                      "16d73984c2d2712156135ab69987ca78aca67a2cf4f0f2287ea584556f9d223a",
                      "23ececb2482e3b1d9e284502e2beb65ae76492f2791f3bfef50852ee64b883c3",
                      "f52b3dc018195eae42f543e64e976c818c06672b5489746e2bf74438d488181b",
                      new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
              ),
              // ~May 30, 2023
              new ServiceConfig(
                      "5341c6702a3312243c0f049f87259352ff32aa80f0f6426351c3dd063d817d7a",
                      "248356aa0d3431abc45da1773cfd6191a4f2989a4a99da31f450bd7c461e312b",
                      "b61188a6c946557f32e612eff5615908abd1b72ec11d8b7070595a92d4abbbf1",
                      "ac292a1ad27c0338a5159d5fab2bed3917ea144536cb13b5c1226d09a2fbc648",
                      new String[] { "INTEL-SA-00334", "INTEL-SA-00615", "INTEL-SA-00657" }
              ));

      config.logAdapter = new MobileCoinLogAdapter();
      config.fogView    = new ClientConfig.Service().withTrustRoots(trustRoots)
              .withVerifier(verifierFactory.createViewVerifier());
      config.fogLedger  = new ClientConfig.Service().withTrustRoots(trustRoots)
              .withVerifier(verifierFactory.createLedgerVerifier());
      config.consensus  = new ClientConfig.Service().withTrustRoots(trustRoots)
              .withVerifier(verifierFactory.createConsensusVerifier());
      config.report     = new ClientConfig.Service().withVerifier(verifierFactory.createReportVerifier());

      return config;
    } catch (AttestationException ex) {
      throw new IllegalStateException();
    }
  }
}
