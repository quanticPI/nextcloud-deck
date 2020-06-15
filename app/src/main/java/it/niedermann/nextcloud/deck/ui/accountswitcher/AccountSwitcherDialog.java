package it.niedermann.nextcloud.deck.ui.accountswitcher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;

import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.DialogAccountSwitcherBinding;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.MainViewModel;
import it.niedermann.nextcloud.deck.ui.branding.BrandedDialogFragment;
import it.niedermann.nextcloud.deck.ui.manageaccounts.ManageAccountsActivity;
import it.niedermann.nextcloud.deck.util.ExceptionUtil;

import static it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.LiveDataHelper.observeOnce;
import static it.niedermann.nextcloud.deck.ui.MainActivity.ACTIVITY_MANAGE_ACCOUNTS;
import static it.niedermann.nextcloud.deck.ui.branding.BrandedActivity.applyBrandToLayerDrawable;

public class AccountSwitcherDialog extends BrandedDialogFragment {

    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";

    private AccountSwitcherAdapter adapter;
    private SyncManager syncManager;
    private DialogAccountSwitcherBinding binding;
    private MainViewModel viewModel;
    private long currentAccountId;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        final Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_CURRENT_ACCOUNT_ID)) {
            throw new IllegalArgumentException("Please provide at least " + KEY_CURRENT_ACCOUNT_ID);
        } else {
            this.currentAccountId = args.getLong(KEY_CURRENT_ACCOUNT_ID);
        }

        syncManager = new SyncManager(requireActivity());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogAccountSwitcherBinding.inflate(requireActivity().getLayoutInflater());
        binding.accountItemLabel.setText(viewModel.getCurrentAccount().getName());

        Glide.with(requireContext())
                .load(viewModel.getCurrentAccount().getUrl() + "/index.php/avatar/" + Uri.encode(viewModel.getCurrentAccount().getUserName()) + "/64")
                .error(R.drawable.ic_person_grey600_24dp)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.currentAccountItemAvatar);

        observeOnce(syncManager.readAccounts(), requireActivity(), (accounts) -> {
            accounts.remove(viewModel.getCurrentAccount());
            adapter.setAccounts(accounts);
        });

        binding.accountLayout.setOnClickListener((v) -> dismiss());

        adapter = new AccountSwitcherAdapter((localAccount -> {
            viewModel.setCurrentAccount(localAccount, localAccount.getServerDeckVersionAsObject().isSupported(requireContext()));
            dismiss();
        }));

        binding.accountsList.setAdapter(adapter);

        binding.addAccount.setOnClickListener((v) -> {
            try {
                AccountImporter.pickNewAccount(requireActivity());
            } catch (NextcloudFilesAppNotInstalledException e) {
                ExceptionUtil.handleNextcloudFilesAppNotInstalledException(requireContext(), e);
            } catch (AndroidGetAccountsPermissionNotGranted e) {
                AccountImporter.requestAndroidAccountPermissionsAndPickAccount(requireActivity());
            }
            dismiss();
        });

        binding.manageAccounts.setOnClickListener((v) -> {
            requireActivity().startActivityForResult(new Intent(requireContext(), ManageAccountsActivity.class), ACTIVITY_MANAGE_ACCOUNTS);
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    public static DialogFragment newInstance(long currentAccountId) {
        DialogFragment dialog = new AccountSwitcherDialog();

        Bundle args = new Bundle();
        args.putLong(KEY_CURRENT_ACCOUNT_ID, currentAccountId);
        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        applyBrandToLayerDrawable((LayerDrawable) binding.check.getDrawable(), R.id.area, mainColor);
    }
}