package com.ninereader.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.TrashItemEntity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class TrashRepositoryTest extends DbTestBase {

    private TrashItemEntity trashed(long docId, long deletedAt) throws IOException {
        File f = new File(context.getFilesDir(), "t" + deletedAt + ".pdf");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3});
        }
        TrashItemEntity t = new TrashItemEntity();
        t.documentId = docId;
        t.name = "x.pdf";
        t.tempPath = f.getAbsolutePath();
        t.deletedAt = deletedAt;
        t.size = 3;
        t.id = db.trash().insert(t);
        return t;
    }

    @Test
    public void purgeRemovesOnlyExpiredItems() throws IOException {
        TrashRepository repo = new TrashRepository(context, db);
        DocumentRepository docs = new DocumentRepository(db, direct);
        DocumentEntity d = docs.ensure("content://a", "a.pdf", 3, null);
        db.documents().setInTrash(d.id, true);
        long now = 100L * 24 * 3600 * 1000;
        TrashItemEntity old = trashed(d.id, now - TrashItemEntity.RETENTION_MS - 1);
        TrashItemEntity recent = trashed(-1, now - 1000);
        assertEquals(1, repo.purgeExpired(now));
        assertFalse(new File(old.tempPath).exists());
        assertTrue(new File(recent.tempPath).exists());
        assertNull(db.trash().byId(old.id));
        // The library entry of a permanently deleted document disappears.
        assertNull(db.documents().byId(d.id));
        assertEquals(1, value(repo.all()).size());
    }

    @Test
    public void retentionHelpers() {
        TrashItemEntity t = new TrashItemEntity();
        t.deletedAt = 0;
        assertEquals(30, t.daysLeft(0));
        assertEquals(0, t.daysLeft(TrashItemEntity.RETENTION_MS * 2));
        assertTrue(t.isExpired(TrashItemEntity.RETENTION_MS + 1));
        assertFalse(t.isExpired(1000));
    }
}
