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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;

import org.openintents.openpgp.util.OpenPgpUtils;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.provider.ApiDataAccessObject;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.remote.ApiPermissionHelper;
import org.sufficientlysecure.keychain.remote.ui.adapter.SelectSignKeyAdapter;
import org.sufficientlysecure.keychain.ui.CreateKeyActivity;
import org.sufficientlysecure.keychain.ui.base.RecyclerFragment;
import org.sufficientlysecure.keychain.ui.util.adapter.CursorAdapter;


public class SelectIdentityKeyListFragment extends RecyclerFragment<SelectSignKeyAdapter>
        implements SelectSignKeyAdapter.SelectSignKeyListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String ARG_API_IDENTITY = "pref_uid";
    private static final String ARG_PREF_UID = "pref_uid";

    private String apiIdentity;
    private String prefUid;

    /**
     * Creates new instance of this fragment
     */
    public static SelectIdentityKeyListFragment newInstance(String apiIdentity, String preferredUserId) {
        SelectIdentityKeyListFragment frag = new SelectIdentityKeyListFragment();
        Bundle args = new Bundle();

        args.putString(ARG_API_IDENTITY, apiIdentity);
        args.putString(ARG_PREF_UID, preferredUserId);

        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        apiIdentity = getArguments().getString(ARG_API_IDENTITY);
        prefUid = getArguments().getString(ARG_PREF_UID);

        // Give some text to display if there is no data. In a real
        // application this would come from a resource.
        setEmptyText(getString(R.string.list_empty));

        SelectSignKeyAdapter adapter = new SelectSignKeyAdapter(getContext(), null);
        adapter.setListener(this);

        setAdapter(adapter);
        setLayoutManager(new LinearLayoutManager(getContext()));

        // Start out with a progress indicator.
        hideList(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri baseUri = KeyRings.buildUnifiedKeyRingsUri();

        // These are the rows that we will retrieve.
        String[] projection = new String[]{
                KeyRings._ID,
                KeyRings.MASTER_KEY_ID,
                KeyRings.USER_ID,
                KeyRings.IS_EXPIRED,
                KeyRings.IS_REVOKED,
                KeyRings.HAS_ENCRYPT,
                KeyRings.VERIFIED,
                KeyRings.HAS_ANY_SECRET,
                KeyRings.HAS_DUPLICATE_USER_ID,
                KeyRings.CREATION,
        };

        String selection = KeyRings.HAS_ANY_SECRET + " != 0";

        String orderBy = KeyRings.USER_ID + " ASC";
        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, projection, selection, null, orderBy);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        getAdapter().swapCursor(CursorAdapter.KeyCursor.wrap(data));

        // The list should now be shown.
        if (isResumed()) {
            showList(true);
        } else {
            showList(false);
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        getAdapter().swapCursor(null);
    }

    @Override
    public void onDestroy() {
        getAdapter().setListener(null);
        super.onDestroy();
    }

    @Override
    public void onCreateKeyDummyClicked() {
        getKeySelectFragmentListener().onCreateKey();
    }

    @Override
    public void onSelectKeyItemClicked(long masterKeyId) {
        getKeySelectFragmentListener().onKeySelected(masterKeyId);
    }

    SelectIdentityKeyFragmentListener getKeySelectFragmentListener() {
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        if (!(activity instanceof SelectIdentityKeyFragmentListener)) {
            throw new IllegalStateException("SelectIdentityKeyListFragment must be attached to KeySelectFragmentListener!");
        }

        return (SelectIdentityKeyFragmentListener) activity;
    }

    public interface SelectIdentityKeyFragmentListener {
        void onCreateKey();
        void onKeySelected(Long masterKeyId);
    }

}
