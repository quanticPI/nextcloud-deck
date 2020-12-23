package it.niedermann.nextcloud.deck.persistence.sync.adapters.db;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.db.dao.AccountDao;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class DeckDatabaseTest {
    private AccountDao accountDao;
    private DeckDatabase db;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, DeckDatabase.class).build();
        accountDao = db.getAccountDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void writeUserAndReadInList() throws Exception {
        Account account = new Account();
        account.setName("test@example.com");
        account.setUserName("test");
        account.setUrl("https://example.com");
        accountDao.insert(account);
        Account byName = accountDao.getAccountByNameDirectly("test@example.com");
        assertEquals("test1", byName.getUserName());
        assertEquals("https://example.com", byName.getUrl());
    }
}
