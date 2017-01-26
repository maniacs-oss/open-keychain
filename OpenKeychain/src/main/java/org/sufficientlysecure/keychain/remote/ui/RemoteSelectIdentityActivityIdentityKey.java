/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.remote.ui;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.openintents.openpgp.util.OpenPgpUtils;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.provider.ApiDataAccessObject;
import org.sufficientlysecure.keychain.provider.ApiIdentityDataAccessObject;
import org.sufficientlysecure.keychain.remote.ApiPermissionHelper;
import org.sufficientlysecure.keychain.remote.ApiPermissionHelper.WrongPackageCertificateException;
import org.sufficientlysecure.keychain.remote.ui.SelectIdentityKeyListFragment.SelectIdentityKeyFragmentListener;
import org.sufficientlysecure.keychain.ui.CreateKeyActivity;
import org.sufficientlysecure.keychain.ui.base.BaseActivity;
import org.sufficientlysecure.keychain.util.Log;


public class RemoteSelectIdentityActivityIdentityKey extends BaseActivity implements SelectIdentityKeyFragmentListener {
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_API_IDENTITY = "api_identity";
    public static final String EXTRA_CURRENT_MASTER_KEY_ID = "current_master_key_id";

    protected static final int REQUEST_CODE_CREATE_KEY = 0x00008884;
    private String packageName;
    private String apiIdentity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        apiIdentity = intent.getStringExtra(EXTRA_API_IDENTITY);

        checkPackageAllowed();

        // Inflate a "Done" custom action bar
        setFullScreenDialogClose(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });

        TextView noneButton = (TextView) findViewById(R.id.api_select_sign_key_none);
        noneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onKeySelected(null);
                setResult(Activity.RESULT_OK);
                finish();
            }
        });


        startListFragments(savedInstanceState, apiIdentity, apiIdentity);
    }

    private void checkPackageAllowed() {
        ApiDataAccessObject apiDao = new ApiDataAccessObject(this);
        ApiPermissionHelper apiPermissionHelper = new ApiPermissionHelper(this, apiDao);
        boolean packageAllowed;
        try {
            packageAllowed = apiPermissionHelper.isPackageAllowed(packageName);
        } catch (WrongPackageCertificateException e) {
            packageAllowed = false;
        }
        if (!packageAllowed) {
            throw new IllegalStateException("Pending intent launched by unknown app!");
        }
    }

    private void startListFragments(Bundle savedInstanceState, String apiIdentity, String preferredUserId) {
        // However, if we're being restored from a previous state,
        // then we don't need to do anything and should return or else
        // we could end up with overlapping fragments.
        if (savedInstanceState != null) {
            return;
        }

        // Create an instance of the fragments
        SelectIdentityKeyListFragment listFragment = SelectIdentityKeyListFragment
                .newInstance(apiIdentity, preferredUserId);
        // Add the fragment to the 'fragment_container' FrameLayout
        // NOTE: We use commitAllowingStateLoss() to prevent weird crashes!
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.api_select_sign_key_list_fragment, listFragment)
                .commitAllowingStateLoss();
        // do it immediately!
        getSupportFragmentManager().executePendingTransactions();
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.api_select_identity_key);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if a result has been returned, display a notify
        if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
            OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
            result.createNotify(this).show();
        }

        switch (requestCode) {
            case REQUEST_CODE_CREATE_KEY: {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
                        // TODO: select?
//                        EditKeyResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
//                        mSelectKeySpinner.setSelectedKeyId(result.mMasterKeyId);
                    } else {
                        Log.e(Constants.TAG, "missing result!");
                    }
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void onCreateKey() {
        OpenPgpUtils.UserId userIdSplit = KeyRing.splitUserId(apiIdentity);

        Intent intent = new Intent(this, CreateKeyActivity.class);
        intent.putExtra(CreateKeyActivity.EXTRA_NAME, userIdSplit.name);
        intent.putExtra(CreateKeyActivity.EXTRA_EMAIL, userIdSplit.email);
        startActivityForResult(intent, SelectSignKeyIdActivity.REQUEST_CODE_CREATE_KEY);
    }

    @Override
    public void onKeySelected(Long masterKeyId) {
        ApiIdentityDataAccessObject apiIdentityDao = new ApiIdentityDataAccessObject(this, packageName);

        apiIdentityDao.setMasterKeyIdForApiIdentity(apiIdentity, masterKeyId);

        setResult(Activity.RESULT_OK);
        finish();
    }
}
