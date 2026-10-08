package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.Client
import com.example.data.model.Item
import com.example.data.model.StoreSettings
import com.example.util.BackupEngine
import com.example.util.ExportEngine
import com.example.util.RestoreEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBackupAndRestoreFlow() {
        runBlocking {
            // 1. Seed data
            val client = Client(
                name = "صيدلية النور الحديثة",
                phone = "777123456",
                address = "صنعاء - شارع تعز",
                balance = 1500.0,
                clientType = "جملة"
            )
            val clientId = db.clientDao().insertClient(client).toInt()

            val item = Item(
                name = "باراسيتامول 500 ملجم",
                category = "أدوية",
                purchasePrice = 100.0,
                sellingPrice = 150.0,
                quantity = 50,
                unit = "شريط"
            )
            db.itemDao().insertItem(item)

            // 2. Perform Backup
            val backupResult = BackupEngine.createBackup(
                context = context,
                db = db,
                selectedCategories = BackupEngine.ALL_CATEGORIES.toSet(),
                destination = "الهاتف",
                retentionCount = 5
            )

            assertTrue("Backup should succeed", backupResult.isSuccess)
            val backupFile = backupResult.getOrThrow()
            assertTrue("Backup file must exist", backupFile.exists())
            assertTrue("Backup file size must be > 0", backupFile.length() > 0)

            // 3. Clear Database
            db.clientDao().deleteAllClients()
            db.itemDao().deleteAllItems()
            assertEquals(0, db.clientDao().getAllClients().size)
            assertEquals(0, db.itemDao().getAllItems().size)

            // 4. Restore from Backup
            val restoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = db,
                backupFile = backupFile
            )

            assertTrue("Restore should succeed", restoreResult.success)
            assertTrue("Restored count should be >= 2", restoreResult.totalRestored >= 2)

            val restoredClients = db.clientDao().getAllClients()
            assertEquals(1, restoredClients.size)
            assertEquals("صيدلية النور الحديثة", restoredClients.first().name)

            val restoredItems = db.itemDao().getAllItems()
            assertEquals(1, restoredItems.size)
            assertEquals("باراسيتامول 500 ملجم", restoredItems.first().name)

            // Clean up backup file
            backupFile.delete()
        }
    }

    @Test
    fun testExportEngineOutputs() {
        runBlocking {
            val client = Client(
                name = "مستشفى الأمل",
                phone = "771000000",
                address = "تعز",
                balance = 5000.0,
                clientType = "مستشفى"
            )
            db.clientDao().insertClient(client)

            val settings = StoreSettings(storeName = "شركة الحكيمي الطبية")

            val csvFile = ExportEngine.exportToCsv(
                context = context,
                db = db,
                selectedCategories = setOf("clients"),
                settings = settings
            )
            assertTrue("CSV file must exist", csvFile.exists())
            assertTrue("CSV file must contain data", csvFile.length() > 0)

            val txtFile = ExportEngine.exportToTxt(
                context = context,
                db = db,
                selectedCategories = setOf("clients"),
                settings = settings
            )
            assertTrue("TXT file must exist", txtFile.exists())
            assertTrue("TXT file must contain data", txtFile.length() > 0)

            csvFile.delete()
            txtFile.delete()
        }
    }
}
