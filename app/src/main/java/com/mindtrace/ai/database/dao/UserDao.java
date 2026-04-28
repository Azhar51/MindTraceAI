package com.mindtrace.ai.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.mindtrace.ai.database.entity.User;

@Dao
public interface UserDao {
    @Insert
    void insert(User user);

    /**
     * Fetch user by email for application-layer password verification.
     * Password comparison is done via {@link com.mindtrace.ai.security.PasswordHasher}
     * instead of raw SQL to support hashed storage.
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    /**
     * @deprecated Use {@link #findByEmail(String)} + PasswordHasher.verify() instead.
     * Retained only for migration compatibility — will be removed in v2.
     */
    @Deprecated
    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    User login(String email, String password);

    @Update
    void update(User user);

    @Query("SELECT * FROM users LIMIT 1")
    User getUser();
}
