package it.niedermann.nextcloud.deck.ui.archivedboards;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collections;

import it.niedermann.nextcloud.deck.databinding.ActivityArchivedBinding;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.Board;
import it.niedermann.nextcloud.deck.model.full.FullBoard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.MainViewModel;
import it.niedermann.nextcloud.deck.ui.board.ArchiveBoardListener;
import it.niedermann.nextcloud.deck.ui.board.DeleteBoardListener;
import it.niedermann.nextcloud.deck.ui.board.EditBoardListener;
import it.niedermann.nextcloud.deck.ui.branding.BrandedActivity;
import it.niedermann.nextcloud.deck.ui.exception.ExceptionHandler;

public class ArchivedBoardsActvitiy extends BrandedActivity implements DeleteBoardListener, EditBoardListener, ArchiveBoardListener {

    private static final String BUNDLE_KEY_ACCOUNT = "accountId";

    private MainViewModel viewModel;
    private ActivityArchivedBinding binding;
    private ArchivedBoardsAdapter adapter;
    private SyncManager syncManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

        final Bundle args = getIntent().getExtras();
        if (args == null || !args.containsKey(BUNDLE_KEY_ACCOUNT)) {
            throw new IllegalArgumentException("Please provide at least " + BUNDLE_KEY_ACCOUNT);
        }

        final Account account = (Account) args.getSerializable(BUNDLE_KEY_ACCOUNT);

        if (account == null) {
            throw new IllegalArgumentException(BUNDLE_KEY_ACCOUNT + " must not be null.");
        }

        binding = ActivityArchivedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.setCurrentAccount(account);
        syncManager = new SyncManager(this);

        adapter = new ArchivedBoardsAdapter(getSupportFragmentManager(), (board) -> syncManager.dearchiveBoard(board));
        binding.recyclerView.setAdapter(adapter);

        syncManager.getBoards(account.getId(), true).observe(this, (boards) -> {
            viewModel.setCurrentAccountHasArchivedBoards(boards != null && boards.size() > 0);
            adapter.setBoards(boards == null ? Collections.emptyList() : boards);
        });

    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        applyBrandToPrimaryToolbar(mainColor, textColor, binding.toolbar);
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull Account account) {
        return new Intent(context, ArchivedBoardsActvitiy.class)
                .putExtra(BUNDLE_KEY_ACCOUNT, account)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    public void onBoardDeleted(Board board) {
        syncManager.deleteBoard(board);
    }

    @Override
    public void onUpdateBoard(FullBoard fullBoard) {
        syncManager.updateBoard(fullBoard);
    }

    @Override
    public void onArchive(Board board) {
        syncManager.dearchiveBoard(board);
    }
}