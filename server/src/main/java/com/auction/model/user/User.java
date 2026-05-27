package com.auction.model.user;

import com.auction.model.entity.Entity;

public abstract class User extends Entity {
    protected String username;
    protected String password;
    protected String email;
    protected String role;
    protected boolean banned;

    public User() { super(); }

    public User(String id, String username, String password, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.email    = email;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail()    { return email; }
    public String getRole()     { return role; }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setEmail(String email)       { this.email = email; }
    public void setRole(String role)         { this.role = role; }
    public boolean isBanned()                { return banned; }
    public void setBanned(boolean banned)    { this.banned = banned; }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', role='" + role + "'}";
    }
}