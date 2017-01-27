package org.sufficientlysecure.keychain;


import org.bouncycastle.bcpg.sig.KeyFlags;
import org.openintents.openpgp.util.OpenPgpUtils;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel.Algorithm;


public class AutoCryptConstants {
    public static SaveKeyringParcel getKeyringParametersForUserId(String apiIdentity) {
        SaveKeyringParcel result = new SaveKeyringParcel();

        result.mAddSubKeys.add(new SaveKeyringParcel.SubkeyAdd(Algorithm.RSA,
                2048, null, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA, 0L));
        result.mAddSubKeys.add(new SaveKeyringParcel.SubkeyAdd(Algorithm.RSA,
                2048, null, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE, 0L));

        result.setNewUnlock(null);

        String userId = KeyRing.createUserId(
                new OpenPgpUtils.UserId(null, apiIdentity, null)
        );
        result.mAddUserIds.add(userId);
        result.mChangePrimaryUserId = userId;

        return result;
    }
}
