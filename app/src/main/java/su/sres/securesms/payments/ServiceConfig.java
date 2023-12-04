package su.sres.securesms.payments;

import com.mobilecoin.lib.util.Hex;

public class ServiceConfig {
    private final byte[] consensus;
    private final byte[] report;
    private final byte[] ledger;
    private final byte[] view;
    private final String[] hardeningAdvisories;

    public ServiceConfig(String consensus, String report, String ledger, String view, String[] hardeningAdvisories) {
        this.consensus = Hex.toByteArray(consensus);
        this.report = Hex.toByteArray(report);
        this.ledger = Hex.toByteArray(ledger);
        this.view = Hex.toByteArray(view);
        this.hardeningAdvisories = hardeningAdvisories;
    }

    public byte[] getConsensus() {
        return consensus;
    }

    public byte[] getReport() {
        return report;
    }

    public byte[] getLedger() {
        return ledger;
    }

    public byte[] getView() {
        return view;
    }

    public String[] getHardeningAdvisories() {
        return hardeningAdvisories;
    }
}
