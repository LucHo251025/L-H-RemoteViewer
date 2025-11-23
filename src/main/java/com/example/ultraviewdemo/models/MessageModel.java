package com.example.ultraviewdemo.models;

import java.io.Serializable;

public class MessageModel implements Serializable {

    private static final long serialVersionUID = 1L;
    private int action;
    private String owner_id;
    private String owner_password;
    private String partner_id;
    private String partner_password;
    private boolean isSuccess;
    private String message;
    private byte[] data;

    public MessageModel(int action, String owner_id) {
        super();
        this.action = action;
        this.owner_id = owner_id;
    }

    public MessageModel() {
        super();

    }
    public boolean isSuccess() {
        return isSuccess;
    }
    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public byte[] getData() {
        return data;
    }
    public void setData(byte[] data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getOwner_id() {
        return owner_id;
    }

    public void setOwner_id(String owner_id) {
        this.owner_id = owner_id;
    }

    public String getOwner_password() {
        return owner_password;
    }

    public void setOwner_password(String owner_password) {
        this.owner_password = owner_password;
    }

    public String getPartner_id() {
        return partner_id;
    }

    public void setPartner_id(String partner_id) {
        this.partner_id = partner_id;
    }

    public String getPartner_password() {
        return partner_password;
    }

    public void setPartner_password(String partner_password) {
        this.partner_password = partner_password;
    }

    @Override
    public String toString() {
        return "MessageModel{" +
                "action=" + action +
                ", owner_id='" + owner_id + '\'' +
                ", owner_password='" + owner_password + '\'' +
                ", partner_id='" + partner_id + '\'' +
                ", partner_password='" + partner_password + '\'' +
                ", isSuccess=" + isSuccess +
                ", message='" + message + '\'' +
                '}';
    }
}