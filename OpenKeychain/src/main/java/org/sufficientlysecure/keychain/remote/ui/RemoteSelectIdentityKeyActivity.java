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


import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;

import org.sufficientlysecure.keychain.AutoCryptConstants;
import org.sufficientlysecure.keychain.BuildConfig;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.EditKeyResult;
import org.sufficientlysecure.keychain.provider.ApiDataAccessObject;
import org.sufficientlysecure.keychain.provider.ApiIdentityDataAccessObject;
import org.sufficientlysecure.keychain.remote.ApiPermissionHelper;
import org.sufficientlysecure.keychain.remote.ApiPermissionHelper.WrongPackageCertificateException;
import org.sufficientlysecure.keychain.remote.ui.SelectIdentityKeyListFragment.SelectIdentityKeyFragmentListener;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel;
import org.sufficientlysecure.keychain.ui.base.BaseActivity;
import org.sufficientlysecure.keychain.ui.base.CryptoOperationHelper;
import org.sufficientlysecure.keychain.util.Log;


public class RemoteSelectIdentityKeyActivity extends BaseActivity implements SelectIdentityKeyFragmentListener {
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_API_IDENTITY = "api_identity";
    public static final String EXTRA_CURRENT_MASTER_KEY_ID = "current_master_key_id";

    public static final String STATE_LIST_ALL_KEYS = "list_all_keys";
    public static final String STATE_KEY_CREATION_STARTED = "key_creation_started";


    private View layoutCreateKey;
    private View layoutKeyList;
    private ViewAnimator viewAnimator;

    private String packageName;
    private String apiIdentity;
    private boolean listAllKeys;
    private boolean keyCreationStarted;

    private Long createdMasterKeyId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        apiIdentity = intent.getStringExtra(EXTRA_API_IDENTITY);

        checkPackageAllowed(packageName);

        if (savedInstanceState != null) {
            listAllKeys = savedInstanceState.getBoolean(STATE_LIST_ALL_KEYS);
            keyCreationStarted = savedInstanceState.getBoolean(STATE_KEY_CREATION_STARTED);
        }

        // Inflate a "Done" custom action bar
        setFullScreenDialogClose(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });

        ImageView iconClientApp = (ImageView) findViewById(R.id.icon_client_app);
        Drawable appIcon;
        CharSequence appName;
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            appIcon = packageManager.getApplicationIcon(applicationInfo);
            appName = packageManager.getApplicationLabel(applicationInfo);
        } catch (NameNotFoundException e) {
            Log.e(Constants.TAG, "Unable to find info of calling app!");
            finish();
            return;
        }
        iconClientApp.setImageDrawable(appIcon);

        TextView titleText = (TextView) findViewById(R.id.select_identity_key_title);
        titleText.setText(getString(R.string.select_identity_key_title, appName));

        TextView textApiIdentity = (TextView) findViewById(R.id.text_api_identity);
        textApiIdentity.setText(apiIdentity);

        layoutCreateKey = findViewById(R.id.layout_create_key);
        layoutCreateKey.setVisibility(View.GONE);
        findViewById(R.id.button_create_key).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onCreateKey();
            }
        });

        findViewById(R.id.button_disable).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onKeySelected(null);
            }
        });

        findViewById(R.id.key_creation_done).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickDoneAfterKeyCreation();
            }
        });

        viewAnimator = (ViewAnimator) findViewById(R.id.status_animator);

        layoutKeyList = findViewById(R.id.select_key_fragment);
        SelectIdentityKeyListFragment frag =
                (SelectIdentityKeyListFragment) getSupportFragmentManager().findFragmentById(R.id.select_key_fragment);
        frag.setApiIdentity(apiIdentity);
        frag.setListAllKeys(listAllKeys);
    }

    public void setListAllKeys(boolean listAllKeys) {
        this.listAllKeys = listAllKeys;

        SelectIdentityKeyListFragment frag =
                (SelectIdentityKeyListFragment) getSupportFragmentManager().findFragmentById(R.id.select_key_fragment);
        frag.setListAllKeys(listAllKeys);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LIST_ALL_KEYS, listAllKeys);
        outState.putBoolean(STATE_KEY_CREATION_STARTED, keyCreationStarted);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.select_identity_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.list_all_keys).setChecked(listAllKeys);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.list_all_keys:
                boolean newState = !item.isChecked();
                item.setChecked(newState);
                setListAllKeys(newState);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkPackageAllowed(String packageName) {
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

    @Override
    protected void initLayout() {
        setContentView(R.layout.api_select_identity_key);
    }

    public void onCreateKey() {
        if (keyCreationStarted) {
            // this should never happen, but if it does it's probably a UI error. just reset to avoid a stuck ui
            setViewAnimatorStatus(Status.LIST);
            return;
        }
        keyCreationStarted = true;

        CryptoOperationHelper.Callback<SaveKeyringParcel, EditKeyResult> createKeyCallback =
                new CryptoOperationHelper.Callback<SaveKeyringParcel, EditKeyResult>() {
            SaveKeyringParcel saveKeyringParcel = AutoCryptConstants.getKeyringParametersForUserId(apiIdentity);

            @Override
            public SaveKeyringParcel createOperationInput() {
                return saveKeyringParcel;
            }

            @Override
            public void onCryptoOperationSuccess(EditKeyResult result) {
                setViewAnimatorStatus(Status.CREATE_DONE);
                createdMasterKeyId = result.mMasterKeyId;
            }

            @Override
            public void onCryptoOperationCancelled() {
            }

            @Override
            public void onCryptoOperationError(EditKeyResult result) {
                result.createNotify(RemoteSelectIdentityKeyActivity.this);
            }

            @Override
            public boolean onCryptoSetProgress(String msg, int progress, int max) {
                return true;
            }
        };

        CryptoOperationHelper<SaveKeyringParcel, EditKeyResult> createOpHelper =
                new CryptoOperationHelper<>(1, this, createKeyCallback, null);
        createOpHelper.cryptoOperation();

        setViewAnimatorStatus(Status.CREATE_IN_PROGRESS);
    }

    public void setViewAnimatorStatus(Status status) {
        viewAnimator.setDisplayedChild(status.displayedChild);
    }

    private enum Status {
        LIST(0),
        CREATE_IN_PROGRESS(1),
        CREATE_DONE(2),
        ERROR(3),
        ;

        final int displayedChild;

        Status(int displayedChild) {
            this.displayedChild = displayedChild;
        }
    }

    private void onClickDoneAfterKeyCreation() {
        if (createdMasterKeyId == null) {
            throw new IllegalStateException("This button shouldn't be clickable while no key was created!");
        }

        onKeySelected(createdMasterKeyId);
    }

    @Override
    public void onKeySelected(Long masterKeyId) {
        ApiIdentityDataAccessObject apiIdentityDao = new ApiIdentityDataAccessObject(this, packageName);

        apiIdentityDao.setMasterKeyIdForApiIdentity(apiIdentity, masterKeyId);

        finish();
    }

    @Override
    public void onChangeListEmptyStatus(boolean isEmpty) {
        layoutKeyList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        boolean showCreateButton = isEmpty || BuildConfig.DEBUG;
        layoutCreateKey.setVisibility(showCreateButton ? View.VISIBLE : View.GONE);
    }
}
