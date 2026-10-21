package app.silverbox.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import app.silverbox.domain.Format
import app.silverbox.domain.Stock
import kotlinx.coroutines.flow.Flow

/**
 * A roll of film or a movie cassette: loaded, shot, sent to the lab, and back in an envelope.
 * [sentAt] is null while it is still in the camera; [readyAt] is when the lab hands it back.
 */
@Entity(tableName = "rolls")
data class Roll(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [Format] name. */
    val format: String,
    /** [Stock] name. */
    val stock: String,
    /** Frames on a roll, or clips on a cassette. */
    val capacity: Int,
    /** An event the roll is for ("Summer at Gran's"), or empty. */
    val name: String = "",
    val loadedAt: Long,
    val sentAt: Long? = null,
    val readyAt: Long? = null,
    val express: Boolean = false,
    /** The lab's order number, printed on the receipt and the envelope. */
    val order: Int = 0,
    /** The lab has finished its work (prints made, film cut). */
    val developed: Boolean = false,
    /** The envelope has been opened. */
    val opened: Boolean = false,
    /** How far past its date the film was when it went to the lab, 0..1. */
    val expired: Float = 0f,
    /** The finished film of a cassette, a file in the vault. */
    val film: String = "",
) {
    val formatOf: Format get() = runCatching { Format.valueOf(format) }.getOrDefault(Format.PHOTO)
    val stockOf: Stock get() = runCatching { Stock.valueOf(stock) }.getOrDefault(Stock.SUNNY)
    val movie: Boolean get() = formatOf == Format.MOVIE

    fun inCamera() = sentAt == null
    fun atLab(now: Long) = sentAt != null && (!developed || now < (readyAt ?: Long.MAX_VALUE))
    fun ready(now: Long) = sentAt != null && developed && now >= (readyAt ?: Long.MAX_VALUE)
    fun waiting(now: Long) = ready(now) && !opened
}

/** One exposure: a photo or a clip from the phone's camera, and what the lab made of it. */
@Entity(tableName = "frames", indices = [Index("rollId")])
data class Frame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rollId: Long,
    /** 1-based place on the roll. */
    val number: Int,
    /** The camera's file, kept in the vault. */
    val original: String,
    val video: Boolean = false,
    val durationMs: Long = 0,
    /** Where the three seconds of a clip started, when the lab still cut them; kept for old backups. */
    val clipStartMs: Long = 0,
    val takenAt: Long,
    /** The gallery item it came from, so the same picture is never loaded twice. */
    val source: String = "",
    /** The developed print, a file in the vault; empty until the lab is done. */
    val print: String = "",
    /** [app.silverbox.develop.Defect] name. */
    val defect: String = "",
    /** In the album rather than the shoebox. */
    val album: Boolean = false,
    /** Written on the back of the print. */
    val note: String = "",
    /** Who took it, if not the keeper of the camera. */
    val by: String = "",
)

@Dao
abstract class LabDao {
    @Query("SELECT * FROM rolls ORDER BY loadedAt DESC")
    abstract fun observeRolls(): Flow<List<Roll>>

    @Query("SELECT * FROM frames ORDER BY takenAt ASC")
    abstract fun observeFrames(): Flow<List<Frame>>

    @Query("SELECT * FROM rolls ORDER BY loadedAt DESC")
    abstract suspend fun rolls(): List<Roll>

    @Query("SELECT * FROM frames ORDER BY takenAt ASC")
    abstract suspend fun frames(): List<Frame>

    @Query("SELECT * FROM rolls WHERE id = :id")
    abstract suspend fun roll(id: Long): Roll?

    @Query("SELECT * FROM frames WHERE rollId = :rollId ORDER BY number ASC")
    abstract suspend fun framesOf(rollId: Long): List<Frame>

    @Query("SELECT * FROM frames WHERE id = :id")
    abstract suspend fun frame(id: Long): Frame?

    @Query("SELECT * FROM rolls WHERE sentAt IS NULL AND format = :format LIMIT 1")
    abstract suspend fun loaded(format: String): Roll?

    @Query("SELECT source FROM frames WHERE source != ''")
    abstract suspend fun sources(): List<String>

    @Query("SELECT MAX(`order`) FROM rolls")
    abstract suspend fun lastOrder(): Int?

    @Insert abstract suspend fun insert(r: Roll): Long
    @Update abstract suspend fun update(r: Roll)
    @Delete abstract suspend fun delete(r: Roll)

    @Insert abstract suspend fun insert(f: Frame): Long
    @Update abstract suspend fun update(f: Frame)
    @Delete abstract suspend fun delete(f: Frame)

    @Query("DELETE FROM frames WHERE rollId = :rollId")
    abstract suspend fun deleteFramesOf(rollId: Long)

    @Query("UPDATE rolls SET opened = 1 WHERE id = :id")
    abstract suspend fun markOpened(id: Long)

    @Query("DELETE FROM rolls") protected abstract suspend fun clearRolls()
    @Query("DELETE FROM frames") protected abstract suspend fun clearFrames()
    @Insert protected abstract suspend fun insertRolls(l: List<Roll>)
    @Insert protected abstract suspend fun insertFrames(l: List<Frame>)

    @Transaction
    open suspend fun replaceAll(rolls: List<Roll>, frames: List<Frame>) {
        clearFrames()
        clearRolls()
        insertRolls(rolls)
        insertFrames(frames)
    }
}

@Database(entities = [Roll::class, Frame::class], version = 1, exportSchema = true)
abstract class LabDatabase : RoomDatabase() {
    abstract fun dao(): LabDao

    companion object {
        fun create(context: Context): LabDatabase =
            Room.databaseBuilder(context.applicationContext, LabDatabase::class.java, "silverbox.db").build()
    }
}
