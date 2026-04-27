package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.TrustedContact;

import java.util.List;

/**
 * DAO for {@link TrustedContact} — emergency/trusted contacts for crisis support.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code CrisisViewModel} — load contacts for crisis screen</li>
 *   <li>{@code SupportFragment} — display "reach out" contacts</li>
 *   <li>{@code CrisisActivity} — auto-compose SMS for urgent crises</li>
 * </ul>
 */
@Dao
public interface TrustedContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TrustedContact contact);

    @Update
    void update(TrustedContact contact);

    @Delete
    void delete(TrustedContact contact);

    /** Get all contacts, emergency contacts first. */
    @Query("SELECT * FROM trusted_contacts ORDER BY isEmergency DESC, createdAt ASC")
    List<TrustedContact> getAllSync();

    /** Get all contacts as observable LiveData. */
    @Query("SELECT * FROM trusted_contacts ORDER BY isEmergency DESC, createdAt ASC")
    LiveData<List<TrustedContact>> getAll();

    /** Get only emergency contacts. */
    @Query("SELECT * FROM trusted_contacts WHERE isEmergency = 1 ORDER BY createdAt ASC")
    List<TrustedContact> getEmergencyContactsSync();

    /** Get contacts that should be auto-notified during crisis. */
    @Query("SELECT * FROM trusted_contacts WHERE notifyOnCrisis = 1 ORDER BY isEmergency DESC")
    List<TrustedContact> getCrisisNotifyContactsSync();

    /** Get contact count. */
    @Query("SELECT COUNT(*) FROM trusted_contacts")
    int getCount();

    /** Delete all contacts. */
    @Query("DELETE FROM trusted_contacts")
    void deleteAll();
}
