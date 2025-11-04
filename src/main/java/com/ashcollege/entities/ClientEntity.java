package com.ashcollege.entities;

import javax.persistence.Column;

public class ClientEntity {
    private int id;
    private String name;
    private UserEntity manager; // מנהל הלקוח
    private String status;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UserEntity getManager() { return manager; }
    public void setManager(UserEntity manager) { this.manager = manager; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
