package com.bng.drivo.data.model;

public class UserProfile {

    private final String id;
    private String name;
    private String email;
    private final String phone;
    private String photoUrl;

    public UserProfile(String id, String name, String email, String phone, String photoUrl) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.photoUrl = photoUrl;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public boolean isComplete() {
        return name != null && !name.trim().isEmpty();
    }

    public String getInitials() {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && initials.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }
}
