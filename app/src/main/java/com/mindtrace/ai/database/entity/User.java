package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String email;
    /**
     * Hashed password in format "base64(salt):base64(SHA-256(salt+password))".
     * Legacy plaintext values are auto-migrated on next successful login.
     * @see com.mindtrace.ai.security.PasswordHasher
     */
    public String password;
    public long createdAt;
}
