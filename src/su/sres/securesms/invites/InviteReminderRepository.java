package su.sres.securesms.invites;

import android.content.Context;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;

import java.util.List;

public final class InviteReminderRepository implements InviteReminderModel.Repository {

    private final Context context;

    public InviteReminderRepository(Context context) {
        this.context = context;
    }

    @Override
    public void setHasSeenFirstInviteReminder(Recipient recipient) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
        recipientDatabase.setSeenFirstInviteReminder(recipient.getId());
    }

    @Override
    public void setHasSeenSecondInviteReminder(Recipient recipient) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
        recipientDatabase.setSeenSecondInviteReminder(recipient.getId());
    }

    @Override
    public int getPercentOfInsecureMessages(int insecureCount) {
        RecipientDatabase recipientDatabase      = DatabaseFactory.getRecipientDatabase(context);
        List<RecipientId> registeredRecipients   = recipientDatabase.getRegisteredForInsights();
        List<RecipientId> unregisteredRecipients = recipientDatabase.getNotRegisteredForInsights();
        MmsSmsDatabase    mmsSmsDatabase         = DatabaseFactory.getMmsSmsDatabase(context);
        int               insecure               = mmsSmsDatabase.getInsecureMessageCountForRecipients(unregisteredRecipients);
        int               secure                 = mmsSmsDatabase.getSecureMessageCountForRecipients(registeredRecipients);

        if (insecure + secure == 0) return 0;
        return Math.round(100f * (insecureCount / (float) (insecure + secure)));
    }
}