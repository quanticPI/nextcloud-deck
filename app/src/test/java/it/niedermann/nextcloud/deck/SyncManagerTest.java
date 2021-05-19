package it.niedermann.nextcloud.deck;

import android.content.Context;
import android.os.Build;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;

import it.niedermann.nextcloud.deck.api.IResponseCallback;
import it.niedermann.nextcloud.deck.exceptions.OfflineException;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.Board;
import it.niedermann.nextcloud.deck.model.Card;
import it.niedermann.nextcloud.deck.model.Stack;
import it.niedermann.nextcloud.deck.model.full.FullBoard;
import it.niedermann.nextcloud.deck.model.full.FullStack;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.ServerAdapter;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.db.DataBaseAdapter;
import it.niedermann.nextcloud.deck.persistence.sync.helpers.SyncHelper;
import it.niedermann.nextcloud.deck.persistence.sync.helpers.providers.CardDataProvider;
import it.niedermann.nextcloud.deck.persistence.sync.helpers.providers.StackDataProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class SyncManagerTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ServerAdapter serverAdapter = mock(ServerAdapter.class);
    private final DataBaseAdapter dataBaseAdapter = mock(DataBaseAdapter.class);
    private final SyncHelper.Factory syncHelperFactory = mock(SyncHelper.Factory.class);

    private SyncManager syncManager;

    @Before
    public void setup() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final Constructor<SyncManager> constructor = SyncManager.class.getDeclaredConstructor(Context.class,
                DataBaseAdapter.class,
                ServerAdapter.class,
                ExecutorService.class,
                SyncHelper.Factory.class);
        constructor.setAccessible(true);
        syncManager = constructor.newInstance(context,
                dataBaseAdapter,
                serverAdapter,
                MoreExecutors.newDirectExecutorService(),
                syncHelperFactory);
    }

    @Test
    public void testHasAccounts() throws InterruptedException {
        when(dataBaseAdapter.hasAccounts()).thenReturn(new MutableLiveData<>(true));
        final LiveData<Boolean> hasAccountsPositive = syncManager.hasAccounts();
        assertTrue(TestUtil.getOrAwaitValue(hasAccountsPositive));
        verify(dataBaseAdapter, times(1)).hasAccounts();

        reset(dataBaseAdapter);

        when(dataBaseAdapter.hasAccounts()).thenReturn(new MutableLiveData<>(false));
        final LiveData<Boolean> hasAccountsNegative = syncManager.hasAccounts();
        assertFalse(TestUtil.getOrAwaitValue(hasAccountsNegative));
        verify(dataBaseAdapter, times(1)).hasAccounts();
    }

    @Test
    public void testReadAccount() throws InterruptedException {
        final Account account = new Account();
        account.setId(5L);
        account.setName("text@example.com");

        when(dataBaseAdapter.readAccount(5)).thenReturn(new MutableLiveData<>(account));
        assertEquals(account, TestUtil.getOrAwaitValue(syncManager.readAccount(5)));
        verify(dataBaseAdapter, times(1)).readAccount(5);

        reset(dataBaseAdapter);

        when(dataBaseAdapter.readAccount("test@example.com")).thenReturn(new MutableLiveData<>(account));
        assertEquals(account, TestUtil.getOrAwaitValue(syncManager.readAccount("test@example.com")));
        verify(dataBaseAdapter, times(1)).readAccount("test@example.com");

        reset(dataBaseAdapter);

        when(dataBaseAdapter.readAccount(5)).thenReturn(new MutableLiveData<>(null));
        assertNull(TestUtil.getOrAwaitValue(syncManager.readAccount(5)));
        verify(dataBaseAdapter, times(1)).readAccount(5);

        reset(dataBaseAdapter);

        when(dataBaseAdapter.readAccount("test@example.com")).thenReturn(new MutableLiveData<>(null));
        assertNull(TestUtil.getOrAwaitValue(syncManager.readAccount("test@example.com")));
        verify(dataBaseAdapter, times(1)).readAccount("test@example.com");
    }

    @Test
    public void testDeleteAccount() {
        doNothing().when(dataBaseAdapter).deleteAccount(anyLong());
        syncManager.deleteAccount(1337L);
        verify(dataBaseAdapter, times(1)).deleteAccount(1337L);
    }

    /**
     * When {@link SyncManager#synchronizeBoard(IResponseCallback, long)} is triggered, it should
     * pass the given {@link IResponseCallback} to the {@link SyncHelper} and trigger a
     * {@link SyncHelper#doSyncFor(AbstractSyncDataProvider)}.
     * {@link OfflineException} should be caught and passed to the {@link IResponseCallback}
     */
    @SuppressWarnings("JavadocReference")
    @Test
    public void testSynchronizeBoard() {
        final SyncHelper syncHelper = mock(SyncHelper.class);

        when(dataBaseAdapter.getFullBoardByLocalIdDirectly(anyLong(), anyLong())).thenReturn(new FullBoard());
        when(syncHelper.setResponseCallback(any())).thenReturn(syncHelper);
        doNothing().when(syncHelper).doSyncFor(any());
        when(syncHelperFactory.create(any(), any(), any())).thenReturn(syncHelper);

        final IResponseCallback<Boolean> responseCallback = spy(new IResponseCallback<Boolean>(new Account(1L)) {
            @Override
            public void onResponse(Boolean response) {

            }
        });

        syncManager.synchronizeBoard(responseCallback, 1L);

        verify(syncHelper, times(1)).setResponseCallback(responseCallback);
        verify(syncHelper, times(1)).doSyncFor(any(StackDataProvider.class));

        doThrow(OfflineException.class).when(syncHelper).doSyncFor(any());

        syncManager.synchronizeBoard(responseCallback, 1L);

        verify(responseCallback, times(1)).onError(any(OfflineException.class));
    }

    @Test
    public void testSynchronizeCard() {
        final SyncHelper syncHelper = mock(SyncHelper.class);
        final FullStack fullStack = new FullStack();
        fullStack.setStack(new Stack("Test", 1L));

        when(dataBaseAdapter.getFullStackByLocalIdDirectly(anyLong())).thenReturn(fullStack);
        when(dataBaseAdapter.getBoardByLocalIdDirectly(anyLong())).thenReturn(new Board());
        when(syncHelper.setResponseCallback(any())).thenReturn(syncHelper);
        doNothing().when(syncHelper).doSyncFor(any());
        when(syncHelperFactory.create(any(), any(), any())).thenReturn(syncHelper);

        final IResponseCallback<Boolean> responseCallback = spy(new IResponseCallback<Boolean>(new Account(1L)) {
            @Override
            public void onResponse(Boolean response) {

            }
        });

        final Card card = new Card();
        card.setStackId(5000L);

        syncManager.synchronizeCard(responseCallback, card);

        verify(syncHelper, times(1)).setResponseCallback(responseCallback);
        verify(syncHelper, times(1)).doSyncFor(any(CardDataProvider.class));

        doThrow(OfflineException.class).when(syncHelper).doSyncFor(any());

        syncManager.synchronizeCard(responseCallback, card);

        verify(responseCallback, times(1)).onError(any(OfflineException.class));
    }
}
