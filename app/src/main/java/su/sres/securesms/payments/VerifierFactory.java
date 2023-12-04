package su.sres.securesms.payments;

import com.mobilecoin.lib.Verifier;
import com.mobilecoin.lib.exceptions.AttestationException;

import java.util.function.Function;

public class VerifierFactory {
    private final ServiceConfig[] serviceConfigs;

    public VerifierFactory(ServiceConfig... serviceConfigs) {
        this.serviceConfigs = serviceConfigs;
    }

    public Verifier createConsensusVerifier() throws AttestationException {
        return createVerifier(ServiceConfig::getConsensus);
    }

    public Verifier createLedgerVerifier() throws AttestationException {
        return createVerifier(ServiceConfig::getLedger);
    }

    public Verifier createViewVerifier() throws AttestationException {
        return createVerifier(ServiceConfig::getView);
    }

    public Verifier createReportVerifier() throws AttestationException {
        return createVerifier(ServiceConfig::getReport);
    }

    private Verifier createVerifier(Function<ServiceConfig, byte[]> getConfigValue) throws AttestationException {
        Verifier verifier = new Verifier();
        for (ServiceConfig config : serviceConfigs) {
            verifier.withMrEnclave(getConfigValue.apply(config), null, config.getHardeningAdvisories());
        }
        return verifier;
    }
}
